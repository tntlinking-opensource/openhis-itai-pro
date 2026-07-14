/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.job;

import lombok.Data;
import org.quartz.JobExecutionContext;

import java.util.Map;

/**
 * 任务执行上下文
 *
 * <p>封装任务执行所需的信息，包括任务参数、租户信息等</p>
 *
 * @author Whale Framework
 */
@Data
public class JobContext {

    /**
     * Quartz原始上下文
     */
    private JobExecutionContext quartzContext;

    /**
     * 任务参数（从JobDataMap中获取）
     */
    private Map<String, Object> parameters;

    /**
     * 租户ID（如果任务需要多租户支持）
     */
    private Long tenantId;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 任务组名
     */
    private String jobGroup;

    /**
     * 获取参数值
     *
     * @param key 参数键
     * @return 参数值
     */
    public Object getParameter(String key) {
        return parameters != null ? parameters.get(key) : null;
    }

    /**
     * 获取参数值（带类型转换）
     *
     * @param key 参数键
     * @param type 参数类型
     * @param <T> 参数类型
     * @return 参数值
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String key, Class<T> type) {
        Object value = getParameter(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        throw new IllegalArgumentException(
            String.format("Parameter '%s' is not of type %s", key, type.getName()));
    }

    /**
     * 获取字符串参数
     *
     * @param key 参数键
     * @return 参数值
     */
    public String getStringParameter(String key) {
        return getParameter(key, String.class);
    }

    /**
     * 获取整数参数
     *
     * @param key 参数键
     * @return 参数值
     */
    public Integer getIntParameter(String key) {
        return getParameter(key, Integer.class);
    }

    /**
     * 获取长整数参数
     *
     * @param key 参数键
     * @return 参数值
     */
    public Long getLongParameter(String key) {
        return getParameter(key, Long.class);
    }
}
