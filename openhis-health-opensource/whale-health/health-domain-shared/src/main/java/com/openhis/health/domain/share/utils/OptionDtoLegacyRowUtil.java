/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
