/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊医生站「申请退费」应用编排用到的非枚举业务常量（与《代码生成通用规范》B.10.3 对齐）。
 */
public interface DoctorStationRefundApplicationConstants {

    /**
     * 作废药品发放时写入的未执行原因码：门诊退费申请路径触发的未发药作废。
     */
    String MEDICATION_DISPENSE_NOT_PERFORMED_REASON_OUTPATIENT_REFUND_APP = "OUTPATIENT_REFUND_APPLICATION";

    /**
     * 作废耗材发放时写入的未执行原因码：门诊退费申请路径触发的未发耗作废。
     */
    String DEVICE_DISPENSE_NOT_PERFORMED_REASON_OUTPATIENT_REFUND_APP = "OUTPATIENT_REFUND_APPLICATION";

    /**
     * 申请退耗材命令中的原因文本：医生站门诊退费申请。
     */
    String DEVICE_ORDER_REFUND_REASON_OUTPATIENT_REFUND_APP = "门诊医生站退费申请";

    /**
     * 申请退诊疗命令中的原因文本：医生站门诊退费申请。
     */
    String SERVICE_ORDER_REFUND_REASON_OUTPATIENT_REFUND_APP = "门诊医生站退费申请";

    /**
     * 取消诊疗服务申请时的原因文本：门诊退费申请。
     */
    String SERVICE_REQUEST_CANCEL_REASON_OUTPATIENT_REFUND_APP = "门诊退费申请";

    /**
     * 停止诊疗服务申请时的原因文本：门诊退费申请。
     */
    String SERVICE_REQUEST_STOP_REASON_OUTPATIENT_REFUND_APP = "门诊退费申请";

    /**
     * IF-DRA-02 拒绝语义码：耗材已执行（与 AS EX-DRA-201 对齐，供前端稳定分支）。
     */
    String REJECT_REASON_CODE_EX_DRA_201 = "EX-DRA-201";

    /**
     * IF-DRA-03 拒绝语义码：同一原支付禁止分次退费（与 AS EX-DRA-304 对齐）。
     */
    String MESSAGE_CODE_HALF_REFUND_NOT_SUPPORTED = "EX-DRA-304";

    /**
     * IF-DRA-04：已退款或已退药不可撤销（与 AS EX-DRA-501 对齐）。
     */
    String MESSAGE_CODE_REFUND_REVOKE_NOT_ALLOWED = "EX-DRA-501";

    /**
     * IF-DRA-04：禁止半取消（与 AS EX-DRA-502 对齐）。
     */
    String MESSAGE_CODE_HALF_CANCEL_NOT_SUPPORTED = "EX-DRA-502";

    /**
     * IF-DRA-04：禁止混选可申请与可撤销支付单（与 AS EX-DRA-503 对齐）。
     */
    String MESSAGE_CODE_MIXED_APPLY_AND_REVOKE = "EX-DRA-503";
}
