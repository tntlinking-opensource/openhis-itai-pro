/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.payment.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 确认 Win 被扫回传（通道 C · {@code applyClientScanResult}）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyScanConfirmExtensionIn {

    /** HIS 支付单主键。 */
    private Long paymentId;
    /** 混合支付明细序号（paymentDetails 列表下标）。 */
    private Integer detailIndex;
    /** 扫码行金额（元，scale=2）。 */
    private BigDecimal amount;
    /** 商户订单号。 */
    private String merTradeNo;
    /** 银联/BPC 交易号。 */
    private String tradeNo;
    /** BPC 原值 pay_type。 */
    private String payType;
    /** 响应码。 */
    private String respCode;
    /** 响应描述。 */
    private String respMsg;
    /** 实际金额（分）。 */
    private Long txnAmtFen;
    /** 操作员。 */
    private Long operatorId;
    /** 院区（多 mid 路由）。 */
    private Long orgId;
}
