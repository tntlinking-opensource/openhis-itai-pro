/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊收费应用编排共享常量。
 */
public interface OutpatientChargeConstants {

    /** 门诊票据类型编码。 */
    String INVOICE_TYPE_OUTPATIENT = "OUTPATIENT";

    /** 门诊正式结算成功提示码。 */
    String MESSAGE_CODE_SETTLE_SUCCESS = "EX-OPC-410";
    /** 门诊转账户成功提示码。 */
    String MESSAGE_CODE_SWITCH_SUCCESS = "EX-OPC-409";
    /** 门诊转账户失败：目标账户不存在。 */
    String MESSAGE_CODE_SWITCH_ACCOUNT_NOT_FOUND = "EX-OPC-201";
    /** 门诊转账户失败：切换落库失败或无可切换明细。 */
    String MESSAGE_CODE_SWITCH_FAILED = "EX-OPC-202";
    /** 门诊转账户失败：就诊仅单一记账账户（如纯自费挂号），不支持互转。 */
    String MESSAGE_CODE_SWITCH_DUAL_ACCOUNT_REQUIRED = "EX-OPC-210";

    /** 初始化数据加载失败。 */
    String MESSAGE_CODE_INIT_LOAD_FAILED = "EX-OPC-101";
/** OutpatientCharge 共享常量：code queue query failed，值 {@code EX-OPC-102}。 */
    String MESSAGE_CODE_QUEUE_QUERY_FAILED = "EX-OPC-102";
    /** 待收费明细加载失败。 */
    String MESSAGE_CODE_PENDING_ITEMS_LOAD_FAILED = "EX-OPC-103";

    /**
     * 待收费列表/预结算预览：收费项定义缺失或 {@code charge_name} 为空时的展示占位（非异常消息字面量）。
     */
    String PENDING_CHARGE_ITEM_DISPLAY_FALLBACK = "收费项目";

    /** 预结算失败：未选择收费项。 */
    String MESSAGE_CODE_PRESETTLE_NO_CHARGE_ITEMS = "EX-OPC-301";
    /** 预结算失败：缺少诊断。 */
    String MESSAGE_CODE_PRESETTLE_DIAGNOSIS_MISSING = "EX-OPC-302";
    /** 预结算失败：诊断与收费项锚点不一致。 */
    String MESSAGE_CODE_PRESETTLE_DIAGNOSIS_MISMATCH = "EX-OPC-303";
    /** 预结算失败：无法解析收费项归属的结算账户（缺账户且就诊未开立默认自费账户）。与 AS §5 中 EX-OPC-308 对齐（勿占用 EX-OPC-304 医疗类型语义）。 */
    String MESSAGE_CODE_PRESETTLE_ACCOUNT_MISSING = "EX-OPC-308";
    /** 预结算失败：合同不存在。 */
    String MESSAGE_CODE_PRESETTLE_CONTRACT_NOT_FOUND = "EX-OPC-306";

    /** 正式结算失败：草稿不存在或状态非法。 */
    String MESSAGE_CODE_SETTLE_DRAFT_INVALID = "EX-OPC-401";
    /** 正式结算失败：草稿明细为空。 */
    String MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY = "EX-OPC-402";
    /** 正式结算失败：收费项与草稿不一致。 */
    String MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH = "EX-OPC-403";
    /** 正式结算失败：重复收费。 */
    String MESSAGE_CODE_SETTLE_ALREADY_CHARGED = "EX-OPC-404";
    /** 正式结算失败：金额不一致。 */
    String MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH = "EX-OPC-405";
    /** 正式结算失败：收费项状态非法。 */
    String MESSAGE_CODE_SETTLE_CHARGE_ITEM_STATUS_INVALID = "EX-OPC-406";
    /** 正式结算失败：医嘱已签退或费用已回草稿，须重新预结算（GAP-STALE-001）。 */
    String MESSAGE_CODE_SETTLE_ADVICE_CHANGED = "EX-OPC-411";

    /**
     * 收银队列汇总收费状态编码：同一就诊下多条收费明细状态不一致（非 {@code ChargeItemStatus} 枚举字面量）。
     */
    String CASHIER_CHARGE_ROLLUP_MIXED_STATUS_CODE = "MIXED";

    /** 收银队列汇总收费状态展示文案：多条明细状态不一致。 */
    String CASHIER_CHARGE_ROLLUP_MIXED_STATUS_TEXT = "费用状态不一致";
}
