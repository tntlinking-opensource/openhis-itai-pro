/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.order.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 银联 BPC 订单（对应表 upc_bpc_order）。
 */
@Getter
@Setter
@Entity
@Table(name = "upc_bpc_order")
public class BpcOrder extends BaseAuditedEntity {

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "detail_index", nullable = false)
    private Integer detailIndex = 0;

    @Column(name = "mer_trade_no", nullable = false, length = 64)
    private String merTradeNo;

    @Column(name = "trade_no", length = 64)
    private String tradeNo;

    @Column(name = "tran_type", nullable = false, length = 8)
    private String tranType;

    @Column(name = "txn_amt_fen", nullable = false)
    private Long txnAmtFen;

    @Column(name = "pay_type", length = 16)
    private String payType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "resp_code", length = 8)
    private String respCode;

    @Column(name = "resp_msg", length = 256)
    private String respMsg;

    @Column(name = "profile_id", nullable = false, length = 32)
    private String profileId;

    @Column(name = "pos_no", length = 32)
    private String posNo;

    @Column(name = "scan_code_digest", length = 64)
    private String scanCodeDigest;
}
