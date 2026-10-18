/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.domain.template;

import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.plugin.emr.domain.enums.TemplateScopeLevel;
import org.springframework.util.StringUtils;

/**
 * 模板使用范围与就诊场景字段校验（领域不变式）。
 */
public final class TemplateScopeValidator {

    private TemplateScopeValidator() {
    }

    /**
     * 保存前校验 scope / 科室 / 所有者 / 就诊类别组合。
     *
     * @param scopeLevelCode       范围编码
     * @param organizationId       科室 id（DEPARTMENT 必填）
     * @param ownerPractitionerId  所有者（PERSONAL 必填）
     * @param encounterClassCode   就诊类别（必填）
     */
    public static void validateForSave(
            String scopeLevelCode,
            Long organizationId,
            Long ownerPractitionerId,
            String encounterClassCode) {
        if (!StringUtils.hasText(encounterClassCode)) {
            throw new ValidationException("请选择适用就诊类别（门诊/住院等）");
        }
        TemplateScopeLevel scope = TemplateScopeLevel.fromCode(scopeLevelCode);
        if (scope == null) {
            throw new ValidationException("请选择使用范围（个人/科室/全院）");
        }
        switch (scope) {
            case HOSPITAL -> {
                if (organizationId != null) {
                    throw new ValidationException("全院模板不能指定科室");
                }
                if (ownerPractitionerId != null) {
                    throw new ValidationException("全院模板不能指定个人所有者");
                }
            }
            case DEPARTMENT -> {
                if (organizationId == null) {
                    throw new ValidationException("科室模板必须选择适用科室");
                }
                if (ownerPractitionerId != null) {
                    throw new ValidationException("科室模板不能指定个人所有者");
                }
            }
            case PERSONAL -> {
                if (ownerPractitionerId == null) {
                    throw new ValidationException("个人模板必须指定所有者");
                }
            }
            default -> throw new ValidationException("无效的使用范围");
        }
    }

    /**
     * 归一化范围编码；无法识别时返回 null。
     *
     * @param raw 原始值
     * @return PERSONAL / DEPARTMENT / HOSPITAL 或 null
     */
    public static String normalizeScopeLevel(String raw) {
        TemplateScopeLevel level = TemplateScopeLevel.fromCode(raw);
        if (level != null) {
            return level.getCode();
        }
        return null;
    }

    /**
     * 归一化就诊类别编码为大写；空则 null。
     *
     * @param raw 原始值
     * @return 大写编码或 null
     */
    public static String normalizeEncounterClassCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase();
    }
}
