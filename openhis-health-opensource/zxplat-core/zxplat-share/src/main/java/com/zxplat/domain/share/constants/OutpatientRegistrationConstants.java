/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 门诊挂号应用编排共享常量（对齐 AS-OP-FIN-001 IF-REG-09/10）。
 *
 * <p>退号收费门禁（BR-REG-015）仅保留非挂号锚点账单三态原因码；药耗收口由 AS-OP-FIN-003 IF-REF-04 承担。</p>
 */
public interface OutpatientRegistrationConstants {

    /** 退号校验通过原因码。 */
    String CANCEL_REASON_CODE_OK = "OK";

    /** 退号校验：存在待收费明细（非挂号锚点）。 */
    String CANCEL_REASON_CODE_PENDING_CHARGE = "CANCEL-BIZ-410";

    /** 退号校验：存在已收费未全退账单（非挂号锚点）。 */
    String CANCEL_REASON_CODE_SETTLED_CHARGE = "CANCEL-BIZ-411";

    /** 退号校验：存在退费处理中账单（非挂号锚点）。 */
    String CANCEL_REASON_CODE_REFUNDING_CHARGE = "CANCEL-BIZ-412";

    /** 存在待收费项目，须医生撤回已签发医嘱后再退号。 */
    String CANCEL_MESSAGE_PENDING_CHARGE =
        "存在待收费项目，请医生撤回已签发医嘱后再退号";

    /** 存在已收费账单，须先完成门诊退费后再退号。 */
    String CANCEL_MESSAGE_SETTLED_CHARGE =
        "存在已收费账单，请先完成门诊退费后再退号";

    /** 存在退费处理中账单，须待退费完成后再退号。 */
    String CANCEL_MESSAGE_REFUNDING_CHARGE =
        "存在退费处理中的账单，请待退费完成后再退号";

    /**
     * 退号校验：已接诊/暂离/完诊，须医生先取消接诊（与账单门禁无关，表示医生同意退号）。
     */
    String CANCEL_REASON_CODE_NEED_CANCEL_RECEPTION = "CANCEL-BIZ-416";

    /**
     * 已接诊不可直接退号：引导患者回医生站取消接诊。
     */
    String CANCEL_MESSAGE_NEED_CANCEL_RECEPTION =
        "请让患者去医生处取消接诊后再退号";

    /**
     * 未收费取消门禁：半成功（银联已扣或医保 2207 已确认，院内尚未落成挂号）。
     *
     * <p>禁止走「未收费取消」作废草稿，避免钱已收、就诊不可见。</p>
     */
    String CANCEL_REASON_CODE_HALF_SUCCESS = "CANCEL-BIZ-413";

    /**
     * 半成功禁止取消的窗口提示：引导补结算或银联退货+医保撤销。
     */
    String CANCEL_MESSAGE_HALF_SUCCESS =
        "本单银联或医保已收款但挂号未落成，不能取消：请续付补结算完成挂号，或先银联退货并撤销医保结算后再取消";

    /**
     * 挂号窗口列表：退号完成行状态展示文案。
     *
     * <p>库内状态仍为 {@code CANCELLED}；窗口不展示枚举默认「已取消」，以免与未收费取消混淆。</p>
     */
    String CLINIC_REGISTER_STATUS_TEXT_REFUNDED = "退号";

    /**
     * 挂号明细/窗口：未收费取消终态展示文案。
     *
     * <p>库内状态仍为 {@code ENTERED_IN_ERROR}；不直接展示枚举「错误录入」，以免与业务「取消挂号」脱节。</p>
     */
    String CLINIC_REGISTER_STATUS_TEXT_ENTERED_IN_ERROR = "未收费取消";

    /** 复诊0元号资格未满足（BR-REG-020）。 */
    String REG_REASON_CODE_HISTORICAL_FOLLOW_UP_INELIGIBLE = "REG-BIZ-420";

    /**
     * 复诊0元号：本科室近 72 小时内无已接诊记录时的窗口提示。
     */
    String REG_MESSAGE_HISTORICAL_FOLLOW_UP_INELIGIBLE =
        "该患者在本科室近72小时内无已接诊记录，不能使用复诊0元号";
}
