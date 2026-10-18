/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.domain.query.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 财务银联订单分页结果。 */
@Data
@Accessors(chain = true)
public class PageBpcOrdersResult {

    private long total;

    private List<BpcOrderListItemDto> rows = new ArrayList<>();

    @Data
    @Accessors(chain = true)
    public static class BpcOrderListItemDto {

        private Long orderId;

        private Long paymentId;

        private Integer detailIndex;

        private String merTradeNo;

        private String tradeNo;

        private String tranType;

        private Long txnAmtFen;

        /** 银联/POS 回传实付（分） */
        private Long paidAmtFen;

        private String payType;

        private String status;

        private String respCode;

        private String respMsg;

        private String posNo;

        /** fin_payment_reconciliation.status_enum */
        private String paymentStatus;

        /** BPC SUCCESS + C 且 payment 仍为 DRAFT */
        private Boolean pendingSettle;

        /**
         * HIS 已完成逆向退费，但本笔 BPC 消费单仍为 SUCCESS（银联未退）。
         */
        private Boolean orphanRefund;

        private String createdAt;

        private String updatedAt;
    }
}
