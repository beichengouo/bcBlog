package com.bc.bcblog.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 二次验证路径规则测试。
 *
 * 目的：防止"该要密码的接口漏掉"（等于门没锁）以及"只读接口被误拦"（后台到处弹验证框）。
 */
class SecurityPathRulesTest {

    @Test
    @DisplayName("沙盒世界所有接口都要二次验证（这次就是漏在这里）")
    void testSandboxProtected() {
        assertTrue(SecurityPathRules.needVerify("/api/admin/sandbox/characters", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/sandbox/characters/run", "POST"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/sandbox/worlds", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/sandbox/shop/orders", "GET"));
    }

    @Test
    @DisplayName("站点设置只拦写操作，读取放行")
    void testConfigWriteOnly() {
        // 后台壁纸层/看板娘管理/邮件管理每次进页面都会读它，读不能弹框
        assertFalse(SecurityPathRules.needVerify("/api/admin/config", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/config", "PUT"));

        assertTrue(SecurityPathRules.needVerify("/api/admin/config/logo", "POST"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/config/logo", "DELETE"));

        // 背景透明度、看板娘开关属于外观设置，不该每次翻页都弹验证
        assertFalse(SecurityPathRules.needVerify("/api/admin/config/admin-bg-opacity", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/config/admin-bg-opacity", "PUT"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/config/live2d-enabled", "PUT"));

        // 第三方 Key 是敏感信息，读也要验证
        assertTrue(SecurityPathRules.needVerify("/api/admin/config/ip-location-ak", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/config/gaode-ip-key", "POST"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/config/acg-cover-token", "GET"));
    }

    @Test
    @DisplayName("用户 / 邮件 / AI / 日志 / 审计 / 积分 / 等级都要二次验证")
    void testOtherSensitivePaths() {
        assertTrue(SecurityPathRules.needVerify("/api/admin/user/list", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/member/list", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/invite/list", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/email/config", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/gitalk/comments", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/ai/provider/list", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/deepseek/balance", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/log/login", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/audit/list", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/point/grant", "POST"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/level/save", "POST"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/ip-location/query", "GET"));
        assertTrue(SecurityPathRules.needVerify("/api/admin/system/cleanup", "POST"));
    }

    @Test
    @DisplayName("日常内容运营与公共接口不受二次验证影响")
    void testDailyPathsNotProtected() {
        assertFalse(SecurityPathRules.needVerify("/api/admin/article/page", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/article/save", "POST"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/category/tree", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/tag/list", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/comment/page", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/photo/list", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/resource/list", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/announcement/list", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/background/list", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/live2d/list", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/music/playlist", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/upload/image", "POST"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/dashboard/stats", "GET"));
        // 系统监控只读，仪表盘一进来就要用，不能弹框
        assertFalse(SecurityPathRules.needVerify("/api/admin/system/monitor", "GET"));
        // 安全设置页本身必须免验证，否则没设过安全密码的人无处可设
        assertFalse(SecurityPathRules.needVerify("/api/admin/security/status", "GET"));
        assertFalse(SecurityPathRules.needVerify("/api/admin/security/password", "POST"));
        // 前台接口不受影响
        assertFalse(SecurityPathRules.needVerify("/api/portal/sandbox", "GET"));
    }
}
