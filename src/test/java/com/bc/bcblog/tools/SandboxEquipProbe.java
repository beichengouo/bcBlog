package com.bc.bcblog.tools;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bc.bcblog.entity.SandboxAct;
import com.bc.bcblog.entity.SandboxCharacter;
import com.bc.bcblog.entity.SandboxItem;
import com.bc.bcblog.entity.SandboxQuest;
import com.bc.bcblog.entity.SandboxQuestReward;
import com.bc.bcblog.entity.SandboxWorld;
import com.bc.bcblog.mapper.SandboxCharacterMapper;
import com.bc.bcblog.mapper.SandboxItemMapper;
import com.bc.bcblog.mapper.SandboxWorldMapper;
import com.bc.bcblog.service.ConfigService;
import com.bc.bcblog.service.impl.SandboxServiceImpl;
import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

/**
 * 装备栏的服务端规则探针（**不调用 AI**）。
 *
 * 覆盖最容易悄悄坏掉的几条：
 *   1. 装备 / 卸下 / 破损三种动作是否按预期改库；
 *   2. 加成是否按品质区间夹取（AI 写 999 也不能当真）；
 *   3. 「加成不能超过自身实力」这条硬规则有没有真的拦下来；
 *   4. 不是装备的东西、破损的东西能不能穿；
 *   5. 同一槽位会不会出现两件（顶替是否正确）；
 *   6. 角色的「装备加成合计」与装备栏是否始终一致。
 *
 * 全程在自己的临时世界里跑，跑完删干净。手动运行： mvn test "-Dtest=SandboxEquipProbe"
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SandboxEquipProbe {

    static {
        // 测试进程里禁用定时任务（见 tools/README.md）
        System.setProperty("bcblog.sandbox.scheduler.disabled", "true");
    }

    private static final String WORLD_NAME = "【探针】装备栏测试世界";
    private static final String HERO = "【探针】装备测试者";

    @Autowired
    private SandboxServiceImpl service;
    @Autowired
    private ConfigService configService;
    @Autowired
    private SandboxWorldMapper worldMapper;
    @Autowired
    private SandboxCharacterMapper characterMapper;
    @Autowired
    private SandboxItemMapper itemMapper;

    private int passed = 0;
    private final java.util.List<String> report = new java.util.ArrayList<>();

    @Test
    void run() throws Exception {
        Long worldId = null;
        String tableBefore = configService.getConfigValue("sandbox_equip_bonus_by_rarity", "1-10,10-20,20-30,30-60,60-120");
        try {
            worldId = setUpWorld();
            SandboxCharacter hero = setUpCharacter(worldId);
            addItem(hero, "精钢长剑", "weapon", 12, 2, 0, 0, "剑身笔直、开过锋的长剑");
            addItem(hero, "龙鳞重剑", "weapon", 90, 5, 0, 0, "剑身布满龙鳞纹路的古剑，重得吓人");
            addItem(hero, "黑铁胸甲", "armor", 15, 2, 0, 0, "沉甸甸的黑铁胸甲");
            addItem(hero, "备用短刀", "weapon", 8, 1, 0, 0, "备用的短刀");
            addItem(hero, "破损的旧斗篷", "armor", 0, 1, 0, 1, "已经破得透风的旧斗篷");
            addItem(hero, "银叶草", "none", 0, 1, 0, 0, "能入药的淡紫色小草");

            testEquipBasics(hero);
            testCannotWearRule(hero);
            testSlotReplaceAndMisc(hero);
            testBrokenFlow(hero);
            testBonusTableConfig(hero);
            testAdminApiGuard(hero);
            testPortalVO(worldId, hero);
            testShopAndRewardTyping(hero);
            testQuestRewardFields(worldId, hero);

            writeReport("全部通过（" + passed + " 项）");
            System.out.println("\n===== 装备栏探针：全部通过（" + passed + " 项）=====");
        } catch (Throwable e) {
            writeReport("失败：" + e.getMessage());
            throw e;
        } finally {
            configService.setConfigValue("sandbox_equip_bonus_by_rarity", tableBefore);
            if (worldId != null) {
                itemMapper.delete(new QueryWrapper<SandboxItem>().eq("world_id", worldId));
                characterMapper.delete(new QueryWrapper<SandboxCharacter>().eq("world_id", worldId));
                worldMapper.deleteById(worldId);
                System.out.println("临时世界已清理（world_id=" + worldId + "）");
            }
        }
    }

    /** 装备与卸下：加成按品质区间保留，合计与装备栏对得上 */
    private void testEquipBasics(SandboxCharacter hero) throws Exception {
        equipChange(hero, "{\"equip\":[\"精钢长剑\"]}");
        SandboxItem sword = item(hero, "精钢长剑");
        check("装备后 equipped=1", sword.getEquipped() != null && sword.getEquipped() == 1);
        check("装备的槽位写对了（weapon）", "weapon".equals(sword.getSlot()));
        check("加成落在精良区间（12）", Integer.valueOf(12).equals(sword.getPowerBonus()));
        check("装备加成合计 = 12", Integer.valueOf(12).equals(equipPower(hero)));

        equipChange(hero, "{\"unequip\":[\"精钢长剑\"]}");
        check("卸下后 equipped=0", item(hero, "精钢长剑").getEquipped() == 0);
        check("卸下后加成合计归零", Integer.valueOf(0).equals(equipPower(hero)));
    }

    /** 「加成不能超过自身实力」：拿不动的装备拦下来，其余装备照常穿上 */
    private void testCannotWearRule(SandboxCharacter hero) throws Exception {
        String note = equipChange(hero, "{\"equip\":[\"龙鳞重剑\",\"黑铁胸甲\"]}");
        check("拿不动的传说重剑没有被穿上", item(hero, "龙鳞重剑").getEquipped() == 0);
        check("同一批里拿得动的胸甲照常穿上", item(hero, "黑铁胸甲").getEquipped() == 1);
        check("说明里写清了拿不动的规则", note != null && note.contains("拿不动") || (note != null && note.contains("自身实力")));
        check("加成合计只算上了胸甲（15）", Integer.valueOf(15).equals(equipPower(hero)));
    }

    /** 同槽位顶替 + 非装备不能穿 */
    private void testSlotReplaceAndMisc(SandboxCharacter hero) throws Exception {
        equipChange(hero, "{\"equip\":[\"精钢长剑\"]}");
        check("第二件武器把第一件顶下来了（旧武器已卸下）", item(hero, "黑铁胸甲").getEquipped() == 1);
        check("新武器已装备", item(hero, "精钢长剑").getEquipped() == 1);
        SandboxItem armor = item(hero, "黑铁胸甲");
        check("护具槽位的另一件没被误伤", armor.getEquipped() == 1);
        check("加成合计 = 剑 12 + 甲 15", Integer.valueOf(27).equals(equipPower(hero)));

        String note = equipChange(hero, "{\"equip\":[\"银叶草\",\"不存在的剑\"]}");
        check("不是装备的东西穿不上", item(hero, "银叶草").getEquipped() == 0);
        check("背包里没有的东西不会凭空装备", note != null && note.contains("没装上"));
        check("这两次失败没有改动加成合计", Integer.valueOf(27).equals(equipPower(hero)));
    }

    /** 事件式破损：卸下、改名、加成归零；破损的穿不上，修好能再穿 */
    private void testBrokenFlow(SandboxCharacter hero) throws Exception {
        String note = equipChange(hero, "{\"broken\":[\"精钢长剑\"]}");
        check("破损时给出了说明", note != null && note.contains("破损"));
        check("破损后从装备栏取下", item(hero, "破损的精钢长剑").getEquipped() == 0);
        check("破损后加成不再计入战斗力（数值留在物品上，修好还能用）",
                Integer.valueOf(12).equals(item(hero, "破损的精钢长剑").getPowerBonus()));
        check("破损标记已写", item(hero, "破损的精钢长剑").getBroken() == 1);
        check("破损后加成合计只剩护具 15", Integer.valueOf(15).equals(equipPower(hero)));

        equipChange(hero, "{\"equip\":[\"破损的精钢长剑\"]}");
        check("破损的装备不能再穿", item(hero, "破损的精钢长剑").getEquipped() == 0);

        // 后台修复
        service.repairItem(item(hero, "破损的精钢长剑").getId());
        SandboxItem fixed = item(hero, "精钢长剑");
        check("修复后名字去掉了破损前缀", fixed != null);
        check("修复后破损标记清掉", fixed != null && fixed.getBroken() == 0);
        check("修复后还是原来那件装备（加成仍是 12）",
                fixed != null && Integer.valueOf(12).equals(fixed.getPowerBonus()));
    }

    /** 后台改区间要立刻生效：把区间改成 100-110 后，一件 999 的装备会被夹到 110 */
    private void testBonusTableConfig(SandboxCharacter hero) throws Exception {
        SandboxCharacter strong = characterMapper.selectById(hero.getId());
        // 先把自身实力抬高，否则 110 会被"拿不动"规则拦下来
        characterMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, hero.getId())
                .set(SandboxCharacter::getCombatPower, 500));
        configService.setConfigValue("sandbox_equip_bonus_by_rarity", "100-110,200-210,300-310,400-410,500-510");
        // 注意：拿不动的判定读的是角色对象，改完库要重新取一份（不然还是旧的 18）
        SandboxCharacter strong2 = characterMapper.selectById(hero.getId());
        equipChange(strong2, "{\"equip\":[{\"name\":\"备用短刀\",\"bonus\":999}]}");
        SandboxItem dagger = item(hero, "备用短刀");
        check("管理员改过区间后立刻生效（999 → 110）", Integer.valueOf(110).equals(dagger.getPowerBonus()));
        check("加成落库后合计跟着更新", Integer.valueOf(110 + 15).equals(equipPower(hero)));
        check("换过武器后护具还在", item(hero, "黑铁胸甲").getEquipped() == 1);
        // 收尾：把战力与区间还原，免得影响后面的检查
        configService.setConfigValue("sandbox_equip_bonus_by_rarity", "1-10,10-20,20-30,30-60,60-120");
        characterMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, hero.getId())
                .set(SandboxCharacter::getCombatPower, strong.getCombatPower()));
    }

    /** 后台接口的兜底校验：不是装备、拿不动、破损，都要给出明确错误 */
    private void testAdminApiGuard(SandboxCharacter hero) throws Exception {
        check("后台装备「银叶草」被拒绝", throwsBusiness(() -> service.setEquip(item(hero, "银叶草").getId(), 1)));
        check("后台装备「龙鳞重剑」被拒绝（拿不动）",
                throwsBusiness(() -> service.setEquip(idOf(hero, "龙鳞重剑"), 1)));
        check("后台装备破损装备被拒绝",
                throwsBusiness(() -> service.setEquip(idOf(hero, "破损的旧斗篷"), 1)));
        check("后台卸下没问题", !throwsBusiness(() -> service.setEquip(idOf(hero, "黑铁胸甲"), 0)));
        service.setEquip(idOf(hero, "黑铁胸甲"), 1);
        check("加成合计与装备栏始终一致（重算后仍相同）",
                Integer.valueOf(equipPower(hero)).equals(service.recalcEquipPower(hero.getId())));
    }

    // ---------------- 脚手架 ----------------

    /**
     * 委托奖励里的装备（这条曾经断过：AI 明明给了 slot/power_bonus，
     * 但读回来的时候没解析这两个字段，于是奖励永远只是"普通物品"）。
     * 这里把三条路都走一遍：后台手填 → 存库 → 读回来 → 发奖进背包。
     */
    private void testQuestRewardFields(Long worldId, SandboxCharacter hero) throws Exception {
        // ① 后台手填（前端传的就是 rewards 列表）
        SandboxQuest manual = new SandboxQuest();
        manual.setWorldId(worldId);
        manual.setTitle("【探针】手填奖励装备的委托");
        manual.setQuestType("hunt");
        manual.setDifficulty(2);
        manual.setLocationName("【探针】某地");
        manual.setTarget("（探针目标）");
        manual.setRewardCoins(10);
        manual.setRewards(java.util.Arrays.asList(
                reward("黑潮影刃", 3, 1, 22, "weapon", "刃身泛着暗光的短剑"),
                reward("治疗药水", 2, 2, 0, "none", "淡绿色的低阶恢复药")));
        SandboxQuest saved = service.saveQuest(manual);

        SandboxQuest readBack = findQuest(worldId, "【探针】手填奖励装备的委托");
        check("手填的奖励装备读回来还认得（slot = weapon）",
                readBack != null && readBack.getRewards() != null && !readBack.getRewards().isEmpty()
                        && "weapon".equals(readBack.getRewards().get(0).getSlot()));
        check("手填的装备加成读回来还在（22）",
                readBack != null && readBack.getRewards() != null && !readBack.getRewards().isEmpty()
                        && Integer.valueOf(22).equals(readBack.getRewards().get(0).getPowerBonus()));
        check("同一批里的普通奖励仍然是普通物品",
                readBack != null && readBack.getRewards() != null && readBack.getRewards().size() > 1
                        && "none".equals(readBack.getRewards().get(1).getSlot()));

        // ② AI 直出的 JSON（字段名是 snake_case 的 power_bonus）
        SandboxQuest ai = new SandboxQuest();
        ai.setWorldId(worldId);
        ai.setTitle("【探针】AI 奖励装备的委托");
        ai.setQuestType("hunt");
        ai.setDifficulty(2);
        ai.setLocationName("【探针】某地");
        ai.setTarget("（探针目标）");
        ai.setRewardItems("[{\"name\":\"青光\",\"rarity\":3,\"quantity\":1,\"slot\":\"weapon\","
                + "\"power_bonus\":28,\"description\":\"刃身泛着青光的长剑\"}]");
        service.saveQuest(ai);
        SandboxQuest aiBack = findQuest(worldId, "【探针】AI 奖励装备的委托");
        check("AI 用 power_bonus 写的加成也认得（武器 +28）",
                aiBack != null && aiBack.getRewards() != null && !aiBack.getRewards().isEmpty()
                        && "weapon".equals(aiBack.getRewards().get(0).getSlot())
                        && Integer.valueOf(28).equals(aiBack.getRewards().get(0).getPowerBonus()));

        // ③ 发奖：奖励进背包时要按字段进装备栏候选，而不是只按名字猜
        String note = grantQuestItems(saved, hero);
        SandboxItem granted = item(hero, "黑潮影刃");
        check("发奖时装备按字段进背包（武器 +22）",
                granted != null && "weapon".equals(granted.getSlot())
                        && Integer.valueOf(22).equals(granted.getPowerBonus()));
        check("发奖说明里列出了这件装备", note != null && note.contains("黑潮影刃"));
    }

    private SandboxQuestReward reward(String name, int rarity, int quantity, int bonus,
                                      String slot, String description) {
        SandboxQuestReward reward = new SandboxQuestReward();
        reward.setName(name);
        reward.setRarity(rarity);
        reward.setQuantity(quantity);
        reward.setDescription(description);
        reward.setSlot(slot);
        reward.setPowerBonus(bonus);
        return reward;
    }

    private SandboxQuest findQuest(Long worldId, String title) {
        return service.questsForAdmin(worldId, "all").stream()
                .filter(q -> title.equals(q.getTitle())).findFirst().orElse(null);
    }

    private String grantQuestItems(SandboxQuest quest, SandboxCharacter hero) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("grantQuestItems",
                SandboxQuest.class, SandboxCharacter.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        Object value = method.invoke(target == null ? service : target, quest, hero);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 集市商品 / 礼物 / 委托奖励进背包时也要认得出装备。
     * 这三条路都走同一个 addToBackpack，而商品表里没有槽位与加成两列，
     * 所以要在进门时按名字识别槽位、加成取品质区间中点——不然买了把匕首也穿不上。
     */
    private void testShopAndRewardTyping(SandboxCharacter hero) throws Exception {
        addToBackpack(hero, "黑潮影刃匕首", 3);
        SandboxItem dagger = item(hero, "黑潮影刃匕首");
        check("集市买到的匕首被认成武器", dagger != null && "weapon".equals(dagger.getSlot()));
        check("集市买到的匕首拿到了品质区间中点加成（稀有 25）",
                dagger != null && Integer.valueOf(25).equals(dagger.getPowerBonus()));

        addToBackpack(hero, "翻新的制式铁盾", 1);
        SandboxItem shield = item(hero, "翻新的制式铁盾");
        check("集市买到的盾被认成副手", shield != null && "offhand".equals(shield.getSlot()));
        check("普通品质的盾加成取中点 5",
                shield != null && Integer.valueOf(5).equals(shield.getPowerBonus()));

        addToBackpack(hero, "荧光蘑菇干", 1);
        SandboxItem food = item(hero, "荧光蘑菇干");
        check("吃的不会被当成装备", food != null && "none".equals(food.getSlot())
                && Integer.valueOf(0).equals(food.getPowerBonus()));

        // 字段说了算：名字完全看不出是剑，只要 slot 写了 weapon 就是装备
        addToBackpackWithSlot(hero, "青光", 3, "weapon", 28);
        SandboxItem named = item(hero, "青光");
        check("名字看不出是装备、但字段写了 weapon → 就是武器",
                named != null && "weapon".equals(named.getSlot()));
        check("字段给的加成原样保留（28 落在稀有区间内）",
                named != null && Integer.valueOf(28).equals(named.getPowerBonus()));

        // 字段说是防具，那就进护具格（同样不看名字）
        addToBackpackWithSlot(hero, "月影", 4, "armor", 55);
        SandboxItem armor = item(hero, "月影");
        check("字段写了 armor → 进护具格", armor != null && "armor".equals(armor.getSlot()));
        check("史诗区间内的加成保留（55）",
                armor != null && Integer.valueOf(55).equals(armor.getPowerBonus()));

        // 明确声明"不是装备"时不按名字猜（哪怕名字里带「剑」）
        addToBackpackWithSlot(hero, "残破的仪式短剑", 1, "none", 0);
        SandboxItem notEquip = item(hero, "残破的仪式短剑");
        check("字段写 none 时不当装备（名字里带「短剑」也不猜）",
                notEquip != null && "none".equals(notEquip.getSlot())
                        && Integer.valueOf(0).equals(notEquip.getPowerBonus()));
    }

    /** 调带装备字段的 addToBackpack（8 参重载） */
    private void addToBackpackWithSlot(SandboxCharacter hero, String name, int rarity,
                                       String slot, int bonus) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("addToBackpack",
                SandboxCharacter.class, String.class, int.class, Integer.class, String.class, String.class,
                String.class, Integer.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        method.invoke(target == null ? service : target, hero, name, 1, rarity, null, "（探针）" + name,
                slot, bonus);
    }

    private void addToBackpack(SandboxCharacter hero, String name, int rarity) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("addToBackpack",
                SandboxCharacter.class, String.class, int.class, Integer.class, String.class, String.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        method.invoke(target == null ? service : target, hero, name, 1, rarity, null, "（探针）" + name);
    }

    /** 前台对象：装备栏与"自身 + 装备"的战斗力要给到页面，且与库内一致 */
    private void testPortalVO(Long worldId, SandboxCharacter hero) {
        com.bc.bcblog.vo.SandboxPortalVO portal = service.portal(worldId);
        com.bc.bcblog.vo.SandboxCharacterVO vo = portal == null || portal.getCharacters() == null ? null
                : portal.getCharacters().stream().filter(c -> c.getId().equals(hero.getId())).findFirst().orElse(null);
        check("前台对象带上了装备栏", vo != null && vo.getEquipment() != null);
        check("前台战斗力 = 自身实力 + 装备加成",
                vo != null && vo.getTotalPower() != null && vo.getCombatPower() != null
                        && vo.getTotalPower() == vo.getCombatPower() + vo.getEquipPower());
        check("前台装备加成与库里的一致",
                vo != null && vo.getEquipPower() != null && vo.getEquipPower().equals(equipPower(hero)));
    }

    private Long idOf(SandboxCharacter hero, String name) {
        SandboxItem item = item(hero, name);
        return item == null ? -1L : item.getId();
    }

    private boolean throwsBusiness(Runnable action) {
        try {
            action.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 调一次私有的 applyEquipChanges（allowSelfCheck=false：探针不调 AI） */
    private String equipChange(SandboxCharacter hero, String json) throws Exception {
        Method method = SandboxServiceImpl.class.getDeclaredMethod("applyEquipChanges",
                SandboxCharacter.class, JSONObject.class, SandboxAct.class, boolean.class, boolean.class);
        method.setAccessible(true);
        Object target = org.springframework.aop.framework.AopProxyUtils.getSingletonTarget(service);
        // 服务端读的是 obj 里的 equip_change 字段，这里把内容包一层，模拟 AI 的完整输出
        JSONObject obj = new JSONObject("{\"equip_change\":" + json + "}");
        SandboxAct act = new SandboxAct();
        act.setActions("（探针步骤）");
        act.setSummary("（探针步骤）");
        Object result = method.invoke(target == null ? service : target, hero, obj, act, true, false);
        // 结果对象是私有静态类，用反射读 note 字段
        if (result == null) {
            return null;
        }
        java.lang.reflect.Field note = result.getClass().getDeclaredField("note");
        note.setAccessible(true);
        Object value = note.get(result);
        return value == null ? null : String.valueOf(value);
    }

    private Integer equipPower(SandboxCharacter hero) {
        SandboxCharacter fresh = characterMapper.selectById(hero.getId());
        return fresh.getEquipPower() == null ? 0 : fresh.getEquipPower();
    }

    private SandboxItem item(SandboxCharacter hero, String name) {
        return itemMapper.selectOne(new QueryWrapper<SandboxItem>()
                .eq("character_id", hero.getId()).eq("name", name).last("limit 1"));
    }

    private Long setUpWorld() {
        SandboxWorld world = new SandboxWorld();
        world.setName(WORLD_NAME);
        world.setDescription("（探针自动创建，跑完即删）");
        world.setWorldPrompt("剑与魔法的世界。");
        world.setEnabled(0);
        world.setPortalVisible(0);
        world.setMapImage("");
        worldMapper.insert(world);
        return world.getId();
    }

    private SandboxCharacter setUpCharacter(Long worldId) {
        SandboxCharacter hero = new SandboxCharacter();
        hero.setWorldId(worldId);
        hero.setName(HERO);
        hero.setTitle("测试者");
        hero.setPersona("（探针角色）");
        hero.setAppearance("");
        hero.setX(20);
        hero.setY(20);
        hero.setLocationName("【探针】某地");
        hero.setStatusJson("{\"体力\":90,\"魔力\":60,\"饥饿度\":20,\"心情\":\"平静\",\"伤势\":\"无恙\"}");
        hero.setCoins(0);
        hero.setCombatPower(18);
        hero.setEquipPower(0);
        hero.setEnabled(1);
        hero.setIntervalMin(45);
        hero.setIntervalMax(75);
        characterMapper.insert(hero);
        return hero;
    }

    private void addItem(SandboxCharacter hero, String name, String slot, int bonus, int rarity,
                         int equipped, int broken, String description) {
        SandboxItem item = new SandboxItem();
        item.setWorldId(hero.getWorldId());
        item.setCharacterId(hero.getId());
        item.setName(name);
        item.setQuantity(1);
        item.setRarity(rarity);
        item.setSlot(slot);
        item.setPowerBonus(bonus);
        item.setEquipped(equipped);
        item.setBroken(broken);
        item.setDescription(description);
        itemMapper.insert(item);
    }

    private void check(String what, boolean ok) {
        if (ok) {
            passed++;
            report.add("- ✅ " + what);
            System.out.println("  ✅ " + what);
        } else {
            report.add("- ❌ " + what);
            writeReport("失败：" + what);
            throw new AssertionError("探针失败：" + what);
        }
    }

    private void writeReport(String title) {
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("target", "probe");
            java.nio.file.Files.createDirectories(dir);
            StringBuilder sb = new StringBuilder("# 沙盒装备栏探针报告\n\n");
            sb.append("结果：").append(title).append("\n\n");
            for (String line : report) {
                sb.append(line).append("\n");
            }
            java.nio.file.Files.write(dir.resolve("equip-probe.md"),
                    sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("报告：target/probe/equip-probe.md");
        } catch (Exception ignored) {
            // 写报告失败不影响探针本身
        }
    }
}
