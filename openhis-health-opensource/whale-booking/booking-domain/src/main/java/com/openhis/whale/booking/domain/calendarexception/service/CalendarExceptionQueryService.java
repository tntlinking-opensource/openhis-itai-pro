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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T3 日历例外读侧查询。
 */
@Service
@RequiredArgsConstructor
public class CalendarExceptionQueryService {

    private final CalendarExceptionRepository calendarExceptionRepository;

    /**
     * 分页查询日历例外。
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
     * 按主键加载。
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
     */
    public List<CalendarException> listByMonth(Long organizationId, YearMonth month) {
        LocalDate rangeStart = month.atDay(1);
        LocalDate rangeEnd = month.atEndOfMonth();
        return calendarExceptionRepository.findOverlappingRange(organizationId, rangeStart, rangeEnd);
    }

    /**
     * 解析指定日期在从模板生成时的行为（BR-TPL-001 / TC-TPL-01/02）。
     */
    public DayGeneratePlanModel resolveDayGeneratePlan(Long organizationId, LocalDate date) {
        List<CalendarException> rows = calendarExceptionRepository.findApplicableForDate(organizationId, date);
        DayGeneratePlanModel plan = new DayGeneratePlanModel();
        for (CalendarException row : rows) {
            if (!Boolean.TRUE.equals(row.getAutoApplyOnGenerate())) {
                continue;
            }
            String type = row.getExceptionType();
            if (CalendarExceptionTypeConstants.CLOSED.equals(type)) {
                plan.setSkipNormal(true);
            } else if (CalendarExceptionTypeConstants.OVERRIDE.equals(type)) {
                plan.setOverrideTemplateId(row.getOverrideScheduleTemplateId());
            } else if (CalendarExceptionTypeConstants.REDUCED.equals(type)) {
                plan.setOverrideTemplateId(row.getOverrideScheduleTemplateId());
                plan.setReducedCapacity(true);
            } else if (CalendarExceptionTypeConstants.EXTRA.equals(type)) {
                plan.setGenerateExtra(true);
                plan.setExtraTemplateId(row.getOverrideScheduleTemplateId());
            }
        }
        return plan;
    }

    /**
     * 构建月历标记：date → 例外类型列表。
     */
    public Map<LocalDate, List<String>> buildMonthMarkerMap(Long organizationId, YearMonth month) {
        Map<LocalDate, List<String>> markers = new HashMap<>();
        for (CalendarException row : this.listByMonth(organizationId, month)) {
            LocalDate start = row.getExceptionDate();
            LocalDate end = row.getRangeEnd() != null ? row.getRangeEnd() : row.getExceptionDate();
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                if (YearMonth.from(cursor).equals(month)) {
                    markers.computeIfAbsent(cursor, key -> new ArrayList<>()).add(row.getExceptionType());
                }
                cursor = cursor.plusDays(1);
            }
        }
        return markers;
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
