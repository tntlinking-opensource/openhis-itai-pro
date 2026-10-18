/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.healthcheckup.service;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayPlan;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDaySlot;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcDayType;
import com.zxplat.plugin.booking.domain.healthcheckup.entity.HcSlotTemplate;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcDayPlanRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcDaySlotRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcDayTypeRepository;
import com.zxplat.plugin.booking.domain.healthcheckup.repository.HcSlotTemplateRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 体检号源日历读侧。
 *
 * <p>边界：仅本聚合 Repository（模板 / 日计划 / 日类型 / 日时段）。</p>
 */
@Service
@RequiredArgsConstructor
public class HealthCheckupSlotCalendarQueryService {

    private final HcSlotTemplateRepository slotTemplateRepository;
    private final HcDayPlanRepository dayPlanRepository;
    private final HcDayTypeRepository dayTypeRepository;
    private final HcDaySlotRepository daySlotRepository;

    /**
     * 加载机构号源模板（不存在时返回空）。
     *
     * @param organizationId 机构主键
     * @return 模板可选结果
     */
    public Optional<HcSlotTemplate> findTemplateByOrganizationId(Long organizationId) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        return slotTemplateRepository.findFirstByOrganizationIdAndIsDeletedFalse(organizationId);
    }

    /**
     * 加载机构号源模板（不存在时抛错）。
     *
     * @param organizationId 机构主键
     * @return 模板实体
     */
    public HcSlotTemplate loadTemplateByOrganizationId(Long organizationId) {
        return this.findTemplateByOrganizationId(organizationId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "号源模板不存在"));
    }

    /**
     * 按日期区间列出日计划。
     *
     * @param organizationId 机构主键
     * @param fromDate 起始日期（含）
     * @param toDate 结束日期（含）
     * @return 日计划列表
     */
    public List<HcDayPlan> listDayPlansBetween(Long organizationId, LocalDate fromDate, LocalDate toDate) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (fromDate == null || toDate == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "日期区间不能为空");
        }
        if (toDate.isBefore(fromDate)) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "结束日期不得早于起始日期");
        }
        return dayPlanRepository.findByOrganizationIdAndPlanDateBetweenAndIsDeletedFalseOrderByPlanDateAsc(
            organizationId, fromDate, toDate);
    }

    /**
     * 加载指定日期日计划。
     *
     * @param organizationId 机构主键
     * @param planDate 计划日期
     * @return 日计划可选结果
     */
    public Optional<HcDayPlan> findDayPlan(Long organizationId, LocalDate planDate) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (planDate == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "planDate 不能为空");
        }
        return dayPlanRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalse(organizationId, planDate);
    }

    /**
     * 加载指定日期日计划（不存在时抛错）。
     *
     * @param organizationId 机构主键
     * @param planDate 计划日期
     * @return 日计划实体
     */
    public HcDayPlan loadDayPlan(Long organizationId, LocalDate planDate) {
        return this.findDayPlan(organizationId, planDate)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "日计划不存在"));
    }

    /**
     * 列出日计划下开放合同类型。
     *
     * @param dayPlanId 日计划主键
     * @return 日类型列表
     */
    public List<HcDayType> listDayTypesByDayPlanId(Long dayPlanId) {
        if (dayPlanId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "dayPlanId 不能为空");
        }
        return dayTypeRepository.findByDayPlanIdAndIsDeletedFalse(dayPlanId);
    }

    /**
     * 按机构与日期列出时段配额。
     *
     * @param organizationId 机构主键
     * @param planDate 计划日期
     * @return 时段配额列表
     */
    public List<HcDaySlot> listDaySlotsByDate(Long organizationId, LocalDate planDate) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (planDate == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "planDate 不能为空");
        }
        return daySlotRepository.findByOrganizationIdAndPlanDateAndIsDeletedFalseOrderByStartTimeAsc(organizationId, planDate);
    }

    /**
     * 按日计划主键列出时段配额。
     *
     * @param dayPlanId 日计划主键
     * @return 时段配额列表
     */
    public List<HcDaySlot> listDaySlotsByDayPlanId(Long dayPlanId) {
        if (dayPlanId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "dayPlanId 不能为空");
        }
        return daySlotRepository.findByDayPlanIdAndIsDeletedFalse(dayPlanId);
    }

    /**
     * 按主键加载时段配额。
     *
     * @param daySlotId 时段配额主键
     * @return 时段配额实体
     */
    public HcDaySlot loadDaySlotById(Long daySlotId) {
        if (daySlotId == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "daySlotId 不能为空");
        }
        return daySlotRepository.findByIdAndIsDeletedFalse(daySlotId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "时段配额不存在"));
    }

    /**
     * 按多个日期批量加载时段配额。
     *
     * @param organizationId 机构主键
     * @param planDates 计划日期集合
     * @return 时段配额列表
     */
    public List<HcDaySlot> listDaySlotsByDates(Long organizationId, List<LocalDate> planDates) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (planDates == null || planDates.isEmpty()) {
            return List.of();
        }
        return daySlotRepository.findByOrganizationIdAndPlanDateInAndIsDeletedFalse(organizationId, planDates);
    }
}
