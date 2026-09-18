package com.bc.bcblog.common;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 沙盒「绝对不死」守卫的规则测试：该拦的拦、该放过的放过。 */
class SandboxDeathGuardTest {

    @Test
    void 真的写死了要拦下来() {
        assertNotNull(SandboxDeathGuard.hitOf("伊露雅被魔物咬中，当场断气。"));
        assertNotNull(SandboxDeathGuard.hitOf("她的尸体被同伴抬了回来。"));
        assertNotNull(SandboxDeathGuard.hitOf("莉莉娜倒在地上，已经没了呼吸。"));
        assertNotNull(SandboxDeathGuard.hitOf("他在混战中命丧荒野。"));
    }

    @Test
    void 修辞与濒死描写不要误伤() {
        assertNull(SandboxDeathGuard.hitOf("她差点死在这里，靠着树干喘了很久。"));
        assertNull(SandboxDeathGuard.hitOf("伊露雅以为自己要死了，却发现只是皮外伤。"));
        assertNull(SandboxDeathGuard.hitOf("他感觉死神就在身边，但最终还是撑了下来。"));
        // 「濒死」是我们要的状态词，不属于"把人写死"
        assertNull(SandboxDeathGuard.hitOf("她被打到濒死，昏迷了整整一夜。"));
    }

    @Test
    void 状态里写了死亡也要扫出来() {
        JSONObject obj = JSONUtil.parseObj("{\"actions\":[\"在森林里捡柴\"],\"summary\":\"平安无事\","
                + "\"status\":{\"体力\":20,\"伤势\":\"死亡\"}}");
        assertNotNull(SandboxDeathGuard.detect(obj));
    }

    @Test
    void 本地清洗后不再出现死亡表述() {
        String json = "{\"actions\":[\"被魔物扑倒，当场断气\"],\"summary\":\"她死了\","
                + "\"inner_voice\":\"我以为自己要死了\",\"status\":{\"体力\":10,\"伤势\":\"死亡\"}}";
        String cleaned = SandboxDeathGuard.clean(json);
        JSONObject obj = JSONUtil.parseObj(cleaned);
        assertNotNull(obj);
        assertNull(SandboxDeathGuard.detect(obj), "清洗后不应再命中死亡词");
        // 修辞保留原样，不被改坏
        assertTrue(cleaned.contains("我以为自己要死了"));
    }

    @Test
    void 伤势只允许四个档位() {
        assertEquals("无恙", SandboxDeathGuard.normalizeInjury("无恙"));
        assertEquals("轻伤", SandboxDeathGuard.normalizeInjury("轻伤，擦了药"));
        assertEquals("重伤", SandboxDeathGuard.normalizeInjury("重伤昏迷"));
        assertEquals("濒死", SandboxDeathGuard.normalizeInjury("奄奄一息"));
        // 沾了"死"字的值一律归到最重的"活着"状态
        assertEquals("濒死", SandboxDeathGuard.normalizeInjury("死亡"));
        assertEquals("濒死", SandboxDeathGuard.normalizeInjury("生命垂危"));
        // 无法识别时原样保留（不影响以后扩展新的伤势描述）
        assertEquals("骨折", SandboxDeathGuard.normalizeInjury("骨折"));
        assertFalse(SandboxDeathGuard.INJURY_LEVELS.contains("骨折"));
    }
}
