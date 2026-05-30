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

package com.openhis.health.domain.share.converter;

import com.openhis.health.domain.share.enums.EnumInterface;
import com.openhis.health.domain.share.utils.EnumUtil;
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
