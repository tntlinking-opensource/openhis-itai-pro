/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 住院日结交账应用编排消息码与展示常量（对齐 AS-IP-FIN-005 §5）。
 */
public interface InpatientDailySettlementConstants {

    /** 页面初始化失败（EX-IP-DCL-101）。 */
    String EX_IP_DCL_101 = "EX-IP-DCL-101";

    /** 日期或经办人参数非法（EX-IP-DCL-102）。 */
    String EX_IP_DCL_102 = "EX-IP-DCL-102";

    /** 日结查询失败（EX-IP-DCL-103）。 */
    String EX_IP_DCL_103 = "EX-IP-DCL-103";

    /** 勾选 payment 非法（EX-IP-DCL-105）。 */
    String EX_IP_DCL_105 = "EX-IP-DCL-105";

    /** 漏交硬栏：截止前存在窗外更早未交账（EX-IP-DCL-106）。 */
    String EX_IP_DCL_106 = "EX-IP-DCL-106";

    /** 撤回交账权限不足（EX-IP-DCL-107）。 */
    String EX_IP_DCL_107 = "EX-IP-DCL-107";

    /** 无可交账流水（EX-IP-DCL-201）。 */
    String EX_IP_DCL_201 = "EX-IP-DCL-201";

    /** 并发交账冲突（EX-IP-DCL-301）。 */
    String EX_IP_DCL_301 = "EX-IP-DCL-301";

    /** 交账成功提示语义码（EX-IP-DCL-410，非异常）。 */
    String EX_IP_DCL_410 = "EX-IP-DCL-410";

    /** 费用性质筛选项：自费。 */
    String EXPENSE_SELF_PAY = "selfPay";

    /** 费用性质筛选项：医保。 */
    String EXPENSE_INSURANCE = "insurance";

    /** 费用性质展示：自费。 */
    String EXPENSE_SELF_PAY_LABEL = "自费";

    /** 费用性质展示：医保。 */
    String EXPENSE_INSURANCE_LABEL = "医保";

    /** 预交金分类展示标签（BR-IP-DCL-002）。 */
    String DEPOSIT_CATEGORY_LABEL = "预交金";

    /** 历史预交金 payment kind 落库码（与 {@code HOSPITAL_DEPOSIT} 同义，交账查询须一并纳入）。 */
    String LEGACY_HOSPITAL_DEPOSIT_KIND_CODE = "PRE_DEPOSIT";

    /** 住院日结上半区排除的 fin_type_code：诊察费（门诊号源归并码 1012，住院不展示）。 */
    String INPATIENT_EXCLUDED_CONSULTATION_FEE_FIN_TYPE_CODE = "1012";

    /** 病区名缺失时的展示降级（AS-SDD §7.4）。 */
    String WARD_NAME_FALLBACK = "—";

    /** 患者分项姓名缺失时的展示降级（BR-IP-DCL-030）。 */
    String PATIENT_NAME_FALLBACK = "未知患者";

    /** 患者分项分组键前缀：按就诊。 */
    String PATIENT_GROUP_KEY_ENCOUNTER_PREFIX = "E:";

    /** 患者分项分组键前缀：按患者（无就诊时回落）。 */
    String PATIENT_GROUP_KEY_PATIENT_PREFIX = "P:";

    /** 患者分项分组键前缀：单笔 payment（无就诊/患者时回落）。 */
    String PATIENT_GROUP_KEY_PAYMENT_PREFIX = "PAY:";

    /** 下半区扁平行 key：医保上传差额（患者分项剔除用）。 */
    String SUMMARY_ROW_KEY_CHS_RETAIL_UPLOAD_DIFF = "chsRetailUploadDiffAmount";

    /** 渠道汇总金额单位。 */
    String AMOUNT_UNIT_YUAN = "元";

/** InpatientDailySettlement 共享常量：display scale，值 {@code 2}。 */
    int AMOUNT_DISPLAY_SCALE = 2;

    /** 财务代交账经办人下拉单次加载上限。 */
    int CASHIER_OPTION_PAGE_SIZE = 500;
}
