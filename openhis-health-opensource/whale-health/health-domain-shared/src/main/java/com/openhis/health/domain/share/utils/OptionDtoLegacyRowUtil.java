/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.whale.core.application.dto.OptionDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    /**
     * 将下拉转为可拼音/五笔过滤的行（耗材等仍吃 Map 行的 init 契约）。
     *
     * <p>必含 {@code label}/{@code value}；{@code pyStr}/{@code wbStr}/{@code busNo} 仅非空时写入，
     * 避免 {@link Map#of} 拒 null。</p>
     *
     * @param options 选项（可含拼音五笔）
     * @return 行列表；空输入返回空列表
     */
    public static List<Map<String, Object>> toSearchableLabelValueRows(List<OptionDto> options) {
        if (options == null || options.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (OptionDto option : options) {
            if (option == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", option.getLabel());
            row.put("value", option.getValue());
            if (option.getPyStr() != null && !option.getPyStr().isEmpty()) {
                row.put("pyStr", option.getPyStr());
            }
            if (option.getWbStr() != null && !option.getWbStr().isEmpty()) {
                row.put("wbStr", option.getWbStr());
            }
            if (option.getBusNo() != null && !option.getBusNo().isEmpty()) {
                row.put("busNo", option.getBusNo());
            }
            rows.add(row);
        }
        return rows;
    }
}
