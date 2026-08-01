/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import java.math.BigDecimal;

/**
 * 门诊药品处方分方（{@code prescription_no}）规则常量：药品性质码、处方类别码与装箱上限。
 *
 * <p>药品性质码对齐术语 {@code medicine_properties}（目录落库 {@code pharmacology_category_code}），
 * <b>禁止</b>与门诊拆分属性 {@code part_attribute_enum}/{@code OrderCalcRuleEnum} 混用。
 * 药品目录分类码复用 {@link DailySettlementSharedConstants#MEDICATION_CATEGORY_WESTERN_CODE} /
 * {@link DailySettlementSharedConstants#MEDICATION_CATEGORY_PATENT_CHINESE_CODE}，禁止在本接口重复定义。</p>
 */
public interface PrescriptionSplitConstants {

    /** 系统设置项：门诊处方金额上限（元）；不配时回落 {@link #DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN}。 */
    String SETTING_OUTPATIENT_PRESCRIPTION_AMOUNT_LIMIT_YUAN = "outpatientPrescriptionAmountLimitYuan";

    /** 未配置系统设置时的默认金额上限（元；与 V10 种子一致）。 */
    BigDecimal DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN = new BigDecimal("1000");

    /** 不按金额分方时可在系统设置中配置极大值（如 99999）。 */
    BigDecimal OUTPATIENT_AMOUNT_LIMIT_DISABLED_SENTINEL_YUAN = new BigDecimal("99999");

    /**
     * 西药或中成药各自同一处方内允许的最大药品申请行数
     * （每条 {@code MedicationRequest} 计 1，相同目录主键不合并；西药与中成药分轨后各自独立计数）。
     */
    int MAX_WESTERN_PATENT_MEDICATION_KINDS_PER_PRESCRIPTION = 5;

    /** 药品性质：普通药品（术语 medicine_properties code {@code 1} / {@code pharmacology_category_code}）。 */
    String MEDICINE_PROPERTY_NORMAL = "1";

    /** 药品性质：麻醉药品（术语 medicine_properties code {@code 2}）。 */
    String MEDICINE_PROPERTY_NARCOTIC = "2";

    /** 药品性质：毒性药品（术语 medicine_properties code {@code 3}）。 */
    String MEDICINE_PROPERTY_TOXIC = "3";

    /** 药品性质：一类精神药品（术语 medicine_properties code {@code 4}）。 */
    String MEDICINE_PROPERTY_PSYCH_ONE = "4";

    /** 药品性质：二类精神药品（术语 medicine_properties code {@code 5}）。 */
    String MEDICINE_PROPERTY_PSYCH_TWO = "5";

    /**
     * @deprecated 命名易与门诊拆分属性混淆；请改用 {@link #MEDICINE_PROPERTY_NORMAL}
     */
    @Deprecated
    String PART_ATTRIBUTE_NORMAL = MEDICINE_PROPERTY_NORMAL;

    /**
     * @deprecated 请改用 {@link #MEDICINE_PROPERTY_NARCOTIC}
     */
    @Deprecated
    String PART_ATTRIBUTE_NARCOTIC = MEDICINE_PROPERTY_NARCOTIC;

    /**
     * @deprecated 请改用 {@link #MEDICINE_PROPERTY_TOXIC}
     */
    @Deprecated
    String PART_ATTRIBUTE_TOXIC = MEDICINE_PROPERTY_TOXIC;

    /**
     * @deprecated 请改用 {@link #MEDICINE_PROPERTY_PSYCH_ONE}
     */
    @Deprecated
    String PART_ATTRIBUTE_PSYCH_ONE = MEDICINE_PROPERTY_PSYCH_ONE;

    /**
     * @deprecated 请改用 {@link #MEDICINE_PROPERTY_PSYCH_TWO}
     */
    @Deprecated
    String PART_ATTRIBUTE_PSYCH_TWO = MEDICINE_PROPERTY_PSYCH_TWO;

    /** 处方类别码（355）：门诊西药（不含中成药；中成药见 {@link #RX_TYPE_OUTPATIENT_PATENT}）。 */
    String RX_TYPE_OUTPATIENT_WESTERN = "1";

    /** 处方类别码（355）：门诊中药饮片。 */
    String RX_TYPE_OUTPATIENT_HERBAL = "2";

    /** 处方类别码（355）：急诊西药。 */
    String RX_TYPE_EMERGENCY_WESTERN = "3";

    /** 处方类别码（355）：急诊中药饮片。 */
    String RX_TYPE_EMERGENCY_HERBAL = "4";

    /** 处方类别码（355）：儿科西药。 */
    String RX_TYPE_PEDIATRIC_WESTERN = "5";

    /** 处方类别码（355）：儿科中药饮片。 */
    String RX_TYPE_PEDIATRIC_HERBAL = "6";

    /** 处方类别码（355）：麻、精一。 */
    String RX_TYPE_NARCOTIC_PSYCH_ONE = "7";

    /**
     * 处方类别码（355）：门诊中成药（术语已有码 {@code 10}；与西药 {@link #RX_TYPE_OUTPATIENT_WESTERN} 分轨）。
     */
    String RX_TYPE_OUTPATIENT_PATENT = "10";

    /** 处方类别码（355）：急诊中成药。 */
    String RX_TYPE_EMERGENCY_PATENT = "11";

    /** 处方类别码（355）：儿科中成药。 */
    String RX_TYPE_PEDIATRIC_PATENT = "12";

    /** 分方装箱：西药轨道（含外购等非中成药、非饮片普通药；精二可入本轨）。 */
    String SPLIT_TRACK_WESTERN = "WESTERN";

    /** 分方装箱：中成药轨道（精二可入本轨；与西药不得同方）。 */
    String SPLIT_TRACK_CHINESE_PATENT = "CHINESE_PATENT";

    /**
     * @deprecated 已拆为 {@link #SPLIT_TRACK_WESTERN} 与 {@link #SPLIT_TRACK_CHINESE_PATENT}；保留别名仅防旧引用编译，勿再写入逻辑。
     */
    @Deprecated
    String SPLIT_TRACK_WESTERN_PATENT = SPLIT_TRACK_WESTERN;

    /** 分方装箱：中药饮片轨道。 */
    String SPLIT_TRACK_HERBAL = "HERBAL";

    /** 分方装箱：毒性药品轨道。 */
    String SPLIT_TRACK_TOXIC = "TOXIC";

    /** 分方装箱：麻/精一药品轨道。 */
    String SPLIT_TRACK_NARCOTIC_PSYCH_ONE = "NARCOTIC_PSYCH_ONE";

    /** 分方分区键分隔符（业务键不含此字符）。 */
    String PARTITION_KEY_SEPARATOR = "\u0001";
}
