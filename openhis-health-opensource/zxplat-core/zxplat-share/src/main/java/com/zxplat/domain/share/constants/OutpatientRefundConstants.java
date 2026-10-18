/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 门诊退费应用编排共享常量（对齐 AS-OP-FIN-003 §5 EX-REF-*）。
 */
public interface OutpatientRefundConstants {

/** OutpatientRefund 共享常量：code init load failed，值 {@code EX-REF-101}。 */
    String MESSAGE_CODE_INIT_LOAD_FAILED = "EX-REF-101";

/** OutpatientRefund 共享常量：code queue query failed，值 {@code EX-REF-102}。 */
    String MESSAGE_CODE_QUEUE_QUERY_FAILED = "EX-REF-102";

    /** 就诊无效或无可执行退费明细。 */
    String MESSAGE_CODE_EXECUTION_DETAIL_INVALID = "EX-REF-103";

/** OutpatientRefund 共享常量：code medication return pending，值 {@code EX-REF-201}。 */
    String MESSAGE_CODE_MEDICATION_RETURN_PENDING = "EX-REF-201";

/** OutpatientRefund 共享常量：code device return pending，值 {@code EX-REF-202}。 */
    String MESSAGE_CODE_DEVICE_RETURN_PENDING = "EX-REF-202";

/** OutpatientRefund 共享常量：code account not found，值 {@code EX-REF-401}。 */
    String MESSAGE_CODE_ACCOUNT_NOT_FOUND = "EX-REF-401";

/** OutpatientRefund 共享常量：code account switch failed，值 {@code EX-REF-402}。 */
    String MESSAGE_CODE_ACCOUNT_SWITCH_FAILED = "EX-REF-402";

    /** 就诊仅单一记账账户，不支持半退保留行转医保/转自费。 */
    String MESSAGE_CODE_DUAL_ACCOUNT_REQUIRED = "EX-REF-404";

/** OutpatientRefund 共享常量：code no preserved lines，值 {@code EX-REF-403}。 */
    String MESSAGE_CODE_NO_PRESERVED_LINES = "EX-REF-403";

/** OutpatientRefund 共享常量：code payment not refundable，值 {@code EX-REF-301}。 */
    String MESSAGE_CODE_PAYMENT_NOT_REFUNDABLE = "EX-REF-301";

    /** 收费行状态已变化。 */
    String MESSAGE_CODE_CHARGE_ITEM_STATUS_CHANGED = "EX-REF-302";

    /** 实退金额与应退不一致。 */
    String MESSAGE_CODE_REFUND_AMOUNT_MISMATCH = "EX-REF-303";

/** OutpatientRefund 共享常量：code half refund settle failed，值 {@code EX-REF-304}。 */
    String MESSAGE_CODE_HALF_REFUND_SETTLE_FAILED = "EX-REF-304";

    /** 同一原支付禁止分次退费（一次收须一次退，EX-REF-305）。 */
    String MESSAGE_CODE_HALF_REFUND_NOT_SUPPORTED = "EX-REF-305";

    /** 退费渠道须与原支付明细渠道及分桶金额一致（原路返回）。 */
    String MESSAGE_CODE_REFUND_CHANNEL_MUST_ORIGINAL = "EX-REF-306";

    /**
     * 原支付渠道明细合计与挂载收费项合计不一致，禁止原路冲退复制脏分桶（对齐 BR-REF-024 / BR-OPC-024）。
     */
    String MESSAGE_CODE_REFUND_ORIGINAL_DETAIL_UNBALANCED = "EX-REF-307";

/** OutpatientRefund 共享常量：code refund success，值 {@code EX-REF-410}。 */
    String MESSAGE_CODE_REFUND_SUCCESS = "EX-REF-410";

/** OutpatientRefund 共享常量：code account switch success，值 {@code EX-REF-409}。 */
    String MESSAGE_CODE_ACCOUNT_SWITCH_SUCCESS = "EX-REF-409";
}
