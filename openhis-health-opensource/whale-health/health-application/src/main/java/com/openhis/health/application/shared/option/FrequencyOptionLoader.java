/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.option;

import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
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
            if (row == null || StringUtils.isEmpty(row.getRateCode())) {
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
            if (row == null || StringUtils.isEmpty(row.getRateCode())) {
                continue;
            }
            labelByCode.put(row.getRateCode(), row.getName());
        }
        return labelByCode;
    }
}
