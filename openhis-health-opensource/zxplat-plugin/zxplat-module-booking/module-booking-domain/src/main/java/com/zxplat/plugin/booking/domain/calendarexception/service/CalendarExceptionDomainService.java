/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.calendarexception.service;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.domain.calendarexception.entity.CalendarException;
import com.zxplat.plugin.booking.domain.calendarexception.repository.CalendarExceptionRepository;
import com.zxplat.plugin.booking.domain.share.CalendarExceptionTypeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * T3 日历例外写侧领域服务。
 *
 * <p>不变量：例外类型合法；REDUCED/OVERRIDE/EXTRA 须绑定替代日排班模板。</p>
 */
@Service
@RequiredArgsConstructor
public class CalendarExceptionDomainService {

    private final CalendarExceptionRepository calendarExceptionRepository;

    /**
     * 保存日历例外。
     *
     * @param entity 例外实体
     * @param operatorId 操作人
     * @return 落库后的实体
     * @throws BusinessException 字段校验失败时
     */
    public CalendarException save(CalendarException entity, Long operatorId) {
        // 前置校验：类型、日期区间与替代模板约束
        this.assertValid(entity);
        LocalDateTime now = LocalDateTime.now();
        // 新建时补齐审计字段
        if (entity.getId() == null) {
            entity.setCreatedAt(now);
            entity.setCreatedBy(operatorId);
            entity.setIsDeleted(Boolean.FALSE);
        }
        // 未显式指定时默认参与模板生成
        if (entity.getAutoApplyOnGenerate() == null) {
            entity.setAutoApplyOnGenerate(Boolean.TRUE);
        }
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(operatorId);
        // 持久化
        return calendarExceptionRepository.save(entity);
    }

    /**
     * 软删日历例外。
     *
     * @param id 例外主键
     * @param operatorId 操作人
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
        // 区间止日不得早于起日
        if (entity.getRangeEnd() != null && entity.getRangeEnd().isBefore(entity.getExceptionDate())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "rangeEnd 不能早于 exceptionDate");
        }
        // 减量/覆盖/加号例外须指定替代日排班
        if (CalendarExceptionTypeConstants.requiresOverrideTemplate(entity.getExceptionType())
            && entity.getOverrideScheduleTemplateId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "REDUCED/OVERRIDE/EXTRA 须指定 overrideScheduleTemplateId");
        }
    }
}
