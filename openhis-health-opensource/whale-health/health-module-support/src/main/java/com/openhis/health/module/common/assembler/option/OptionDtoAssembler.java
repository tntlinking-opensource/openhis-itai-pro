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

package com.openhis.health.module.common.assembler.option;

import com.openhis.whale.core.application.dto.OptionDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

/**
 * 应用层共享的 OptionDto 装配器。
 *
 * <p>用于把列表数据统一映射为前端下拉选项，避免在多个 AppService/MapperImpl 中重复手写 id/name/sort 组装逻辑。</p>
 */
public final class OptionDtoAssembler {

    /**
     * 禁止实例化：仅提供静态装配方法。
     */
    private OptionDtoAssembler() {
    }

    /**
     * 按默认排序值把列表映射为 OptionDto 下拉项。
     *
     * @param rows 原始列表
     * @param idExtractor 主键提取器
     * @param labelExtractor 展示名称提取器
     * @param defaultSort 默认排序值
     * @param <T> 行对象类型
     * @return 下拉项列表
     */
    public static <T> List<OptionDto> toOptions(
        List<T> rows,
        Function<T, Long> idExtractor,
        Function<T, String> labelExtractor,
        Long defaultSort) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<OptionDto> options = new ArrayList<>();
        // Loop：遍历原始列表，逐条装配为下拉选项
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            Long id = idExtractor.apply(row);
            String value = null;
            if (id != null) {
                value = String.valueOf(id);
            }
            options.add(new OptionDto(defaultSort, value, labelExtractor.apply(row)));
        }
        return options;
    }

    /**
     * 按行内排序值优先、默认排序值兜底的策略映射为 OptionDto 下拉项。
     *
     * @param rows 原始列表
     * @param idExtractor 主键提取器
     * @param labelExtractor 展示名称提取器
     * @param sortExtractor 行内排序值提取器
     * @param defaultSort 默认排序值
     * @param <T> 行对象类型
     * @return 下拉项列表
     */
    public static <T> List<OptionDto> toOptionsWithSortFallback(
        List<T> rows,
        Function<T, Long> idExtractor,
        Function<T, String> labelExtractor,
        Function<T, Long> sortExtractor,
        Long defaultSort) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<OptionDto> options = new ArrayList<>();
        // Loop：遍历原始列表，逐条装配为下拉选项（支持行内排序值优先）
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            Long id = idExtractor.apply(row);
            String value = null;
            if (id != null) {
                value = String.valueOf(id);
            }
            Long sort = sortExtractor.apply(row);
            if (sort == null) {
                sort = defaultSort;
            }
            options.add(new OptionDto(sort, value, labelExtractor.apply(row)));
        }
        return options;
    }

    /**
     * 按 {@link OptionDto#getSort()} 升序排序；sort 为空时排末位，再以 value 稳定次序。
     *
     * @param options 原始下拉项
     * @return 排序后的新列表；入参为 {@code null} 时返回空列表
     */
    public static List<OptionDto> sortBySortAsc(List<OptionDto> options) {
        if (options == null || options.isEmpty()) {
            if (options == null) {
                return Collections.emptyList();
            }
            return options;
        }
        List<OptionDto> sorted = new ArrayList<>(options);
        sorted.sort(
            Comparator.comparing(OptionDto::getSort, Comparator.nullsLast(Long::compareTo))
                .thenComparing(o -> StringUtils.defaultString(o.getValue())));
        return sorted;
    }
}
