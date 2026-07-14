/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * MRMS 模块共享常量（跨 domain / application 只读引用）。
 */
public final class MrmsConstants {

    /** 定时任务/内部 Job 无登录态时使用的系统操作人占位（质控执行审计）。 */
    public static final long SYSTEM_OPERATOR_ID = 0L;

    /** EMR 入院记录文书类型 code（入院病情解析候选）。 */
    public static final String EMR_DOC_TYPE_ADMISSION_RECORD = "ADMISSION_RECORD";

    /** EMR 出院小结文书类型 code（入院病情解析候选）。 */
    public static final String EMR_DOC_TYPE_DISCHARGE_SUMMARY = "DISCHARGE_SUMMARY";

    private MrmsConstants() {
    }
}
