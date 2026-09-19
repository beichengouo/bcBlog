package com.bc.bcblog.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「能不能互动」的规则测试。
 *
 * 重点是用户实测报的那个问题：地图上有几个一级地点彼此不到 30km，
 * 旧规则只看距离，于是两个不同地区的角色也会互动、还会互相触发回应行动。
 */
class SandboxSocialTest {

    @Test
    void 不同一级地点即使挨得很近也不能互动() {
        // 5km、但分属两个一级地点 —— 这就是要拦掉的情况
        assertFalse(SandboxSocial.canInteract(false, false, 5, 30, true));
    }

    @Test
    void 同一个二级地点不管距离都算在一起() {
        assertTrue(SandboxSocial.canInteract(true, true, 0, 30, true));
        assertTrue(SandboxSocial.canInteract(true, true, 99, 30, true));
    }

    @Test
    void 同一级地点内按距离判断() {
        assertTrue(SandboxSocial.canInteract(true, false, 12, 30, true));
        assertFalse(SandboxSocial.canInteract(true, false, 45, 30, true));
    }

    @Test
    void 关掉强制同地区时退回旧行为() {
        assertTrue(SandboxSocial.canInteract(false, false, 5, 30, false));
        assertFalse(SandboxSocial.canInteract(false, false, 45, 30, false));
    }

    @Test
    void 不限距离时仍然要求同一级地点() {
        assertTrue(SandboxSocial.canInteract(true, false, 999, 0, true));
        assertFalse(SandboxSocial.canInteract(false, false, 1, 0, true));
    }
}
