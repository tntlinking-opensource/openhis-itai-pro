/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * 住院长嘱「不可拆 · 一盒一点」资格判定（对齐 **BR-IP-EXE-035**）。
 *
 * <p>须同时满足：长期药嘱、目录包装单位与拆零单位不同、医嘱用法展示名属口服类（以「口服」开头，
 * 含口服缓释/控释等）。全局开关 {@code inpatientStandingOrderSplittable=0} 由调用方另行判定。</p>
 */
public final class StandingOrderPackageBatchEligibilitySupport {

    private StandingOrderPackageBatchEligibilitySupport() {
    }

    /**
     * 是否满足一盒一点执行资格（不含全局可拆开关）。
     *
     * @param therapyEnum        治疗类型 code
     * @param methodCode         医嘱用法 code（method_code）
     * @param methodLabelByCode  用法 code → 展示名；可空
     * @param product            药品目录展示（包装/拆零单位）
     * @return true=可走包装批次一盒一点
     */
    public static boolean isEligibleWithoutSplittableSwitch(
        String therapyEnum,
        String methodCode,
        Map<String, String> methodLabelByCode,
        ProductDefinitionDisplayDto product) {
        if (!isLongTermTherapy(therapyEnum)) {
            // 非长期：不可拆切盒不适用
            return false;
        }
        if (!isPackageUnitDistinctFromSplit(product)) {
            // 包装与拆零同码：无「整盒」语义，不切盒
            return false;
        }
        return isOralMethodClass(methodCode, methodLabelByCode);
    }

    /**
     * 包装单位与拆零单位是否不同（均非空且 trim 后不等）。
     *
     * @param product 目录
     * @return true=可按包装批次领药
     */
    public static boolean isPackageUnitDistinctFromSplit(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return false;
        }
        if (StringUtils.isEmpty(product.getPackageUnitCode())
            || StringUtils.isEmpty(product.getSplitUnitCode())) {
            return false;
        }
        return !Objects.equals(
            product.getPackageUnitCode().trim(),
            product.getSplitUnitCode().trim());
    }

    /**
     * 用法是否属口服类：术语展示名以 {@link InpatientNurseStationConstants#ORAL_METHOD_DISPLAY_PREFIX} 开头。
     *
     * @param methodCode        用法 code
     * @param methodLabelByCode code → 展示名
     * @return true=口服类
     */
    public static boolean isOralMethodClass(String methodCode, Map<String, String> methodLabelByCode) {
        if (StringUtils.isEmpty(methodCode)) {
            return false;
        }
        if (methodLabelByCode == null || methodLabelByCode.isEmpty()) {
            return false;
        }
        String label = methodLabelByCode.get(methodCode.trim());
        if (StringUtils.isEmpty(label)) {
            return false;
        }
        return label.trim().startsWith(InpatientNurseStationConstants.ORAL_METHOD_DISPLAY_PREFIX);
    }

    /**
     * 是否长期治疗类型。
     *
     * @param therapyEnum therapy code
     * @return true=长期
     */
    private static boolean isLongTermTherapy(String therapyEnum) {
        if (StringUtils.isEmpty(therapyEnum)) {
            return false;
        }
        return TherapyType.LONG_TERM.getCode().equals(therapyEnum.trim());
    }
}
