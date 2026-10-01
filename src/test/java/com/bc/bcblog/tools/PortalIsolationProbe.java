package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bc.bcblog.common.BusinessException;
import com.bc.bcblog.entity.SandboxAreaLock;
import com.bc.bcblog.entity.SandboxAttitudeLog;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxGift;
import com.bc.bcblog.entity.SandboxShopItem;
import com.bc.bcblog.entity.SandboxShopOrder;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxAreaLockMapper;
import com.bc.bcblog.mapper.SandboxAttitudeLogMapper;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxGiftMapper;
import com.bc.bcblog.mapper.SandboxShopItemMapper;
import com.bc.bcblog.mapper.SandboxShopOrderMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.SandboxService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

/**
 * 临时探针：前台沙盒的世界可见性 + 删除世界是否清干净子表。
 *
 * 会新建一个「隐藏世界」并在结束时删掉；只在本地库跑，不碰已有世界的数据。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PortalIsolationProbe {

    static { System.setProperty("bcblog.sandbox.scheduler.disabled", "true"); }

    @Autowired private SandboxService sandboxService;
    @Autowired private SandboxWorldMapper worldMapper;
    @Autowired private SandboxCharacterMapper characterMapper;
    @Autowired private SandboxShopItemMapper shopItemMapper;
    @Autowired private SandboxShopOrderMapper shopOrderMapper;
    @Autowired private SandboxGiftMapper giftMapper;
    @Autowired private SandboxAttitudeLogMapper attitudeLogMapper;
    @Autowired private SandboxAreaLockMapper areaLockMapper;

    private int fail = 0;
    private void ok(String m) { System.out.println("  ok   " + m); }
    private void bad(String m) { System.out.println("  FAIL " + m); fail++; }

    private void expectDenied(String what, Runnable call) {
        try {
            call.run();
            bad(what + "：本该拒绝，却放行了");
        } catch (BusinessException e) {
            ok(what + "：已拒绝（" + e.getMessage() + "）");
        } catch (Exception e) {
            bad(what + "：抛的不是业务异常：" + e);
        }
    }

    private long countWorldRows(String table, Long worldId) {
        switch (table) {
            case "shop_item":
                return shopItemMapper.selectCount(new LambdaQueryWrapper<SandboxShopItem>()
                        .eq(SandboxShopItem::getWorldId, worldId));
            case "shop_order":
                return shopOrderMapper.selectCount(new LambdaQueryWrapper<SandboxShopOrder>()
                        .eq(SandboxShopOrder::getWorldId, worldId));
            case "gift":
                return giftMapper.selectCount(new LambdaQueryWrapper<SandboxGift>()
                        .eq(SandboxGift::getWorldId, worldId));
            case "attitude_log":
                return attitudeLogMapper.selectCount(new LambdaQueryWrapper<SandboxAttitudeLog>()
                        .eq(SandboxAttitudeLog::getWorldId, worldId));
            case "area_lock":
                return areaLockMapper.selectCount(new LambdaQueryWrapper<SandboxAreaLock>()
                        .eq(SandboxAreaLock::getWorldId, worldId));
            default:
                return -1;
        }
    }

    @Test
    void probe() {
        Long visible = sandboxService.resolvePortalWorldId(null);
        System.out.println("   前台默认世界（第一个可见世界）= " + visible);
        if (visible != null) {
            ok("不传 worldId 时解析到的是可见世界");
        } else {
            bad("一个可见世界都没有（先把某个世界设成前台可见再跑）");
        }

        SandboxWorld hidden = new SandboxWorld();
        hidden.setName("__探针隐藏世界__");
        hidden.setEnabled(0);
        hidden.setPortalVisible(0);
        sandboxService.saveWorld(hidden);
        Long hid = hidden.getId();
        System.out.println("   已建隐藏世界 id=" + hid + "（跑完会删掉）");
        if (hid == null) {
            bad("隐藏世界没建出来");
            return;
        }

        try {
            expectDenied("按 worldId 直接读隐藏世界", () -> sandboxService.resolvePortalWorldId(hid));
            expectDenied("不存在的角色", () -> sandboxService.requirePortalVisibleCharacter(0L));

            SandboxCharacter c = new SandboxCharacter();
            c.setWorldId(hid);
            c.setName("探针角色");
            c.setEnabled(1);
            c.setCoins(0);
            characterMapper.insert(c);
            Long cid = c.getId();
            expectDenied("隐藏世界里的角色", () -> sandboxService.requirePortalVisibleCharacter(cid));

            LocalDateTime now = LocalDateTime.now();
            SandboxShopItem item = new SandboxShopItem();
            item.setWorldId(hid);
            item.setBatchTime(now);
            item.setName("探针商品");
            item.setPrice(1);
            item.setStock(1);
            item.setEnabled(1);
            shopItemMapper.insert(item);

            SandboxShopOrder order = new SandboxShopOrder();
            order.setWorldId(hid);
            order.setItemId(item.getId());
            order.setItemName("探针商品");
            order.setCharacterId(cid);
            order.setQuantity(1);
            order.setCreateTime(now);
            shopOrderMapper.insert(order);

            SandboxGift gift = new SandboxGift();
            gift.setWorldId(hid);
            gift.setCharacterId(cid);
            gift.setItemName("探针礼物");
            gift.setQuantity(1);
            gift.setCreateTime(now);
            giftMapper.insert(gift);

            SandboxAttitudeLog log = new SandboxAttitudeLog();
            log.setWorldId(hid);
            log.setCharacterId(cid);
            log.setKind("power");
            log.setNewView("探针");
            attitudeLogMapper.insert(log);

            areaLockMapper.insertIgnore(hid, "探针地区", "探针角色", now);

            String[] tables = {"shop_item", "shop_order", "gift", "attitude_log", "area_lock"};
            for (String t : tables) {
                long n = countWorldRows(t, hid);
                if (n > 0) { ok("删除前 " + t + " 有 " + n + " 行"); } else { bad("删除前 " + t + " 没插进去"); }
            }

            sandboxService.deleteWorld(hid);
            if (worldMapper.selectById(hid) == null) {
                ok("世界已删除");
            } else {
                bad("世界还在");
            }
            for (String t : tables) {
                long n = countWorldRows(t, hid);
                if (n == 0) { ok("删除后 " + t + " 已清空"); } else { bad("删除后 " + t + " 还剩 " + n + " 行"); }
            }
            long chars = characterMapper.selectCount(new LambdaQueryWrapper<SandboxCharacter>()
                    .eq(SandboxCharacter::getWorldId, hid));
            if (chars == 0) { ok("删除后角色已清空"); } else { bad("删除后还剩 " + chars + " 个角色"); }
        } finally {
            // 兜底清理，别把探针数据留在正式库里
            if (worldMapper.selectById(hid) != null) {
                for (String t : new String[]{"shop_item", "shop_order", "gift", "attitude_log", "area_lock"}) {
                    try {
                        switch (t) {
                            case "shop_item":
                                shopItemMapper.delete(new LambdaQueryWrapper<SandboxShopItem>()
                                        .eq(SandboxShopItem::getWorldId, hid));
                                break;
                            case "shop_order":
                                shopOrderMapper.delete(new LambdaQueryWrapper<SandboxShopOrder>()
                                        .eq(SandboxShopOrder::getWorldId, hid));
                                break;
                            case "gift":
                                giftMapper.delete(new LambdaQueryWrapper<SandboxGift>()
                                        .eq(SandboxGift::getWorldId, hid));
                                break;
                            case "attitude_log":
                                attitudeLogMapper.delete(new LambdaQueryWrapper<SandboxAttitudeLog>()
                                        .eq(SandboxAttitudeLog::getWorldId, hid));
                                break;
                            default:
                                areaLockMapper.delete(new LambdaQueryWrapper<SandboxAreaLock>()
                                        .eq(SandboxAreaLock::getWorldId, hid));
                        }
                    } catch (Exception ignored) {
                        // 兜底清理失败也不影响结论
                    }
                }
                characterMapper.delete(new LambdaQueryWrapper<SandboxCharacter>()
                        .eq(SandboxCharacter::getWorldId, hid));
                worldMapper.deleteById(hid);
                System.out.println("   兜底清理完成（世界 " + hid + " 已删）");
            }
        }
        System.out.println(fail == 0 ? "PROBE RESULT: 全部通过" : "PROBE RESULT: 失败 " + fail + " 项");
        if (fail > 0) {
            throw new AssertionError("有 " + fail + " 项不符合预期");
        }
    }
}
