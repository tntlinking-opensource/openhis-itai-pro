/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.job.application.support;

/**
 * Whale 定时任务在 Quartz 中的键名与 JobDataMap 保留字段约定。
 */
public final class ScheduledJobQuartzKeys {

    /** Trigger 名称后缀，与 {@link com.openhis.whale.core.infrastructure.job.JobScheduler} 保持一致。 */
    public static final String TRIGGER_SUFFIX = "_trigger";

    /** JobDataMap：Spring 容器中 IJob Bean 名称。 */
    public static final String JOB_BEAN_NAME = "jobBeanName";

    /** JobDataMap：运维可见的任务显示名。 */
    public static final String DISPLAY_NAME = "displayName";

    /** JobDataMap：备注。 */
    public static final String REMARK = "remark";

    /** JobDataMap：可选租户 ID。 */
    public static final String TENANT_ID = "tenantId";

    /** JobDataMap：错过触发策略 code（便于编辑回显）。 */
    public static final String MISFIRE_POLICY = "misfirePolicy";

    /** JobDataMap：并发策略 code（便于编辑回显）。 */
    public static final String CONCURRENT_POLICY = "concurrentPolicy";

    /** 立即执行时在 Trigger JobDataMap 中标记手动触发（不写回任务定义）。 */
    public static final String MANUAL_TRIGGER = "whaleManualTrigger";

    /** 默认任务组。 */
    public static final String DEFAULT_GROUP = "DEFAULT";

    private ScheduledJobQuartzKeys() {
    }

    /**
     * 判断 JobDataMap 键是否为框架保留字段（不应作为业务参数展示/编辑）。
     *
     * @param key 参数键
     * @return 是否为保留键
     */
    public static boolean isReservedKey(String key) {
        if (key == null) {
            return true;
        }
        if (JOB_BEAN_NAME.equals(key)) {
            return true;
        }
        if (DISPLAY_NAME.equals(key)) {
            return true;
        }
        if (REMARK.equals(key)) {
            return true;
        }
        if (TENANT_ID.equals(key)) {
            return true;
        }
        if (MISFIRE_POLICY.equals(key)) {
            return true;
        }
        if (CONCURRENT_POLICY.equals(key)) {
            return true;
        }
        if (MANUAL_TRIGGER.equals(key)) {
            return true;
        }
        return false;
    }
}
