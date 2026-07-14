/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.support;

import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.emr.domain.enums.TemplateScopeLevel;
import org.springframework.util.StringUtils;

/**
 * 模板使用范围与就诊场景字段校验。
 */
public final class TemplateScopeValidator {

    private TemplateScopeValidator() {
    }

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

    public static String normalizeScopeLevel(String raw) {
        TemplateScopeLevel level = TemplateScopeLevel.fromCode(raw);
        if (level != null) {
            return level.getCode();
        }
        return null;
    }

    public static String normalizeEncounterClassCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return raw.trim().toUpperCase();
    }
}
