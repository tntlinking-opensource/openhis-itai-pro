/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.query.command;

import lombok.Data;

/**
 * 财务银联订单分页查询。
 */
@Data
public class PageBpcOrdersCommand {

    private Integer pageNo;

    private Integer pageSize;

    /** yyyy-MM-dd */
    private String dateFrom;

    /** yyyy-MM-dd */
    private String dateTo;

    /** 仅 BPC 已成功、HIS payment 仍为 DRAFT 的半成功单 */
    private Boolean pendingSettleOnly;

    /**
     * 仅「HIS 已退、银联未退」孤儿单：BPC SUCCESS 消费单，且存在 relation_id 逆向 REFUNDED，
     * 且尚无 BPC 退货单（tran_type=R / status=REFUNDED）。
     */
    private Boolean orphanRefundOnly;

    private Long paymentId;

    private String merTradeNo;

    private String tradeNo;

    /** PENDING / SUCCESS / FAILED / REFUNDED */
    private String status;

    /** WEIX / ZFBA / UPAY 等 */
    private String payType;

    /** C / G / R */
    private String tranType;
}
