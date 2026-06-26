package com.openhis.whale.einvoice.boss.contracts.constants;

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

    /** 票据已开具，重复开具门控。 */
    public static final String EINV_ALREADY_ISSUED = "EINV_ALREADY_ISSUED";

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

    private EinvBossErrorCodes() {
    }
}
