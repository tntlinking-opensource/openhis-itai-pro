/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.common.model;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.TypeReference;
import com.openhis.chs.gateway.client.ResultBody;

import java.util.Map;

/** 平台 scheme DTO ↔ JSON 转换（input 组装与 output 解析） */
public final class ChsSchemeJsonSupport {

    private ChsSchemeJsonSupport() {
    }

    /**
     * 将 DTO 转为平台 input 对象（保留 @JSONField 蛇形命名）。
     *
     * @param dto 业务 DTO
     * @return 可 JSON 序列化的 Map */
    public static Map<String, Object> toInputMap(Object dto) {
        if (dto == null) {
            return Map.of();
        }
        return JSON.parseObject(JSON.toJSONString(dto), new TypeReference<Map<String, Object>>() {
        });
    }

    /** DTO → JSONObject（Facade↔Domain 桥接） */
    public static JSONObject toJsonObject(Object dto) {
        if (dto == null) {
            return new JSONObject();
        }
        return JSON.parseObject(JSON.toJSONString(dto));
    }

    /** JSONObject → 强类型 DTO */
    public static <T> T fromJsonObject(JSONObject json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return json.to(type);
    }

    /** 解析 ResultBody.output 根节点 */
    public static <T> T parseOutput(ResultBody body, Class<T> type) {
        if (body == null || isEmpty(body.getOutput())) {
            return null;
        }
        return JSON.parseObject(body.getOutput().trim(), type);
    }

    /** 解析 ResultBody.output 下指定 key 的嵌套节点 */
    public static <T> T parseNested(ResultBody body, String key, Class<T> type) {
        if (body == null || isEmpty(body.getOutput()) || isEmpty(key)) {
            return null;
        }
        JSONObject root = JSON.parseObject(body.getOutput().trim());
        if (root == null) {
            return null;
        }
        JSONObject nested = root.getJSONObject(key);
        if (nested == null) {
            return null;
        }
        return nested.to(type);
    }

    /** 解析 output 字符串根节点 */
    public static <T> T parseOutputString(String output, Class<T> type) {
        if (isEmpty(output)) {
            return null;
        }
        return JSON.parseObject(output.trim(), type);
    }

    /** 解析 output 字符串下指定 key */
    public static <T> T parseNestedString(String output, String key, Class<T> type) {
        if (isEmpty(output) || isEmpty(key)) {
            return null;
        }
        JSONObject root = JSON.parseObject(output.trim());
        if (root == null) {
            return null;
        }
        JSONObject nested = root.getJSONObject(key);
        if (nested == null) {
            return null;
        }
        return nested.to(type);
    }
}
