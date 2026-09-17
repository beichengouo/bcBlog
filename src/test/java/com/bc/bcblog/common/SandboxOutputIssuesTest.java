package com.bc.bcblog.common;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「输出可疑」判定测试。
 *
 * 默认只在可疑时才做 AI 自查，所以这里必须"宁松勿严"：
 * 正常输出不能被误判（否则每次都多花钱），而实测踩过的坑（物品名混英文、乱扣钱）必须能命中。
 */
class SandboxOutputIssuesTest {

    private static final List<String> LOCATIONS = Arrays.asList("晨雾森林", "自由城邦联盟");
    private static final List<String> COMPANIONS = Arrays.asList("羽", "灵");

    private JSONObject normal() {
        return JSONUtil.parseObj("{\"location\":\"晨雾森林\",\"coins_change\":2," +
                "\"items_change\":{\"野浆果\":{\"delta\":1,\"description\":\"酸甜的野果\"}}," +
                "\"companions\":[\"羽\"],\"status\":{\"体力\":80,\"魔力\":50,\"饥饿度\":30,\"心情\":\"平静\"}}");
    }

    @Test
    @DisplayName("正常输出不报问题（避免每次行动都多花一次调用）")
    void testNormal() {
        assertTrue(SandboxOutputIssues.find(normal(), LOCATIONS, COMPANIONS, 10).isEmpty());
    }

    @Test
    @DisplayName("实测案例：物品名中英混排要能命中")
    void testEnglishItemName() {
        JSONObject obj = normal();
        obj.set("items_change", JSONUtil.parseObj("{\"晨雾森林 of 野浆果\":{\"delta\":1}}"));
        List<String> issues = SandboxOutputIssues.find(obj, LOCATIONS, COMPANIONS, 10);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("野浆果"));
    }

    @Test
    @DisplayName("实测案例：一次扣 15 金币（超过上限 10）要能命中")
    void testSpendTooMuch() {
        JSONObject obj = normal();
        obj.set("coins_change", -15);
        List<String> issues = SandboxOutputIssues.find(obj, LOCATIONS, COMPANIONS, 10);
        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("上限"));
    }

    @Test
    @DisplayName("地点/角色名/状态越界也要能命中")
    void testOtherIssues() {
        JSONObject obj = JSONUtil.parseObj("{\"location\":\"不存在的地方\",\"companions\":[\"陌生人\"]," +
                "\"status\":{\"体力\":150}}");
        List<String> issues = SandboxOutputIssues.find(obj, LOCATIONS, COMPANIONS, 10);
        assertEquals(3, issues.size());
    }

    @Test
    @DisplayName("缺少可选字段时不要误报（items_change / companions 都可以没有）")
    void testMissingOptionalFields() {
        JSONObject obj = JSONUtil.parseObj("{\"location\":\"晨雾森林\",\"coins_change\":0}");
        assertTrue(SandboxOutputIssues.find(obj, LOCATIONS, Collections.emptyList(), 10).isEmpty());
    }

    @Test
    @DisplayName("集市购买的商品名混英文也要命中")
    void testShopBuyName() {
        JSONObject obj = normal();
        JSONArray buy = new JSONArray();
        buy.add(JSONUtil.parseObj("{\"name\":\"The 野浆果\"}"));
        obj.set("shop_buy", buy);
        List<String> issues = SandboxOutputIssues.find(obj, LOCATIONS, COMPANIONS, 10);
        assertEquals(1, issues.size());
    }
}
