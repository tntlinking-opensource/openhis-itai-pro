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

package com.openhis.health.domain.share.utils;

import com.openhis.whale.core.application.dto.OptionDto;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 旧版前端约定的 {@code [{label, value}]} 行结构与 {@link OptionDto} 互转。
 *
 */
public final class OptionDtoLegacyRowUtil {

    private OptionDtoLegacyRowUtil() {
    }

    /**
     * 将术语下拉转为 label/value 行列表。
     *
     * @param options 编码体系选项
     * @return 每行含键 {@code label}、{@code value}；空输入返回空列表
     */
    public static List<Map<String, Object>> toLabelValueRows(List<OptionDto> options) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        return options.stream()
            .filter(Objects::nonNull)
            .map(o -> Map.<String, Object>of("label", o.getLabel(), "value", o.getValue()))
            .collect(Collectors.toList());
    }
}
