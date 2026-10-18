/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.policy.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 放号策略渠道配额 JSON 列 ↔ 结构化 Map（出参禁止透传 JSON 字符串）。
 */
public final class ReleasePolicyJsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Integer>> CHANNEL_QUOTA_TYPE = new TypeReference<>() {};

    private ReleasePolicyJsonSupport() {
    }

    /**
     * 库表 {@code channel_quota_json} → 结构化渠道配额。
     *
     * @param json 如 {@code {"WINDOW":60,"MINIAPP":40}}；空则返回空 Map
     * @return 渠道编码 → 配额百分比；非 null
     */
    public static Map<String, Integer> parseChannelQuotas(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Integer> parsed = MAPPER.readValue(json, CHANNEL_QUOTA_TYPE);
            if (parsed == null) {
                return new LinkedHashMap<>();
            }
            return parsed;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "渠道配额 JSON 格式非法");
        }
    }

    /**
     * 结构化渠道配额 → 落库 JSON 字符串。
     *
     * @param quotas 渠道编码 → 配额；空或 null 时返回 null
     * @return JSON 字符串；无配额时 null
     */
    public static String writeChannelQuotas(Map<String, Integer> quotas) {
        if (quotas == null || quotas.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(quotas);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "渠道配额序列化失败");
        }
    }
}
