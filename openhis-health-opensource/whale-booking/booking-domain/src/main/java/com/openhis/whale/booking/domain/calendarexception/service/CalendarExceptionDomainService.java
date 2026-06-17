package com.openhis.whale.booking.domain.calendarexception.service;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import com.openhis.whale.booking.domain.calendarexception.repository.CalendarExceptionRepository;
import com.openhis.whale.booking.domain.share.CalendarExceptionTypeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T3 日历例外写侧领域服务。
 */
@Service
@RequiredArgsConstructor
public class CalendarExceptionDomainService {

    private final CalendarExceptionRepository calendarExceptionRepository;

    /**
     * 保存日历例外。
     */
    public CalendarException save(CalendarException entity, Long operatorId) {
        this.assertValid(entity);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        if (entity.getAutoApplyOnGenerate() == null) {
            entity.setAutoApplyOnGenerate(Boolean.TRUE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        return calendarExceptionRepository.save(entity);
    }

    /**
     * 软删日历例外。
     */
    public void delete(Long id, Long operatorId) {
        calendarExceptionRepository.findById(id).ifPresent(row -> {
            row.setIsDeleted(Boolean.TRUE);
            row.setDeletedAt(LocalDateTime.now());
            row.setDeletedBy(operatorId);
            calendarExceptionRepository.save(row);
        });
    }

    private void assertValid(CalendarException entity) {
        if (entity.getExceptionDate() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "exceptionDate 不能为空");
        }
        if (StringUtils.isBlank(entity.getExceptionType())
            || !CalendarExceptionTypeConstants.isValid(entity.getExceptionType().trim())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "exceptionType 非法");
        }
        entity.setExceptionType(entity.getExceptionType().trim());
        if (entity.getRangeEnd() != null && entity.getRangeEnd().isBefore(entity.getExceptionDate())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "rangeEnd 不能早于 exceptionDate");
        }
        if (CalendarExceptionTypeConstants.requiresOverrideTemplate(entity.getExceptionType())
            && entity.getOverrideScheduleTemplateId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "REDUCED/OVERRIDE/EXTRA 须指定 overrideScheduleTemplateId");
        }
    }
}
