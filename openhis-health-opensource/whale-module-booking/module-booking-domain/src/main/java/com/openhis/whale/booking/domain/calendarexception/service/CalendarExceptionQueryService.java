/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.calendarexception.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import com.openhis.whale.booking.domain.calendarexception.queries.model.DayGeneratePlanModel;
import com.openhis.whale.booking.domain.calendarexception.repository.CalendarExceptionRepository;
import com.openhis.whale.booking.domain.share.CalendarExceptionTypeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T3 日历例外读侧查询。
 *
 * <p>只读；科室例外优先于全院例外；支持月历标记与模板生成日计划解析。</p>
 */
@Service
@RequiredArgsConstructor
public class CalendarExceptionQueryService {

    private final CalendarExceptionRepository calendarExceptionRepository;

    /**
     * 分页查询日历例外。
     *
     * @param organizationId 机构主键（含全院例外）
     * @param exceptionType 例外类型筛选
     * @param rangeStart 区间起
     * @param rangeEnd 区间止
     * @param keyword 原因关键字
     * @param pageable 分页
     * @return 例外分页
     */
    public Page<CalendarException> pageCalendarExceptions(
        Long organizationId,
        String exceptionType,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        String keyword,
        Pageable pageable) {
        return calendarExceptionRepository.findAll(
            this.buildPageSpec(organizationId, exceptionType, rangeStart, rangeEnd, keyword),
            pageable);
    }

