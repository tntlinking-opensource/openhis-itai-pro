/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.product.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.Map;

/**
 * 产品定义展示：术语码表预热映射（一次请求加载，多行复用，B.10.8.1）。
 */
@Getter
@Setter
public class ProductDefinitionSelectionTerminologyMaps {

    /** 计量单位 {@code UNIT_CODE}。 */
    private Map<String, String> unitLabelByCode = Collections.emptyMap();

    /** 剂量单位 {@code DOSE_UNIT}（未命中时可回落 {@link #unitLabelByCode}）。 */
    private Map<String, String> doseUnitLabelByCode = Collections.emptyMap();

    /** 药品物资分类 {@code MEDICATION_CATEGORY_CODE}。 */
    private Map<String, String> medicationCategoryLabelByCode = Collections.emptyMap();

    /** 诊疗类目 {@code ACTIVITY_CATEGORY_CODE}。 */
    private Map<String, String> activityCategoryLabelByCode = Collections.emptyMap();

    /** 耗材物资分类 {@code DEVICE_CATEGORY_CODE}。 */
    private Map<String, String> deviceCategoryLabelByCode = Collections.emptyMap();

    /** 给药途径 {@code METHOD_CODE}。 */
    private Map<String, String> methodLabelByCode = Collections.emptyMap();

    /** 用药频次（cus_frequency.rate_code）。 */
    private Map<String, String> rateLabelByCode = Collections.emptyMap();

    /** 剂型 {@code DOSE_FORM_CODE}。 */
    private Map<String, String> doseFormLabelByCode = Collections.emptyMap();

    /** 中药特殊煎法 {@code DOSAGE_INSTRUCTION}。 */
    private Map<String, String> dosageInstructionLabelByCode = Collections.emptyMap();

    /** 限制使用 {@code RESTRICTED_ENUM}。 */
    private Map<String, String> restrictedEnumLabelByCode = Collections.emptyMap();

    /** 部位属性 {@code PART_ATTRIBUTE_ENUM}。 */
    private Map<String, String> partAttributeLabelByCode = Collections.emptyMap();

    /** 临时医嘱拆分属性 {@code THO_PART_ATTRIBUTE_ENUM}。 */
    private Map<String, String> thoPartAttributeLabelByCode = Collections.emptyMap();

    /** 医保等级 {@code CHRGITM_LV}（键为等级码字符串）。 */
    private Map<String, String> chrgitmLvLabelByCode = Collections.emptyMap();
}
