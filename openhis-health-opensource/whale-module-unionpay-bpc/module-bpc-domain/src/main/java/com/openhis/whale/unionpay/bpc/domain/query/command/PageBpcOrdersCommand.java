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