    /**
     * 按主键加载有效日历例外。
     *
     * @param id 例外主键
     * @return 例外实体
     * @throws BusinessException id 为空或已删除时
     */
    public CalendarException loadById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "id 不能为空");
        }
        return calendarExceptionRepository.findById(id).filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "日历例外不存在"));
    }

    /**
     * 查询月内例外（含全院 + 科室）。
     *
     * @param organizationId 机构主键
     * @param month 目标月份
     * @return 与当月相交的例外列表
     */
    public List<CalendarException> listByMonth(Long organizationId, YearMonth month) {
        LocalDate rangeStart = month.atDay(1);
        LocalDate rangeEnd = month.atEndOfMonth();
        return calendarExceptionRepository.findAll(
            this.buildOverlappingRangeSpec(organizationId, rangeStart, rangeEnd),
            Sort.by(Sort.Order.asc("exceptionDate"), Sort.Order.asc("id")));
    }

    /**
     * 解析指定日期在从模板生成时的行为（BR-TPL-001 / TC-TPL-01/02）。
     *
     * @param organizationId 机构主键
     * @param date 目标日期
     * @return 生成计划（停诊/覆盖/减量/加号）
     */
    public DayGeneratePlanModel resolveDayGeneratePlan(Long organizationId, LocalDate date) {
        List<CalendarException> rows = this.listApplicableForDate(organizationId, date);
        DayGeneratePlanModel plan = new DayGeneratePlanModel();
        // 遍历当日适用例外，合并生成行为（科室优先于全院）
        for (CalendarException row : rows) {
            // 未勾选自动应用时仅作展示，不参与生成
            if (!Boolean.TRUE.equals(row.getAutoApplyOnGenerate())) {
                continue;
            }
            String type = row.getExceptionType();
            // 停诊日跳过常规模板生成
            if (CalendarExceptionTypeConstants.CLOSED.equals(type)) {
                plan.setSkipNormal(true);
            } else if (CalendarExceptionTypeConstants.OVERRIDE.equals(type)) {
                // 整日替换为指定日排班
                plan.setOverrideTemplateId(row.getOverrideScheduleTemplateId());
            } else if (CalendarExceptionTypeConstants.REDUCED.equals(type)) {
                // 减量出号：替代模板且标记减容
                plan.setOverrideTemplateId(row.getOverrideScheduleTemplateId());
                plan.setReducedCapacity(true);
            } else if (CalendarExceptionTypeConstants.EXTRA.equals(type)) {
                // 加号：常规模板之外额外生成
                plan.setGenerateExtra(true);
                plan.setExtraTemplateId(row.getOverrideScheduleTemplateId());
            }
        }
        return plan;
    }

    /**
     * 构建月历标记：date → 例外类型列表。
     *
     * @param organizationId 机构主键
     * @param month 目标月份
     * @return 日期到例外类型列表的映射
     */
    public Map<LocalDate, List<String>> buildMonthMarkerMap(Long organizationId, YearMonth month) {
        Map<LocalDate, List<String>> markers = new HashMap<>();
        for (CalendarException row : this.listByMonth(organizationId, month)) {
            LocalDate start = row.getExceptionDate();
            LocalDate end = row.getRangeEnd();
            // 单日例外无 rangeEnd 时视为起止同一天
            if (end == null) {
                end = row.getExceptionDate();
            }
            LocalDate cursor = start;
            // 展开跨日例外到月内每一天
            while (!cursor.isAfter(end)) {
                if (YearMonth.from(cursor).equals(month)) {
                    markers.computeIfAbsent(cursor, key -> new ArrayList<>()).add(row.getExceptionType());
                }
                cursor = cursor.plusDays(1);
            }
        }
        return markers;
    }

    private List<CalendarException> listApplicableForDate(Long organizationId, LocalDate date) {
        List<CalendarException> rows = new ArrayList<>(calendarExceptionRepository.findAll(
            this.buildApplicableForDateSpec(organizationId, date),
            Sort.by(Sort.Order.desc("id"))));
        // 科室例外优先于全院例外（原 @Query CASE WHEN organizationId IS NULL）
        rows.sort(Comparator
            .comparing((CalendarException row) -> row.getOrganizationId() == null)
            .thenComparing(CalendarException::getId, Comparator.reverseOrder()));
        return rows;
    }

    private Specification<CalendarException> buildApplicableForDateSpec(Long organizationId, LocalDate date) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.isTrue(root.get("autoApplyOnGenerate")));
            predicates.add(cb.lessThanOrEqualTo(root.get("exceptionDate"), date));
            jakarta.persistence.criteria.Expression<LocalDate> effectiveEnd = cb.coalesce(
                root.get("rangeEnd"), root.get("exceptionDate"));
            predicates.add(cb.greaterThanOrEqualTo(effectiveEnd, date));
            if (organizationId != null) {
                jakarta.persistence.criteria.Predicate orgMatch = cb.equal(root.get("organizationId"), organizationId);
                jakarta.persistence.criteria.Predicate global = cb.isNull(root.get("organizationId"));
                predicates.add(cb.or(orgMatch, global));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<CalendarException> buildOverlappingRangeSpec(
        Long organizationId,
        LocalDate rangeStart,
        LocalDate rangeEnd) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.lessThanOrEqualTo(root.get("exceptionDate"), rangeEnd));
            jakarta.persistence.criteria.Expression<LocalDate> effectiveEnd = cb.coalesce(
                root.get("rangeEnd"), root.get("exceptionDate"));
            predicates.add(cb.greaterThanOrEqualTo(effectiveEnd, rangeStart));
            if (organizationId != null) {
                jakarta.persistence.criteria.Predicate orgMatch = cb.equal(root.get("organizationId"), organizationId);
                jakarta.persistence.criteria.Predicate global = cb.isNull(root.get("organizationId"));
                predicates.add(cb.or(orgMatch, global));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private Specification<CalendarException> buildPageSpec(
        Long organizationId,
        String exceptionType,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        String keyword) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (organizationId != null) {
                jakarta.persistence.criteria.Predicate orgMatch = cb.equal(root.get("organizationId"), organizationId);
                jakarta.persistence.criteria.Predicate global = cb.isNull(root.get("organizationId"));
                predicates.add(cb.or(orgMatch, global));
            }
            if (StringUtils.isNotBlank(exceptionType)) {
                predicates.add(cb.equal(root.get("exceptionType"), exceptionType.trim()));
            }
            if (rangeStart != null) {
                jakarta.persistence.criteria.Expression<LocalDate> effectiveEnd = cb.coalesce(
                    root.get("rangeEnd"), root.get("exceptionDate"));
                predicates.add(cb.greaterThanOrEqualTo(effectiveEnd, rangeStart));
            }
            if (rangeEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("exceptionDate"), rangeEnd));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String like = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
                predicates.add(cb.like(cb.lower(root.get("reason")), like));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
