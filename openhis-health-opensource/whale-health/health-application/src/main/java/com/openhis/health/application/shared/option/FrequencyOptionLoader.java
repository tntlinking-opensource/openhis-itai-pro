/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.option;

import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
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
 *
 * <p>进程内短窗缓存启用频次列表，避免发药 slip 与选品连点反复全表扫描。</p>
 */
@Component
@RequiredArgsConstructor
public class FrequencyOptionLoader {

    /** 频次读侧：启用态列表查询。 */
    private final FrequencyQueryService frequencyQueryService;

    /** 启用频次软缓存。 */
    private volatile CachedFrequencies cachedFrequencies;

    /**
     * 加载全部启用频次下拉（value=rateCode，label=name）。
     *
     * @return 按 sortNo 升序的选项列表
     */
    public List<OptionDto> loadActiveRateOptions() {
        List<Frequency> rows = this.loadActiveFrequenciesCached();
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
        List<Frequency> rows = this.loadActiveFrequenciesCached();
        Map<String, String> labelByCode = new HashMap<>();
        for (Frequency row : rows) {
            if (row == null || StringUtils.isEmpty(row.getRateCode())) {
                continue;
            }
            labelByCode.put(row.getRateCode(), row.getName());
        }
        return labelByCode;
    }

    /**
     * 带短窗缓存地加载启用频次实体列表。
     *
     * @return 启用频次快照；无数据时为空列表
     */
    private List<Frequency> loadActiveFrequenciesCached() {
        long nowMs = System.currentTimeMillis();
        CachedFrequencies cached = this.cachedFrequencies;
        // 短窗命中则复用，避免 IF-OPP-03 每次 listActiveFrequencies
        if (cached != null
            && nowMs - cached.loadedAtMs()
                < GeneralSharedConstants.PRODUCT_DEFINITION_SELECTION_TERMINOLOGY_CACHE_TTL_MS) {
            return cached.rows();
        }
        synchronized (this) {
            cached = this.cachedFrequencies;
            nowMs = System.currentTimeMillis();
            if (cached != null
                && nowMs - cached.loadedAtMs()
                    < GeneralSharedConstants.PRODUCT_DEFINITION_SELECTION_TERMINOLOGY_CACHE_TTL_MS) {
                return cached.rows();
            }
            List<Frequency> rows = frequencyQueryService.listActiveFrequencies();
            List<Frequency> snapshot;
            if (rows == null || rows.isEmpty()) {
                snapshot = List.of();
            } else {
                snapshot = List.copyOf(rows);
            }
            this.cachedFrequencies = new CachedFrequencies(snapshot, System.currentTimeMillis());
            return snapshot;
        }
    }

    /**
     * 启用频次软缓存条目。
     *
     * @param rows       不可变频次快照
     * @param loadedAtMs 加载时刻（epoch ms）
     */
    private record CachedFrequencies(List<Frequency> rows, long loadedAtMs) {
    }
}
