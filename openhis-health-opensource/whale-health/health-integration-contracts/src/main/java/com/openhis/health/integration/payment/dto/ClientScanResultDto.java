/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 银联被扫 Win 回传（settle 请求顶层 {@code clientScanResult}；映射 {@link
 * com.openhis.health.integration.payment.command.ThirdPartyScanConfirmCommand}）。
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
    /** 实际交易金额（分）；可选，与明细 amount 校验一致。 */
    private Long txnAmtFen;
}
