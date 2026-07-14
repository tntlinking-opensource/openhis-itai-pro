/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.share;

/**
 * 博思电子票据域错误码常量。
 *
 * <p>与 {@code einv_boss_issue_task.last_error_code} 及 API 异常对齐。</p>
 */
public final class EinvBossErrorCodes {

    /** 明细合计与开票总额不一致，禁止调用博思。 */
    public static final String EINV_AMOUNT_MISMATCH = "EINV_AMOUNT_MISMATCH";

    /** 明细合计与支付金额不一致（警告态，不阻断调额保存）。 */
    public static final String EINV_PAYMENT_AMOUNT_MISMATCH = "EINV_PAYMENT_AMOUNT_MISMATCH";

    /** 无活跃开票任务。 */
    public static final String EINV_TASK_NOT_FOUND = "EINV_TASK_NOT_FOUND";

    /** 任务已被其他节点抢占（蓝绿并发消费）。 */
    public static final String EINV_TASK_CONCURRENT = "EINV_TASK_CONCURRENT";

    /** 冲红正在被其他节点处理。 */
    public static final String EINV_WRITE_OFF_CONCURRENT = "EINV_WRITE_OFF_CONCURRENT";

    /** 票据已开具，重复开具门控。 */
    public static final String EINV_ALREADY_ISSUED = "EINV_ALREADY_ISSUED";

    /** 非草稿态禁止调额（已开具 / 已作废）。 */
    public static final String EINV_ADJUST_NOT_ALLOWED = "EINV_ADJUST_NOT_ALLOWED";

    /** 博思平台返回非 S0000。 */
    public static final String EINV_BOSS_REJECT = "EINV_BOSS_REJECT";

    /** 调用博思 HTTP 超时。 */
    public static final String EINV_BOSS_TIMEOUT = "EINV_BOSS_TIMEOUT";

    /** 无电子票据单据。 */
    public static final String EINV_DOCUMENT_NOT_FOUND = "EINV_DOCUMENT_NOT_FOUND";

    /** 收费对照不存在。 */
    public static final String EINV_CONTRAST_NOT_FOUND = "EINV_CONTRAST_NOT_FOUND";

    /** 院内编码对照已存在。 */
    public static final String EINV_CONTRAST_DUPLICATE = "EINV_CONTRAST_DUPLICATE";

    /** 请求缺少租户上下文。 */
    public static final String EINV_TENANT_REQUIRED = "EINV_TENANT_REQUIRED";

    private EinvBossErrorCodes() {
    }
}
