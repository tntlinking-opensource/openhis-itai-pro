/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.enums;

import com.openhis.health.domain.share.constants.PaymentChannelConstants;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.Getter;

/**
 * 支付渠道（{@code fin_payment_rec_detail.pay_enum} 持久化身份）。
 *
 * <p>用户收银可选：{@link #CASH}、{@link #ALIPAY}、{@link #WECHAT}、{@link #BANK_CARD}、{@link #SCAN_PAY}；
 * {@link #SCAN_PAY} 仅 UI/入参，禁止落库（ADR-PAY-001）；
 * {@link #CASHIER} 仅门诊收费内部草稿落库，不可作为前端 {@code payChannelCode} 入参；
 * {@link #INSURANCE_FUND}、{@link #INSURANCE_ACCT} 由服务端在医保正式结算后写入。</p>
 */
@Getter
public enum PaymentChannel implements EnumInterface {

/** 现金（PaymentChannel）。 */
    CASH(PaymentChannelConstants.PAY_ENUM_CODE_SYSTEM_ID, "CASH", "现金", "", 1L, true),

/** 支付宝（PaymentChannel）。 */
    ALIPAY(PaymentChannelConstants.PAY_ENUM_CODE_SYSTEM_ID, "ALIPAY", "支付宝", "", 2L, true),

/** 微信（PaymentChannel）。 */
    WECHAT(PaymentChannelConstants.PAY_ENUM_CODE_SYSTEM_ID, "WECHAT", "微信", "", 3L, true),

    /** 银行卡（含 BPC UPAY 被扫落库）。 */
    BANK_CARD(PaymentChannelConstants.PAY_ENUM_CODE_SYSTEM_ID, "BANK_CARD", "银行卡", "", 4L, true),

    /** 扫码支付（银联被扫 UI 入口；不落库 pay_enum）。 */
    SCAN_PAY(PaymentChannelConstants.PAY_ENUM_CODE_SYSTEM_ID, "SCAN_PAY", "扫码支付", "", 5L, true),

    /** 医保基金支付（非用户单选，服务端写入 fin_payment_rec_detail）。 */
    INSURANCE_FUND(0L, "INSURANCE_FUND", "医保统筹", "", 6L, false),

    /** 医保个账支付（非用户单选，服务端写入 fin_payment_rec_detail）。 */
    INSURANCE_ACCT(0L, "INSURANCE_ACCT", "医保个账", "", 7L, false),

    /** 门诊收银台内部渠道（支付草稿明细默认渠道，非用户单选）。 */
    CASHIER(0L, "CASHIER", "收银台", "", 8L, false);

    private final Long codeSystemId;
    private final String code;
    private final String display;
    private final String definition;
    private final Long sortOrder;
    private final boolean userSelectable;

    PaymentChannel(
        Long codeSystemId,
        String code,
        String display,
        String definition,
        Long sortOrder,
        boolean userSelectable) {
        this.codeSystemId = codeSystemId;
        this.code = code;
        this.display = display;
        this.definition = definition;
        this.sortOrder = sortOrder;
        this.userSelectable = userSelectable;
    }

    /**
     * 是否允许用户在前端选择并作为 {@code payChannelCode} 提交。
     *
     * @return 用户可选为 true
     */
    public boolean isUserSelectable() {
        return this.userSelectable;
    }

    /**
     * 是否禁止写入 {@code fin_payment_rec_detail.pay_enum}（UI 专用渠道）。
     */
    public boolean isUiOnly() {
        return this == SCAN_PAY;
    }

    /**
     * 按持久化 code 解析；未命中返回 {@code null}。
     *
     * @param code {@code pay_enum} 值
     * @return 枚举项或 null
     */
    public static PaymentChannel fromCode(String code) {
        return EnumUtil.fromCode(PaymentChannel.class, code);
    }
}
