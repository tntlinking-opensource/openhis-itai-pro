/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.frequency.service;

import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.FrequencyCycleType;
import com.openhis.health.domain.custom.frequency.entity.Frequency;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyConfigInitQueryModel;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyExecutionTimePointQueryModel;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyPageItemQueryModel;
import com.openhis.health.domain.custom.frequency.repository.FrequencyRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 频次读侧查询服务。
 *
 * <p>职责：维护页初始化选项、分页浏览频次主数据、按主键强加载及根据频次编码计算计划执行时刻（纯计算）。</p>
 *
 * <p>边界：只读；{@link #computeExecutionTimePoints} 对停用频次或「必要时」类频次返回空或抛错，与临床开立语义一致。</p>
 * <p>聚合边界：仅本聚合 {@link FrequencyRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class FrequencyQueryService {

    private final FrequencyRepository repository;

    /**
     * 频次主数据按 rateCode 进程内缓存（护士站列表/待执计数同码高频复用，禁逐行打库）。
     */
    private final ConcurrentHashMap<String, Frequency> frequencyByRateCodeCache = new ConcurrentHashMap<>();

    /**
     * 初始化频次配置页。
     *
     * @return 初始化数据
     */
    public FrequencyConfigInitQueryModel initFrequencyConfigPage() {
        FrequencyConfigInitQueryModel model = new FrequencyConfigInitQueryModel();
        model.setCycleTypes(Arrays.asList("daily", "weekly", "interval", "as-needed", "fixed-count"));
        model.setPeriodUnits(Arrays.asList("min", "h", "d", "wk", "mo"));
        model.setApplicableScenes(Arrays.asList("outpatient", "inpatient", "nursing", "all"));
        model.setAllowCrossDayOptions(Arrays.asList("same-day", "cross-day", "by-shift"));
        return model;
    }

    /**
     * 分页查询频次。
     *
     * @param name 名称过滤
     * @param pageNo 页码
     * @param pageSize 页大小
     * @return 频次分页结果
     */
    public Page<FrequencyPageItemQueryModel> pageFrequencies(String name, Integer pageNo, Integer pageSize) {
        int normalizedPageNo;
        if (pageNo == null) {
            normalizedPageNo = 0;
        } else {
            normalizedPageNo = pageNo;
        }
        int normalizedPageSize;
        if (pageSize == null) {
            normalizedPageSize = GeneralSharedConstants.BATCH_LIMIT;
        } else {
            normalizedPageSize = pageSize;
        }
        Pageable pageable = PageRequest.of(normalizedPageNo, normalizedPageSize, Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        String normalizedName;
        if (name == null) {
            normalizedName = "";
        } else {
            normalizedName = name;
        }
        Page<Frequency> page = repository.findAllByNameContaining(normalizedName, pageable);
        return page.map(this::toPageModel);
    }

    /**
     * 查询全部启用频次，供应用层下拉与展示码表装配。
     *
     * @return 按 sortNo、id 升序的启用频次
     */
    public List<Frequency> listActiveFrequencies() {
        return repository.findByActiveFlagTrueOrderBySortNoAscIdAsc();
    }

    /**
     * 按主键读取频次实体。
     *
     * @param id 主键
     * @return 频次实体
     */
    public Frequency findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.FREQUENCY_NOT_FOUND, "频次不存在"));
    }

    /**
     * 计算执行时间点（纯计算，不写库；展开语义对齐 DM-CUS-008 §9.1，仅消费频次主数据字段）。
     *
     * @param rateCode 频次编码
     * @param startTime 开始时刻
     * @param endTime 结束时刻
     * @return 升序、去重后的执行时点列表
     */
    public List<FrequencyExecutionTimePointQueryModel> computeExecutionTimePoints(
        String rateCode,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        if (endTime == null || startTime == null || !endTime.isAfter(startTime)) {
            return new ArrayList<>();
        }
        Frequency frequency = this.requireActiveFrequencyByRateCode(rateCode);
        if (FrequencyCycleType.AS_NEEDED.getCode().equals(frequency.getCycleType())) {
            return new ArrayList<>();
        }
        List<LocalDateTime> rawPoints;
        if (this.isShortTimeInterval(frequency)) {
            rawPoints = this.expandByShortTimeInterval(frequency, startTime, endTime);
        } else if (this.isWeeklyCycle(frequency)) {
            rawPoints = this.expandByWeeklyCalendar(frequency, startTime, endTime);
        } else {
            rawPoints = this.expandByCalendarDays(frequency, startTime, endTime);
        }
        Set<LocalDateTime> deduped = new LinkedHashSet<>(rawPoints);
        List<FrequencyExecutionTimePointQueryModel> result = new ArrayList<>();
        for (LocalDateTime executionAt : deduped) {
            FrequencyExecutionTimePointQueryModel model = new FrequencyExecutionTimePointQueryModel();
            model.setRateCode(rateCode);
            model.setExecutionAt(executionAt);
            result.add(model);
        }
        result.sort(Comparator.comparing(FrequencyExecutionTimePointQueryModel::getExecutionAt));
        return result;
    }

    /**
     * 按 rateCode 加载启用中的频次主数据（进程内缓存，列表展槽禁 N+1 打库）。
     *
     * @param rateCode 频次编码
     * @return 启用中的频次实体
     */
    private Frequency requireActiveFrequencyByRateCode(String rateCode) {
        Frequency cached = frequencyByRateCodeCache.get(rateCode);
        if (cached != null) {
            if (!Boolean.TRUE.equals(cached.getActiveFlag())) {
                throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_STATUS, "频次已停用");
            }
            return cached;
        }
        Frequency frequency = repository.findByRateCode(rateCode)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.FREQUENCY_NOT_FOUND, "频次不存在"));
        if (!Boolean.TRUE.equals(frequency.getActiveFlag())) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_STATUS, "频次已停用");
        }
        frequencyByRateCodeCache.put(rateCode, frequency);
        return frequency;
    }

    /**
     * 按日历日 + {@code day_times} 展开（日周期、间隔周期的「天」维度、固定次数等共用）。
     */
    private List<LocalDateTime> expandByCalendarDays(Frequency frequency, LocalDateTime startTime, LocalDateTime endTime) {
        List<LocalTime> dayTimes = this.parseDayTimes(frequency.getDayTimes(), startTime);
        int dayStep = this.resolveCalendarDayStep(frequency);
        List<LocalDateTime> result = new ArrayList<>();
        LocalDate cursor = startTime.toLocalDate();
        while (!cursor.atStartOfDay().isAfter(endTime)) {
            for (LocalTime dayTime : dayTimes) {
                LocalDateTime candidate = LocalDateTime.of(cursor, dayTime);
                if (this.tryAppendExecutionPoint(result, frequency, candidate, startTime, endTime)) {
                    return result;
                }
            }
            cursor = cursor.plusDays(dayStep);
        }
        return result;
    }

    /**
     * 按 {@code execution_period} + {@code execution_period_unit}（min/h）累加展开。
     */
    private List<LocalDateTime> expandByShortTimeInterval(
        Frequency frequency, LocalDateTime startTime, LocalDateTime endTime) {
        Duration step = this.resolveShortTimeStep(frequency);
        List<LocalDateTime> result = new ArrayList<>();
        LocalDateTime cursor = startTime;
        while (!cursor.isAfter(endTime)) {
            if (this.tryAppendExecutionPoint(result, frequency, cursor, startTime, endTime)) {
                return result;
            }
            cursor = cursor.plus(step);
        }
        return result;
    }

    /**
     * 按周周期展开：步进 {@code execution_period} 周（缺省 1），日内时刻取自 {@code day_times}。
     */
    private List<LocalDateTime> expandByWeeklyCalendar(
        Frequency frequency, LocalDateTime startTime, LocalDateTime endTime) {
        List<LocalTime> dayTimes = this.parseDayTimes(frequency.getDayTimes(), startTime);
        int weekStep = this.resolveWeekStep(frequency);
        List<LocalDateTime> result = new ArrayList<>();
        LocalDate cursor = startTime.toLocalDate();
        while (!cursor.atStartOfDay().isAfter(endTime)) {
            for (LocalTime dayTime : dayTimes) {
                LocalDateTime candidate = LocalDateTime.of(cursor, dayTime);
                if (this.tryAppendExecutionPoint(result, frequency, candidate, startTime, endTime)) {
                    return result;
                }
            }
            cursor = cursor.plusWeeks(weekStep);
        }
        return result;
    }

    /**
     * 解析日历日步进：优先 {@code day_interval}，其次 {@code day_count}，再次 {@code execution_period}（单位 d）。
     */
    private int resolveCalendarDayStep(Frequency frequency) {
        Integer dayInterval = frequency.getDayInterval();
        if (dayInterval != null && dayInterval > 0) {
            return dayInterval;
        }
        Integer dayCount = frequency.getDayCount();
        if (dayCount != null && dayCount > 0) {
            return dayCount;
        }
        if (CustomConstants.PERIOD_UNIT_DAY.equals(this.normalizePeriodUnit(frequency.getExecutionPeriodUnit()))) {
            Integer executionPeriod = frequency.getExecutionPeriod();
            if (executionPeriod != null && executionPeriod > 0) {
                return executionPeriod;
            }
        }
        return 1;
    }

    /**
     * 解析周步进：{@code execution_period}（单位 wk），缺省 1 周。
     */
    private int resolveWeekStep(Frequency frequency) {
        if (CustomConstants.PERIOD_UNIT_WEEK.equals(this.normalizePeriodUnit(frequency.getExecutionPeriodUnit()))) {
            Integer executionPeriod = frequency.getExecutionPeriod();
            if (executionPeriod != null && executionPeriod > 0) {
                return executionPeriod;
            }
        }
        Integer dayCount = frequency.getDayCount();
        if (dayCount != null && dayCount > 0) {
            return dayCount;
        }
        return 1;
    }

    /**
     * 解析 min/h 级步进时长。
     */
    private Duration resolveShortTimeStep(Frequency frequency) {
        Integer period = frequency.getExecutionPeriod();
        if (period == null || period <= 0) {
            throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "短间隔频次缺少 execution_period");
        }
        String unit = this.normalizePeriodUnit(frequency.getExecutionPeriodUnit());
        if (CustomConstants.PERIOD_UNIT_MINUTE.equals(unit)) {
            return Duration.ofMinutes(period);
        }
        if (CustomConstants.PERIOD_UNIT_HOUR.equals(unit)) {
            return Duration.ofHours(period);
        }
        throw new BusinessException(ErrorCodeConstants.FREQUENCY_INVALID_ARGUMENT, "短间隔频次 execution_period_unit 须为 min/h");
    }

    /**
     * 判定频次是否为分钟/小时级短间隔（execution_period_unit 为 min/h）。
     *
     * @param frequency 频次定义
     * @return true 表示短间隔
     */
    private boolean isShortTimeInterval(Frequency frequency) {
        String unit = this.normalizePeriodUnit(frequency.getExecutionPeriodUnit());
        return CustomConstants.PERIOD_UNIT_MINUTE.equals(unit) || CustomConstants.PERIOD_UNIT_HOUR.equals(unit);
    }

    /**
     * 判定频次是否按周循环（cycle_type、week_cycle_flag 或 period_unit=week）。
     *
     * @param frequency 频次定义
     * @return true 表示周循环
     */
    private boolean isWeeklyCycle(Frequency frequency) {
        if (FrequencyCycleType.WEEKLY.getCode().equals(frequency.getCycleType())) {
            return true;
        }
        if (Boolean.TRUE.equals(frequency.getWeekCycleFlag())) {
            return true;
        }
        return CustomConstants.PERIOD_UNIT_WEEK.equals(this.normalizePeriodUnit(frequency.getExecutionPeriodUnit()));
    }

    /**
     * 追加候选时点；达 {@code total_execution_count} 时返回 true 表示应停止展开。
     */
    private boolean tryAppendExecutionPoint(
        List<LocalDateTime> result,
        Frequency frequency,
        LocalDateTime candidate,
        LocalDateTime startTime,
        LocalDateTime endTime) {
        if (candidate.isBefore(startTime) || candidate.isAfter(endTime)) {
            return false;
        }
        result.add(candidate);
        Integer totalExecutionCount = frequency.getTotalExecutionCount();
        if (totalExecutionCount != null && result.size() >= totalExecutionCount) {
            return true;
        }
        return false;
    }

    /**
     * 频次实体映射为分页行。
     */
    private FrequencyPageItemQueryModel toPageModel(Frequency frequency) {
        FrequencyPageItemQueryModel model = new FrequencyPageItemQueryModel();
        model.setId(frequency.getId());
        model.setRateCode(frequency.getRateCode());
        model.setName(frequency.getName());
        model.setCycleType(frequency.getCycleType());
        model.setDayCount(frequency.getDayCount());
        model.setDayInterval(frequency.getDayInterval());
        model.setDayTimes(frequency.getDayTimes());
        model.setWeekCycleFlag(frequency.getWeekCycleFlag());
        model.setTotalExecutionCount(frequency.getTotalExecutionCount());
        model.setExecutionPeriod(frequency.getExecutionPeriod());
        model.setExecutionPeriodUnit(frequency.getExecutionPeriodUnit());
        model.setApplicableScenes(frequency.getApplicableScenes());
        model.setAllowCrossDay(frequency.getAllowCrossDay());
        model.setActiveFlag(frequency.getActiveFlag());
        model.setSortNo(frequency.getSortNo());
        return model;
    }

    /**
     * 解析 {@code day_times}（逗号分隔 HH:mm）；未配置时回退为锚点 {@code startTime} 的日内时刻。
     */
    private List<LocalTime> parseDayTimes(String dayTimes, LocalDateTime anchor) {
        List<LocalTime> result = new ArrayList<>();
        if (StringUtils.isEmpty(dayTimes)) {
            result.add(anchor.toLocalTime());
            return result;
        }
        String[] segments = dayTimes.split(GeneralSharedConstants.COMMA);
        for (String segment : segments) {
            result.add(LocalTime.parse(segment.trim()));
        }
        return result;
    }

    /**
     * 执行周期单位归一化：null 回落空串，非 null 则 trim 并转小写。
     *
     * @param executionPeriodUnit 原始周期单位
     * @return 规范化后的单位串
     */
    private String normalizePeriodUnit(String executionPeriodUnit) {
        if (executionPeriodUnit == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return executionPeriodUnit.trim().toLowerCase();
    }

}
