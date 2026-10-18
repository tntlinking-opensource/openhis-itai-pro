/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;

import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.util.StringUtils;

import java.util.Map;
import java.util.Objects;

/**
 * 住院长嘱「不可拆 · 一盒一点」资格判定（对齐 **BR-IP-EXE-035**）。
 *
 * <p>须同时满足：长期药嘱、目录<strong>非输液</strong>（{@code injectFlag≠true}）、目录包装单位与拆零单位不同、
 * 医嘱用法展示名属口服类（以「口服」开头，含口服缓释/控释等）。
 * 全局开关 {@code inpatientStandingOrderSplittable=0} 由调用方另行判定。</p>
 *
 * <p>药典 {@code inject_flag=true}（输液）开瓶即弃：即使用法误开「口服」，也<strong>不得</strong>按箱一盒一点。
 * 拆零下限列表切批/剩余续算（**BR-IP-EXE-036/038**）对输液默认排除，但用法为「皮下注射」时放行
 * （胰岛素等余量接着用；见 {@link #shouldSkipMinSplitReuseForOpenDiscardInfusion}）。</p>
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
     * @param product            药品目录展示（包装/拆零单位、injectFlag）
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
        if (isInfusionCatalog(product)) {
            // 输液目录开瓶即弃：禁按箱切点/汇总领药（用法误开口服也不放行）
            return false;
        }
        if (!isPackageUnitDistinctFromSplit(product)) {
            // 包装与拆零同码：无「整盒」语义，不切盒
            return false;
        }
        return isOralMethodClass(methodCode, methodLabelByCode);
    }

    /**
     * 目录是否输液药品（药典 {@code med_medication_definition.inject_flag=true}）。
     *
     * <p>产品口径：该标记为输液。一盒一点（035）一律排除；拆零下限续算见
     * {@link #shouldSkipMinSplitReuseForOpenDiscardInfusion}（皮下注射可放行）。</p>
     *
     * @param product 药品目录展示；可空
     * @return true=输液目录
     */
    public static boolean isInfusionCatalog(ProductDefinitionDisplayDto product) {
        if (product == null) {
            return false;
        }
        return Boolean.TRUE.equals(product.getInjectFlag());
    }

    /**
     * 用法是否属皮下注射类：术语展示名以
     * {@link InpatientNurseStationConstants#SUBCUTANEOUS_METHOD_DISPLAY_PREFIX} 开头。
     *
     * @param methodCode        用法 code
     * @param methodLabelByCode code → 展示名
     * @return true=皮下注射类
     */
    public static boolean isSubcutaneousMethodClass(
        String methodCode,
        Map<String, String> methodLabelByCode) {
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
        return label.trim().startsWith(
            InpatientNurseStationConstants.SUBCUTANEOUS_METHOD_DISPLAY_PREFIX);
    }

    /**
     * 拆零下限列表切批/剩余续算是否因「输液开瓶即弃」而跳过。
     *
     * <p>非输液目录不跳过（半片等仍走 036）；输液目录默认跳过；
     * 用法为皮下注射时不跳过（胰岛素等余量接着用）。</p>
     *
     * @param product           药品目录；可空
     * @param methodCode        医嘱用法 code
     * @param methodLabelByCode 用法 code → 展示名
     * @return true=应跳过 MIN_SPLIT 切批与剩余
     */
    public static boolean shouldSkipMinSplitReuseForOpenDiscardInfusion(
        ProductDefinitionDisplayDto product,
        String methodCode,
        Map<String, String> methodLabelByCode) {
        if (!isInfusionCatalog(product)) {
            // 非输液：半片等仍可走拆零下限续算
            return false;
        }
        if (isSubcutaneousMethodClass(methodCode, methodLabelByCode)) {
            // 输液目录但皮下注射：余量接着用（胰岛素），不跳过
            return false;
        }
        // 输液且非皮下（静滴/净推等）：开瓶即弃
        return true;
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
