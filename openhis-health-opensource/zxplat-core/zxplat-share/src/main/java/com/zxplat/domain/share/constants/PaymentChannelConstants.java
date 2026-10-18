/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 收银支付渠道（{@code ter_code_system.id=477 pay_enum}）常量。
 *
 * <p>含扫码半成功（BPC SUCCESS、院内仍 DRAFT）作废门禁码，与挂号
 * {@code OutpatientRegistrationConstants#CANCEL_REASON_CODE_HALF_SUCCESS} 同码，便于前台统一识别。</p>
 *
 * @see com.zxplat.domain.share.enums.PaymentChannel
 */
public interface PaymentChannelConstants {

    /** {@code ter_code_system.id}，收银能力开关（477 pay_enum）。 */
    long PAY_ENUM_CODE_SYSTEM_ID = 477L;

    /** {@code ter_code_system.name}。 */
    String PAY_ENUM_CODE_SYSTEM_NAME = "pay_enum";

    /**
     * 半成功禁止作废支付草稿（银联已扣、院内未落账）。
     *
     * <p>与挂号未收费取消门禁同码 {@code CANCEL-BIZ-413}。</p>
     */
    String EX_HALF_SUCCESS_ABORT_BLOCKED = "CANCEL-BIZ-413";

    /**
     * 半成功禁止作废草稿的窗口提示：引导补结算或先银联退货。
     *
     * <p>正常收退费（settle / 原路退带 {@code clientRefundResult}）不走本提示。</p>
     */
    String MESSAGE_HALF_SUCCESS_ABORT_BLOCKED =
        "本单银联已扣款但院内未落账，不能作废支付草稿：请补结算完成收费，或先银联退货后再取消/重新预结算";
}
