/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;

/**
 * 门诊日结交账应用编排消息码与展示常量（对齐 AS-OP-FIN-004 §5）。
 */
public interface OutpatientDailySettlementConstants {

    /** 页面初始化失败（EX-OP-DCL-101）。 */
    String EX_OP_DCL_101 = "EX-OP-DCL-101";

    /** 日期或经办人参数非法（EX-OP-DCL-102）。 */
    String EX_OP_DCL_102 = "EX-OP-DCL-102";

    /** 日结查询失败（EX-OP-DCL-103）。 */
    String EX_OP_DCL_103 = "EX-OP-DCL-103";

    /** 勾选 payment 非法（EX-OP-DCL-105）。 */
    String EX_OP_DCL_105 = "EX-OP-DCL-105";

    /** 漏交硬栏：截止前存在窗外更早未交账（EX-OP-DCL-106）。 */
    String EX_OP_DCL_106 = "EX-OP-DCL-106";

    /** 撤回交账权限不足（EX-OP-DCL-107）。 */
    String EX_OP_DCL_107 = "EX-OP-DCL-107";

    /** 无可交账流水（EX-OP-DCL-201）。 */
    String EX_OP_DCL_201 = "EX-OP-DCL-201";

    /** 并发交账冲突（EX-OP-DCL-301）。 */
    String EX_OP_DCL_301 = "EX-OP-DCL-301";

    /** 交账成功提示语义码（EX-OP-DCL-410，非异常）。 */
    String EX_OP_DCL_410 = "EX-OP-DCL-410";

    /** 费用性质筛选项：自费（BR-OP-DCL-009）。 */
    String EXPENSE_SELF_PAY = "selfPay";

    /** 费用性质筛选项：医保（BR-OP-DCL-009）。 */
    String EXPENSE_INSURANCE = "insurance";

    /** 费用性质展示：自费。 */
    String EXPENSE_SELF_PAY_LABEL = "自费";

    /** 费用性质展示：医保。 */
    String EXPENSE_INSURANCE_LABEL = "医保";

    /** 渠道汇总金额单位。 */
    String AMOUNT_UNIT_YUAN = "元";

    /** 优惠金额占位（本期固定 0.00）。 */
    String DISCOUNT_AMOUNT_ZERO = "0.00";

/** OutpatientDailySettlement 共享常量：display scale，值 {@code 2}。 */
    int AMOUNT_DISPLAY_SCALE = 2;

    /** 财务代交账经办人下拉单次加载上限。 */
    int CASHIER_OPTION_PAGE_SIZE = 500;

    /** charge_item 无定义名时的项目行展示兜底。 */
    String CHARGE_LINE_NAME_FALLBACK = "收费项目";

    /** 交账弹窗支付方式：费用性质与渠道分隔符。 */
    String PAY_METHOD_LABEL_SEPARATOR = "·";

    /** 医保渠道短名：统筹。 */
    String PAY_METHOD_INSURANCE_FUND_SHORT = "统筹";

    /** 医保渠道短名：个账。 */
    String PAY_METHOD_INSURANCE_ACCT_SHORT = "个账";

    /** 诊察费财务类别码（号源/挂号在日结汇总归并目标，对齐 ter_concept 236/1012）。 */
    String REGISTRATION_FEE_FIN_TYPE_CODE = "1012";

    /** 体检财务类别码（fin_type_code，老日结单 peisCnt 锚点）。 */
    String PHYSICAL_EXAM_FIN_TYPE_CODE = "9999";

    /** payment 统计快照按 fin_type_code 汇总时的 method_code 标识。 */
    String PAYMENT_REC_STATIC_FIN_TYPE_METHOD = "fin_type_code";

    /** 自费合同 bus_no 占位（老日结单 0000 口径，院点可配置覆盖）。 */
    String SELF_PAY_CONTRACT_BUS_NO = "0000";

    /** 省医保合同 bus_no 占位（老日结单 229900 口径）。 */
    String PROVINCIAL_INSURANCE_CONTRACT_BUS_NO = "229900";

    /** 市医保合同 bus_no 占位（老日结单 220100 口径）。 */
    String MUNICIPAL_INSURANCE_CONTRACT_BUS_NO = "220100";

    /** 市医保（补充）合同 bus_no 占位（老日结单 220199 口径）。 */
    String MUNICIPAL_INSURANCE_SUPPLEMENT_CONTRACT_BUS_NO = "220199";

    /** 患者分项姓名缺失时的展示降级（BR-OP-DCL-032）。 */
    String PATIENT_NAME_FALLBACK = "未知患者";

    /** 患者分项分组键前缀：按就诊。 */
    String PATIENT_GROUP_KEY_ENCOUNTER_PREFIX = "E:";

    /** 患者分项分组键前缀：按患者（无就诊时回落）。 */
    String PATIENT_GROUP_KEY_PATIENT_PREFIX = "P:";

    /** 患者分项分组键前缀：单笔 payment（无就诊/患者时回落）。 */
    String PATIENT_GROUP_KEY_PAYMENT_PREFIX = "PAY:";

    /** 下半区扁平行 key：医保上传差额（患者分项剔除用）。 */
    String SUMMARY_ROW_KEY_CHS_RETAIL_UPLOAD_DIFF = "chsRetailUploadDiffAmount";

    /** 是否住院展示：是。 */
    String HOSPITALIZED_TEXT_YES = "是";

    /** 是否住院展示：否。 */
    String HOSPITALIZED_TEXT_NO = "否";

    /** 患者分项导出失败（EX-OP-DCL-108）。 */
    String EX_OP_DCL_108 = "EX-OP-DCL-108";

    /** 患者分项导出 xlsx MIME。 */
    String EXPORT_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 患者分项导出工作表名。 */
    String EXPORT_SHEET_NAME = "门诊日结患者分项";

    /** 患者分项导出文件名前缀。 */
    String EXPORT_FILE_PREFIX = "门诊日结患者分项_";
}
