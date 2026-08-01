/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 体检收费应用编排共享常量（AS-PE-FIN-001）。
 */
public interface PhysicalExamChargeConstants {

    /** 门诊票据类型编码（零元不开票规则与门诊对齐）。 */
    String INVOICE_TYPE_OUTPATIENT = OutpatientChargeConstants.INVOICE_TYPE_OUTPATIENT;

    /** 正式结算支付结果枚举编码（与 {@link com.openhis.health.domain.share.enums.PaymentOutcome} 一致）。 */
    String SETTLE_OUTCOME_COMPLETED = "1";

    /** 初始化失败（EX-PE-CHG-101）。 */
    String MESSAGE_CODE_INIT_LOAD_FAILED = "EX-PE-CHG-101";

    /** 队列查询失败（EX-PE-CHG-102）。 */
    String MESSAGE_CODE_QUEUE_QUERY_FAILED = "EX-PE-CHG-102";

    /** 非 PE 或就诊无效（EX-PE-CHG-103）。 */
    String MESSAGE_CODE_ENCOUNTER_INVALID = "EX-PE-CHG-103";

    /** 预结算失败（EX-PE-CHG-201）。 */
    String MESSAGE_CODE_PRESETTLE_FAILED = "EX-PE-CHG-201";

    /** 正式结算失败（EX-PE-CHG-202）。 */
    String MESSAGE_CODE_SETTLE_FAILED = "EX-PE-CHG-202";

    /** 预结算失败：未选择收费项。 */
    String MESSAGE_CODE_PRESETTLE_NO_CHARGE_ITEMS = "EX-PE-CHG-201";

    /** 预结算失败：无法解析结算账户。 */
    String MESSAGE_CODE_PRESETTLE_ACCOUNT_MISSING = "EX-PE-CHG-201";

    /** 正式结算失败：草稿非法。 */
    String MESSAGE_CODE_SETTLE_DRAFT_INVALID = "EX-PE-CHG-202";

    /** 正式结算失败：草稿明细为空。 */
    String MESSAGE_CODE_SETTLE_DRAFT_DETAIL_EMPTY = "EX-PE-CHG-202";

    /** 正式结算失败：收费项与草稿不一致。 */
    String MESSAGE_CODE_SETTLE_CHARGE_ITEMS_MISMATCH = "EX-PE-CHG-202";

    /** 正式结算失败：重复收费。 */
    String MESSAGE_CODE_SETTLE_ALREADY_CHARGED = "EX-PE-CHG-202";

    /** 正式结算失败：金额不一致。 */
    String MESSAGE_CODE_SETTLE_AMOUNT_MISMATCH = "EX-PE-CHG-202";

    /** 正式结算失败：收费项状态非法。 */
    String MESSAGE_CODE_SETTLE_CHARGE_ITEM_STATUS_INVALID = "EX-PE-CHG-202";

    /** 正式结算失败：医嘱或费用已变更。 */
    String MESSAGE_CODE_SETTLE_ADVICE_CHANGED = "EX-PE-CHG-202";

    /** 正式结算成功提示码。 */
    String MESSAGE_CODE_SETTLE_SUCCESS = "EX-PE-CHG-202";

    /** 懒生成失败：定价缺失。 */
    String MESSAGE_CODE_ENSURE_PRICE_MISSING = "EX-PE-CHG-201";

    /** 明细展示占位。 */
    String CHARGE_ITEM_DISPLAY_FALLBACK = "收费项目";
}
