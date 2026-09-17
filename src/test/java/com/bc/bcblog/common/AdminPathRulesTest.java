package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 后台权限路径规则测试。
 *
 * 目的：把「哪些接口只有超管能用、哪些是公共读接口」钉死。
 * 这类规则一旦写错，不是安全边界出问题（越权），就是被授权的菜单打不开、满屏报「没有该菜单的权限」。
 */
class AdminPathRulesTest {

    @Test
    @DisplayName("系统级密钥 / 管理账号 / 破坏性操作：仅超级管理员")
    void testSuperOnly() {
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/user/list", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/member/list", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/invite/list", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/gitalk/comments", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/email/config", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/deepseek/api-key", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/ip-location/query", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/acg-cover/random", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/audit/list", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/security/status", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/log/login", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/system/cleanup", "POST"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/config/ip-location-ak", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/config/gaode-ip-key", "POST"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/config/acg-cover-token", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/config/ip-location-provider", "PUT"));
    }

    @Test
    @DisplayName("沙盒世界与地图：世界/地点的写入仅超管，世界列表与地点读允许")
    void testSandboxWorld() {
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/world", "POST"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/world/3", "DELETE"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/world/3/enabled", "PUT"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/world/3/visible", "PUT"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/world/3/export", "GET"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/world/3/reset", "POST"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/world/import", "POST"));
        // 地点：读人人可用（行动日志的地点筛选要用），写只有超管
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/locations", "POST"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/locations/5", "DELETE"));
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/locations", "GET"));
        // 世界列表：角色管理/行动日志/集市管理的世界下拉框都要用，不能锁
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/worlds", "GET"));
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/characters", "GET"));
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/characters/3/run", "POST"));
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/shop", "GET"));
    }

    @Test
    @DisplayName("沙盒设置：读人人可用（多个页面要用），写只有超管")
    void testSandboxSettings() {
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/settings", "POST"));
        assertTrue(AdminPathRules.isSuperOnly("/api/admin/sandbox/settings", "PUT"));
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/settings", "GET"));
        // 集市/纪闻各自的写接口走作用域收口，普通管理员可用
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/shop/settings", "PUT"));
        assertFalse(AdminPathRules.isSuperOnly("/api/admin/sandbox/news/settings", "PUT"));
    }

    @Test
    @DisplayName("公共读接口不参与菜单校验，写操作仍归各自菜单")
    void testMenuExempt() {
        assertTrue(AdminPathRules.isMenuExempt("/api/admin/config", "GET"));
        assertTrue(AdminPathRules.isMenuExempt("/api/admin/config/admin-bg-opacity", "GET"));
        assertTrue(AdminPathRules.isMenuExempt("/api/admin/system/monitor", "GET"));
        assertTrue(AdminPathRules.isMenuExempt("/api/admin/ai/provider/list", "GET"));
        assertTrue(AdminPathRules.isMenuExempt("/api/admin/ai/provider/2/models", "GET"));
        assertFalse(AdminPathRules.isMenuExempt("/api/admin/config", "PUT"));
        assertFalse(AdminPathRules.isMenuExempt("/api/admin/config/admin-bg-opacity", "PUT"));
        assertFalse(AdminPathRules.isMenuExempt("/api/admin/article/page", "GET"));
        assertFalse(AdminPathRules.isMenuExempt("/api/admin/sandbox/characters", "GET"));
    }
}
