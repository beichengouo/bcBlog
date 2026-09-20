package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.common.SandboxEquip;
import com.bc.bcblog.entity.SandboxShopItem;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxShopItemMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 集市商品「装备字段」的**真实 AI** 验证（一次调用）。
 *
 * 要确认的是：AI 生成商品时会自己给 slot / power_bonus（名字可以随便取，
 * 不必再叫「XX匕首」「XX铁盾」），服务端把这两个字段按品质区间夹取后落库，
 * 而不是像以前那样靠名字猜。
 *
 * 手动运行： mvn test "-Dtest=SandboxShopEquipFieldCheck"
 * 报告：target/probe/shop-equip-fields.md
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxShopEquipFieldCheck {

    static {
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final Long SOURCE_WORLD = 2L;
    private static final Long PROVIDER_ID = 2L;
    private static final String MODEL = "假流式-gemini-3-flash-preview";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private ConfigService configService;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxShopItemMapper shopItemMapper;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test
    void run() throws Exception {
        StringBuilder report = new StringBuilder("# 集市商品的装备字段（真实 AI）\n\n");
        Long worldId = null;
        int failed = 0;
        try {
            jdbc.update("insert into sandbox_world (name, description, map_image, world_prompt, enabled,"
                    + " portal_visible, create_time, update_time)"
                    + " select ?, description, map_image, world_prompt, 0, 0, now(), now()"
                    + " from sandbox_world where id = ?", "【探针】集市装备字段世界", SOURCE_WORLD);
            worldId = jdbc.queryForObject("select max(id) from sandbox_world", Long.class);
            jdbc.update("insert into sandbox_location (world_id, name, icon, x, y, width, height, polygon,"
                    + " description, danger_level, power_min, power_max, sort_order, create_time)"
                    + " select ?, name, icon, x, y, width, height, polygon, description, danger_level,"
                    + " power_min, power_max, sort_order, now() from sandbox_location where world_id = ?",
                    worldId, SOURCE_WORLD);

            long start = System.currentTimeMillis();
            int created = service.generateShopItems(4, PROVIDER_ID, MODEL, worldId);
            long cost = System.currentTimeMillis() - start;
            report.append("- 模型：").append(MODEL).append("；生成 ").append(created)
                    .append(" 件，耗时 ").append(cost).append(" ms\n\n");

            List<SandboxShopItem> items = shopItemMapper.selectList(new QueryWrapper<SandboxShopItem>()
                    .eq("world_id", worldId).orderByAsc("id"));
            int equipCount = 0;
            for (SandboxShopItem item : items) {
                boolean equip = SandboxEquip.wearable(item.getSlot());
                if (equip) {
                    equipCount++;
                }
                report.append("- ").append(equip ? "【装备】" : "普通商品").append(' ')
                        .append(item.getName()).append("（品质 ").append(item.getRarity())
                        .append("，").append(SandboxEquip.slotLabel(item.getSlot()))
                        .append(" +").append(item.getPowerBonus() == null ? 0 : item.getPowerBonus())
                        .append("）：").append(item.getDescription()).append('\n');
                // 加成必须落在该品质区间里（服务端夹取）
                if (equip) {
                    int[] range = SandboxEquip.bonusRange(
                            configService.getConfigValue("sandbox_equip_bonus_by_rarity",
                                    SandboxEquip.DEFAULT_BONUS_TABLE),
                            item.getRarity() == null ? 1 : item.getRarity());
                    int bonus = item.getPowerBonus() == null ? 0 : item.getPowerBonus();
                    if (bonus < range[0] || bonus > range[1]) {
                        failed++;
                        report.append("    ❌ 加成 ").append(bonus).append(" 不在品质区间 ")
                                .append(range[0]).append('~').append(range[1]).append(" 内\n");
                    }
                }
            }
            report.append("\n- 其中装备类商品：").append(equipCount).append(" 件\n");
            if (created < 1) {
                failed++;
                report.append("- ❌ 一件商品都没生成\n");
            }
            if (items.stream().noneMatch(i -> i.getSlot() == null || "none".equals(i.getSlot()))) {
                // 全都被判成装备也不太对：集市里总该有药水、材料之类的普通货
                failed++;
                report.append("- ❌ 所有商品都被判成了装备（应该同时有药水/材料这类普通商品）\n");
            }
            report.append(failed == 0 ? "\n结果：通过（字段生成与夹取都正常）\n" : "\n结果：" + failed + " 项异常\n");
        } finally {
            Path dir = Paths.get("target", "probe");
            Files.createDirectories(dir);
            Files.write(dir.resolve("shop-equip-fields.md"), report.toString().getBytes(StandardCharsets.UTF_8));
            if (worldId != null) {
                shopItemMapper.delete(new QueryWrapper<SandboxShopItem>().eq("world_id", worldId));
                jdbc.update("delete from sandbox_location where world_id = ?", worldId);
                worldMapper.deleteById(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
            System.out.println("报告：target/probe/shop-equip-fields.md");
        }
        if (failed > 0) {
            throw new AssertionError("集市装备字段验证有 " + failed + " 项异常，详见 target/probe/shop-equip-fields.md");
        }
    }
}
