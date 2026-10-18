/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.job;

import java.util.HashMap;
import java.util.Map;

/**
 * 从 Quartz JobDataMap 中剥离框架保留字段，得到业务参数字典。
 */
public final class JobExecutionParameterFilter {

    private JobExecutionParameterFilter() {
    }

    /**
     * @param parameters 原始 JobDataMap 快照
     * @return 仅含业务键值对的 Map
     */
    public static Map<String, Object> toBusinessParameters(Map<String, Object> parameters) {
        Map<String, Object> business = new HashMap<>();
        if (parameters == null || parameters.isEmpty()) {
            return business;
        }
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!isReservedKey(entry.getKey())) {
                business.put(entry.getKey(), entry.getValue());
            }
        }
        return business;
    }

    /**
     * 与 zxplat-framework job {@code ScheduledJobQuartzKeys#isReservedKey} 保持一致。
     */
    public static boolean isReservedKey(String key) {
        if (key == null) {
            return true;
        }
        if ("jobBeanName".equals(key)) {
            return true;
        }
        if ("displayName".equals(key)) {
            return true;
        }
        if ("remark".equals(key)) {
            return true;
        }
        if ("tenantId".equals(key)) {
            return true;
        }
        if ("misfirePolicy".equals(key)) {
            return true;
        }
        if ("concurrentPolicy".equals(key)) {
            return true;
        }
        if ("manualTrigger".equals(key)
                || "zxplatManualTrigger".equals(key)
                || "whaleManualTrigger".equals(key)) {
            return true;
        }
        return false;
    }
}
