/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.diagnosis.vitalsign.enums;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 生命体征单据类型（DM-DIA-002 §4.1 {@code type_enum}）。
 *
 * <p>持久化为 {@code String}，存枚举 {@link #code}，不进入术语字典。</p>
 */
public enum VitalSignTypeEnum {

    /** 体温单 / 三测单。 */
    TEMPERATURE_SHEET("TEMPERATURE_SHEET", "体温单"),

    /** 护理记录单。 */
    NURSING_RECORD("NURSING_RECORD", "护理记录");

    private final String code;
    private final String display;

    VitalSignTypeEnum(String code, String display) {
        this.code = code;
        this.display = display;
    }

    /**
     * 枚举持久化 code。
     *
     * @return code 字符串
     */
    public String getCode() {
        return code;
    }

    /**
     * 展示名称。
     *
     * @return 中文名称
     */
    public String getDisplay() {
        return display;
    }

    /**
     * 按 code 解析枚举；非法 code 抛业务异常。
     *
     * @param code 持久化 code
     * @return 枚举项
     */
    public static VitalSignTypeEnum fromCode(String code) {
        if (StringUtils.isEmpty(code)) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "单据类型不能为空");
        }
        for (VitalSignTypeEnum item : values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }
        throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "非法单据类型: " + code);
    }
}
