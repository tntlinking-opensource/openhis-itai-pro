/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.payment.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 银联被扫 Win 回传（settle 请求顶层 {@code clientScanResult}；映射 {@link
 * com.zxplat.domain.infrastructure.integration.payment.ExtensionIn.ThirdPartyScanConfirmExtensionIn}）。
 */
@Data
@Accessors(chain = true)
public class ClientScanResultDto {

    /** {@code prepareConsumerScan} 下发的商户订单号。 */
    private String merTradeNo;
    /** 银联/BPC 交易号。 */
    private String tradeNo;
    /** BPC 原值：{@code WEIX} / {@code ZFBA} / {@code UPAY}。 */
    private String payType;
    /** 响应码；成功 {@code 00}。 */
    private String respCode;
    /** 可读描述。 */
    private String respMsg;
    /**
     * 银联 POS 回传实付金额（分）；可选。
     * 可小于 HIS 明细 amount（优惠或渠道拆分）；settle 仅拒绝大于下单金额。
     */
    private Long txnAmtFen;
}
