/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.option;

import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.FrequencyCycleType;
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
     * 构建 rateCode → day_times 非空段数（任意周期；供分次用量不一致 UI/Gate）。
     *
     * @return 码 → 段数；无 day_times 的码不出现
     */
    public Map<String, Integer> buildDayTimesSegmentCountMap() {
        List<Frequency> rows = this.loadActiveFrequenciesCached();
        Map<String, Integer> countByCode = new HashMap<>();
        for (Frequency row : rows) {
            if (row == null || StringUtils.isEmpty(row.getRateCode())) {
                continue;
            }
            Integer count = this.countDayTimesNonEmptySegments(row.getDayTimes());
            if (count == null || count <= 0) {
                continue;
            }
            countByCode.put(row.getRateCode().trim(), count);
        }
        return countByCode;
    }

    /**
     * 解析 rateCode 对应 {@code day_times} 非空段数（任意周期；供分次用量 schedule 段数 Gate）。
     *
     * <p>与「用量不一致」UI 展开框数同源：有几个非空日内时刻就几个剂量框。
     * 与 {@link #buildTimesPerDayMap()} 不同：本方法不限制日周期/日间隔。</p>
     *
     * @param rateCode 频次 code；空则 null
     * @return 非空段数；无启用频次或 day_times 空则 null
     */
    public Integer resolveDayTimesSegmentCount(String rateCode) {
        if (StringUtils.isEmpty(rateCode)) {
            return null;
        }
        String trimmed = rateCode.trim();
        Map<String, Integer> map = this.buildDayTimesSegmentCountMap();
        return map.get(trimmed);
    }

    /**
     * 统计 {@code day_times} 逗号分隔非空段数。
     *
     * @param dayTimes 日内时刻串；可空
     * @return 段数；无法解析时 null
     */
    private Integer countDayTimesNonEmptySegments(String dayTimes) {
        String trimmed = StringUtils.trimToNull(dayTimes);
        if (trimmed == null) {
            return null;
        }
        int count = 0;
        String[] segments = trimmed.split(GeneralSharedConstants.COMMA);
        for (String segment : segments) {
            if (StringUtils.isNotEmpty(StringUtils.trimToEmpty(segment))) {
                count++;
            }
        }
        if (count <= 0) {
            return null;
        }
        return count;
    }

    /**
     * 构建 rateCode → 每日执行次数（仅日周期且日步进为 1；由 {@code day_times} 段数解析）。
     *
     * <p>供长期药「每日三片」日剂量展示；周周期 / 隔日 / 按需 / 固定次数不入图。</p>
     *
     * @return 码 → 每日次数；无法解析的码不出现
     */
    public Map<String, Integer> buildTimesPerDayMap() {
        List<Frequency> rows = this.loadActiveFrequenciesCached();
        Map<String, Integer> timesByCode = new HashMap<>();
        for (Frequency row : rows) {
            if (row == null || StringUtils.isEmpty(row.getRateCode())) {
                continue;
            }
            Integer times = this.resolveDailyTimesPerDay(row);
            if (times == null || times <= 0) {
                continue;
            }
            timesByCode.put(row.getRateCode(), times);
        }
        return timesByCode;
    }

    /**
     * 解析「每日」次数：仅 {@code daily} 且日间隔为 1，取 {@code day_times} 非空段数。
     *
     * @param frequency 频次实体
     * @return 每日次数；不适用时 null
     */
    private Integer resolveDailyTimesPerDay(Frequency frequency) {
        if (frequency == null) {
            return null;
        }
        // 仅日周期可标「每日」
        if (!FrequencyCycleType.DAILY.getCode().equals(StringUtils.trimToEmpty(frequency.getCycleType()))) {
            return null;
        }
        // 隔 N 天：不是每日
        Integer dayInterval = frequency.getDayInterval();
        if (dayInterval != null && dayInterval > 1) {
            return null;
        }
        Integer dayCount = frequency.getDayCount();
        if (dayCount != null && dayCount > 1) {
            return null;
        }
        String dayTimes = StringUtils.trimToNull(frequency.getDayTimes());
        if (dayTimes == null) {
            return null;
        }
        int count = 0;
        String[] segments = dayTimes.split(GeneralSharedConstants.COMMA);
        for (String segment : segments) {
            if (StringUtils.isNotEmpty(StringUtils.trimToEmpty(segment))) {
                count++;
            }
        }
        if (count <= 0) {
            return null;
        }
        return count;
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
