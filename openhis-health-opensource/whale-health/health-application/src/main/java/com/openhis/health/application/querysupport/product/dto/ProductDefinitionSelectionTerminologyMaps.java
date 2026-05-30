/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.querysupport.product.dto;

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

    /** 用药频次 {@code RATE_CODE}。 */
    private Map<String, String> rateLabelByCode = Collections.emptyMap();

    /** 剂型 {@code DOSE_FORM_CODE}。 */
    private Map<String, String> doseFormLabelByCode = Collections.emptyMap();

    /** 限制使用 {@code RESTRICTED_ENUM}。 */
    private Map<String, String> restrictedEnumLabelByCode = Collections.emptyMap();

    /** 部位属性 {@code PART_ATTRIBUTE_ENUM}。 */
    private Map<String, String> partAttributeLabelByCode = Collections.emptyMap();

    /** 临时医嘱拆分属性 {@code THO_PART_ATTRIBUTE_ENUM}。 */
    private Map<String, String> thoPartAttributeLabelByCode = Collections.emptyMap();

    /** 医保等级 {@code CHRGITM_LV}（键为等级码字符串）。 */
    private Map<String, String> chrgitmLvLabelByCode = Collections.emptyMap();
}
