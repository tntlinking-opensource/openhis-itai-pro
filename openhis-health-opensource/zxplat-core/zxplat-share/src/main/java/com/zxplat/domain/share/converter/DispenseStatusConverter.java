/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.converter;

import com.zxplat.domain.share.enums.DispenseStatus;
import jakarta.persistence.Converter;

/**
 * 发放状态枚举持久化转换器：{@link DispenseStatus} <-> String(code)。
 */
@Converter(autoApply = false)
/**
 * DispenseStatusConverter。
 */
public class DispenseStatusConverter extends AbstractEnumCodeConverter<DispenseStatus> {
    public DispenseStatusConverter() {
        super(DispenseStatus.class);
    }
}
