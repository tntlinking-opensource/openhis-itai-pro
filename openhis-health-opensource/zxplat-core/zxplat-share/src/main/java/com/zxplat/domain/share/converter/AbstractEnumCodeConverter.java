/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.converter;

import com.zxplat.domain.share.enums.EnumInterface;
import com.zxplat.domain.share.utils.EnumUtil;
import jakarta.persistence.AttributeConverter;

/**
 * 通用枚举持久化转换器：枚举 <-> String(code)。
 *
 * <p>仅用于实现 {@link EnumInterface#getCode()} 的枚举。</p>
 */
public abstract class AbstractEnumCodeConverter<E extends Enum<E> & EnumInterface>
    implements AttributeConverter<E, String> {

    private final Class<E> enumClass;

    protected AbstractEnumCodeConverter(Class<E> enumClass) {
        this.enumClass = enumClass;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return EnumUtil.toCode(attribute);
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return EnumUtil.fromCode(enumClass, dbData);
    }
}
