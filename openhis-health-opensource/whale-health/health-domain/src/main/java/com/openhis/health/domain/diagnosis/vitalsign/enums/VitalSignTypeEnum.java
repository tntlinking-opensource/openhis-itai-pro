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

package com.openhis.health.domain.diagnosis.vitalsign.enums;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

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
        if (StringUtils.isBlank(code)) {
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
