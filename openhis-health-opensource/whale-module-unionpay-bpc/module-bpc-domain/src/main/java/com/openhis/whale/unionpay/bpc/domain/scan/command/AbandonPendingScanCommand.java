/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.scan.command;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 关闭未确认的被扫 PENDING（取消/超时/被替换/草稿作废）。
 *
 * <p>不调用银联关单 S；仅本院收单状态收口，避免悬挂 PENDING 与重复 prepare。</p>
 */
@Data
@Accessors(chain = true)
public class AbandonPendingScanCommand {

    /** 支付草稿/支付单主键；与 merTradeNo 至少填一个。 */
    private Long paymentId;

    /** 仅关闭指定商户订单号；空则关闭该 payment 下全部 PENDING 消费单。 */
    private String merTradeNo;

    /** 关闭原因（写入 resp_msg，建议 ≤200 字）。 */
    private String reason;

    /** 可选：落库 resp_code（≤8），如 TO/CL/RP/AB。 */
    private String respCode;
}
