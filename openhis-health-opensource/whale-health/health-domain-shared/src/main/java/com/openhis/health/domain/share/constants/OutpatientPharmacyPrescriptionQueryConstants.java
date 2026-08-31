/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * 门诊处方查询（AS-OP-PHA-005）读侧常量：含输液筛、费用性质分类文案与领域语义码锚点。
 */
public interface OutpatientPharmacyPrescriptionQueryConstants {

    /** 含输液筛：不限。 */
    String INFUSION_FILTER_ALL = "ALL";

    /** 含输液筛：仅含注射目录药品的处方（方级，整方展开）。 */
    String INFUSION_FILTER_YES = "YES";

    /** 含输液筛：不含注射目录药品的处方。 */
    String INFUSION_FILTER_NO = "NO";

    /**
     * 点评方号筛模式：命中处方号 {@code IN}（已点评 / 按是否合理收窄）。
     */
    String REVIEW_PRESCRIPTION_FILTER_INCLUDE = "INCLUDE";

    /**
     * 点评方号筛模式：命中处方号 {@code NOT IN}（待点评：排除有效点评方）。
     */
    String REVIEW_PRESCRIPTION_FILTER_EXCLUDE = "EXCLUDE";

    /** 含输液筛选项文案：全部。 */
    String INFUSION_FILTER_ALL_LABEL = "全部";

    /** 含输液筛选项文案：是。 */
    String INFUSION_FILTER_YES_LABEL = "是";

    /** 含输液筛选项文案：否。 */
    String INFUSION_FILTER_NO_LABEL = "否";

    /** 费用性质分类文案：自费。 */
    String CONTRACT_CATEGORY_SELF_CHARGE_LABEL = "自费";

    /** 费用性质分类文案：公费。 */
    String CONTRACT_CATEGORY_PUBLIC_CHARGE_LABEL = "公费";

    /** 费用性质分类文案：省医保。 */
    String CONTRACT_CATEGORY_PROVINCIAL_INSURANCE_LABEL = "省医保";

    /** 费用性质分类文案：市医保。 */
    String CONTRACT_CATEGORY_MUNICIPAL_INSURANCE_LABEL = "市医保";

    /** 费用性质分类文案：工伤保险（category_enum=WORKER_INSURANCE；院点合同 H*-GS）。 */
    String CONTRACT_CATEGORY_WORKER_INSURANCE_LABEL = "工伤保险";

    /** 页面初始化失败。 */
    String EX_OPQ_101 = "EX-OPQ-101";

    /** 开方时间窗非法。 */
    String EX_OPQ_102 = "EX-OPQ-102";

    /** 查询失败。 */
    String EX_OPQ_103 = "EX-OPQ-103";
}
