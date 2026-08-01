/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门/住院日结展示共用常量（fin_type 特列、合同 bus_no 占位、优惠占位）。
 */
public interface DailySettlementSharedConstants {

    /**
     * {@code fin_payment_rec_static.method_code}：按字典 {@code fin_type_code} 统计（DM-FIN-002 §4.10 编码值 1）。
     */
    String PAYMENT_REC_STATIC_METHOD_FIN_TYPE_CODE = "1";

    /** 诊察费财务类别码（号源定价 type_code 与日结归并目标，对齐 ter_concept 236/1012）。 */
    String CONSULTATION_FEE_FIN_TYPE_CODE = "1012";

    /** 号源/挂号收费在日结汇总中的 fin_type 归并码（与 {@link #CONSULTATION_FEE_FIN_TYPE_CODE} 同码）。 */
    String REGISTRATION_FEE_FIN_TYPE_CODE = "1012";

    /** 已废止挂号费 fin_type_code（ter_concept 236/1011；历史快照须归并至 {@link #CONSULTATION_FEE_FIN_TYPE_CODE}，日结上半区不展示）。 */
    String DEPRECATED_REGISTRATION_FEE_FIN_TYPE_CODE = "1011";

    /** 西药 fin_type_code（ter_concept 236/1001）。 */
    String WESTERN_MEDICINE_FIN_TYPE_CODE = "1001";

    /** 外购药品 fin_type_code（ter_concept 236/1002）。 */
    String PURCHASED_MEDICINE_FIN_TYPE_CODE = "1002";

    /** 中草药 fin_type_code（ter_concept 236/1003）。 */
    String CHINESE_HERBAL_MEDICINE_FIN_TYPE_CODE = "1003";

    /** 中成药 fin_type_code（ter_concept 236/2010）。 */
    String CHINESE_PATENT_MEDICINE_FIN_TYPE_CODE = "2010";

    /** 药品目录分类 medication_category_code：中成药。 */
    String MEDICATION_CATEGORY_PATENT_CHINESE_CODE = "1";

    /** 药品目录分类 medication_category_code：西药。 */
    String MEDICATION_CATEGORY_WESTERN_CODE = "2";

    /** 药品目录分类 medication_category_code：外购药品。 */
    String MEDICATION_CATEGORY_PURCHASED_CODE = "3";

    /** 药品目录分类 medication_category_code：中草药。 */
    String MEDICATION_CATEGORY_HERBAL_CODE = "4";

    /** 体检财务类别码（老日结单 peisCnt 锚点）。 */
    String PHYSICAL_EXAM_FIN_TYPE_CODE = "9999";

    /** 优惠金额占位（本期固定 0.00）。 */
    String DISCOUNT_AMOUNT_ZERO = "0.00";

    /** 自费合同 bus_no 占位（老日结单 0000 口径）。 */
    String SELF_PAY_CONTRACT_BUS_NO = "0000";

    /** 省医保合同 bus_no 占位（老日结单 229900 口径）。 */
    String PROVINCIAL_INSURANCE_CONTRACT_BUS_NO = "229900";

    /** 市医保合同 bus_no 占位（老日结单 220100 口径）。 */
    String MUNICIPAL_INSURANCE_CONTRACT_BUS_NO = "220100";

    /** 市医保（补充）合同 bus_no 占位（老日结单 220199 口径）。 */
    String MUNICIPAL_INSURANCE_SUPPLEMENT_CONTRACT_BUS_NO = "220199";
}
