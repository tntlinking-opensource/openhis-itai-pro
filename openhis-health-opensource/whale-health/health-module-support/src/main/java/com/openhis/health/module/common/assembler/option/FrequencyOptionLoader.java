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

import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 频次主数据（{@code cus_frequency}）下拉与展示映射入口。
 *
 * <p>替代原术语 {@code rate_code} 字典；临床开立与列表展示均读启用态频次表。</p>
 */
@Component
@RequiredArgsConstructor
public class FrequencyOptionLoader {

    /** 频次读侧：启用态列表查询。 */
    private final FrequencyQueryService frequencyQueryService;

    /**
     * 加载全部启用频次下拉（value=rateCode，label=name）。
     *
     * @return 按 sortNo 升序的选项列表
     */
    public List<OptionDto> loadActiveRateOptions() {
        List<Frequency> rows = frequencyQueryService.listActiveFrequencies();
        List<OptionDto> result = new ArrayList<>();
        for (Frequency row : rows) {
            if (row == null || StringUtils.isBlank(row.getRateCode())) {
                continue;
            }
            Long sort = null;
            if (row.getSortNo() != null) {
                sort = row.getSortNo().longValue();
            }
            OptionDto option = new OptionDto(sort, row.getRateCode(), row.getName());
            result.add(option);
        }
        return OptionDtoAssembler.sortBySortAsc(result);
    }

    /**
     * 构建 rateCode → 频次名称映射，供列表/发药/护士站批量展示。
     *
     * @return 码 → 展示名
     */
    public Map<String, String> buildRateLabelMap() {
        List<Frequency> rows = frequencyQueryService.listActiveFrequencies();
        Map<String, String> labelByCode = new HashMap<>();
        for (Frequency row : rows) {
            if (row == null || StringUtils.isBlank(row.getRateCode())) {
                continue;
            }
            labelByCode.put(row.getRateCode(), row.getName());
        }
        return labelByCode;
    }
}
