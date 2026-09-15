/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.healthcheckup.dto.HcDayPlanDto;
import com.openhis.whale.booking.application.healthcheckup.dto.HcDaySummaryDto;
import com.openhis.whale.booking.application.healthcheckup.mapper.HealthCheckupSlotCalendarMapper;
import com.openhis.whale.booking.application.healthcheckup.request.AddHcDaySlotRequest;
import com.openhis.whale.booking.application.healthcheckup.request.BatchAddHcDaySlotsRequest;
import com.openhis.whale.booking.application.healthcheckup.request.CopyHcDayPlanRequest;
import com.openhis.whale.booking.application.healthcheckup.request.DeleteHcDaySlotRequest;
import com.openhis.whale.booking.application.healthcheckup.request.GenerateHcFutureDaysRequest;
import com.openhis.whale.booking.application.healthcheckup.request.GetHcSlotTemplateRequest;
import com.openhis.whale.booking.application.healthcheckup.request.LoadHcDayDetailRequest;
import com.openhis.whale.booking.application.healthcheckup.request.LoadHcMonthOverviewRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SaveHcSlotTemplateRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SetHcDayContractTypesRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SetHcDayRestRequest;
import com.openhis.whale.booking.application.healthcheckup.request.SetHcSlotOpenRequest;
import com.openhis.whale.booking.application.healthcheckup.request.UpdateHcSlotCapacityRequest;
import com.openhis.whale.booking.application.healthcheckup.response.BatchAddHcDaySlotsResponse;
import com.openhis.whale.booking.application.healthcheckup.response.GenerateHcFutureDaysResponse;
import com.openhis.whale.booking.application.healthcheckup.response.HcSlotTemplateDto;
import com.openhis.whale.booking.application.healthcheckup.response.LoadHcDayDetailResponse;
import com.openhis.whale.booking.application.healthcheckup.response.LoadHcMonthOverviewResponse;
import com.openhis.whale.booking.application.healthcheckup.response.SaveHcSlotTemplateResponse;
import com.openhis.whale.booking.application.healthcheckup.support.BookingCampusSupport;
import com.openhis.whale.booking.application.share.BookingOperatorResolver;
import com.openhis.whale.booking.domain.healthcheckup.entity.HcDayPlan;
import com.openhis.whale.booking.domain.healthcheckup.entity.HcDaySlot;
import com.openhis.whale.booking.domain.healthcheckup.entity.HcSlotTemplate;
import com.openhis.whale.booking.domain.healthcheckup.service.HealthCheckupSlotCalendarDomainService;
import com.openhis.whale.booking.domain.healthcheckup.service.HealthCheckupSlotCalendarQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 体检号源日历应用服务（18 号需求 · 运营侧）。
 *
 * <p>职责：模板配置、月历总览、日明细维护、生成/复制日计划。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class HealthCheckupSlotCalendarAppService {

    /** 号源日历读侧。 */
    private final HealthCheckupSlotCalendarQueryService slotCalendarQueryService;
    /** 号源日历写侧。 */
    private final HealthCheckupSlotCalendarDomainService slotCalendarDomainService;
    /** Request/DTO 映射。 */
    private final HealthCheckupSlotCalendarMapper slotCalendarMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;
    /** 校区模块开关。 */
    private final BookingCampusSupport bookingCampusSupport;

    /**
     * 加载机构号源模板。
     *
     * @param request 机构主键
     * @return 模板 DTO（不存在时返回空字段 DTO）
     */
    public HcSlotTemplateDto getSlotTemplate(@Valid GetHcSlotTemplateRequest request) {
        // Pre：按机构加载模板。
        Optional<HcSlotTemplate> found = slotCalendarQueryService.findTemplateByOrganizationId(request.getOrganizationId());
        if (found.isEmpty()) {
            HcSlotTemplateDto empty = new HcSlotTemplateDto();
            empty.setOrganizationId(request.getOrganizationId());
            return empty;
        }
        // Asm：映射模板 DTO。
        return slotCalendarMapper.toHcSlotTemplateDto(found.get());
    }

    /**
     * 保存机构号源模板。
     *
     * @param request 模板配置
     * @return 保存后的主键
     */
    @Transactional
    public SaveHcSlotTemplateResponse saveSlotTemplate(@Valid SaveHcSlotTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化模板。
        HcSlotTemplate saved = slotCalendarDomainService.saveTemplate(
            slotCalendarMapper.toHcSlotTemplateEntity(request), operatorId);
        SaveHcSlotTemplateResponse response = new SaveHcSlotTemplateResponse();
        response.setId(saved.getId());
        // Asm：返回主键。
        return response;
    }

    /**
     * 按模板一键生成未来 N 天。
     *
     * @param request 机构、天数与可选校区
     * @return 实际生成日计划数
     */
    @Transactional
    public GenerateHcFutureDaysResponse generateFutureDays(@Valid GenerateHcFutureDaysRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：读取校区模块开关。
        boolean campusEnabled = bookingCampusSupport.isCampusModuleEnabled();
        // Cmd：按模板生成日计划与时段配额。
        int generated = slotCalendarDomainService.generateFutureDays(
            request.getOrganizationId(),
            request.getDays(),
            request.getFromDate(),
            request.getCampusCodes(),
            campusEnabled,
            operatorId);
        GenerateHcFutureDaysResponse response = new GenerateHcFutureDaysResponse();
        response.setGeneratedDayCount(generated);
        // Asm：返回生成数量。
        return response;
    }

    /**
     * 加载月历总览。
     *
     * @param request 年月或日期区间
     * @return 日摘要列表
     */
    public LoadHcMonthOverviewResponse loadMonthOverview(@Valid LoadHcMonthOverviewRequest request) {
        // Pre：解析查询日期区间。
        LocalDate[] range = this.resolveOverviewDateRange(request);
        List<HcDayPlan> plans = slotCalendarQueryService.listDayPlansBetween(
            request.getOrganizationId(), range[0], range[1]);
        List<HcDaySlot> allSlots = slotCalendarQueryService.listDaySlotsByDates(
            request.getOrganizationId(), this.buildDateList(range[0], range[1]));
        Map<LocalDate, HcDayPlan> planByDate = new HashMap<>();
        for (HcDayPlan plan : plans) {
            planByDate.put(plan.getPlanDate(), plan);
        }
        Map<LocalDate, List<HcDaySlot>> slotsByDate = new HashMap<>();
        for (HcDaySlot slot : allSlots) {
            List<HcDaySlot> bucket = slotsByDate.get(slot.getPlanDate());
            if (bucket == null) {
                bucket = new ArrayList<>();
                slotsByDate.put(slot.getPlanDate(), bucket);
            }
            bucket.add(slot);
        }
        List<HcDaySummaryDto> rows = new ArrayList<>();
        LocalDate cursor = range[0];
        while (!cursor.isAfter(range[1])) {
            HcDayPlan plan = planByDate.get(cursor);
            List<HcDaySlot> daySlots = slotsByDate.get(cursor);
            if (daySlots == null) {
                daySlots = List.of();
            }
            rows.add(this.buildDaySummary(cursor, plan, daySlots));
            cursor = cursor.plusDays(1);
        }
        LoadHcMonthOverviewResponse response = new LoadHcMonthOverviewResponse();
        response.setDays(rows);
        // Asm：返回月历摘要。
        return response;
    }

    /**
     * 加载日明细（计划 + 开放类型 + 时段矩阵）。
     *
     * @param request 机构与日期
     * @return 日明细
     */
    public LoadHcDayDetailResponse loadDayDetail(@Valid LoadHcDayDetailRequest request) {
        // Pre：加载日计划、类型与时段。
        Optional<HcDayPlan> planOpt = slotCalendarQueryService.findDayPlan(
            request.getOrganizationId(), request.getPlanDate());
        LoadHcDayDetailResponse response = new LoadHcDayDetailResponse();
        if (planOpt.isEmpty()) {
            HcDayPlanDto emptyPlan = new HcDayPlanDto();
            emptyPlan.setOrganizationId(request.getOrganizationId());
            emptyPlan.setPlanDate(request.getPlanDate());
            emptyPlan.setRestFlag(Boolean.FALSE);
            response.setPlan(emptyPlan);
            response.setDayTypes(List.of());
            response.setSlots(List.of());
            return response;
        }
        HcDayPlan plan = planOpt.get();
        response.setPlan(slotCalendarMapper.toHcDayPlanDto(plan));
        response.setDayTypes(slotCalendarMapper.toHcDayTypeDtos(
            slotCalendarQueryService.listDayTypesByDayPlanId(plan.getId())));
        response.setSlots(slotCalendarMapper.toHcDaySlotDtos(
            slotCalendarQueryService.listDaySlotsByDayPlanId(plan.getId())));
        // Asm：返回日明细。
        return response;
    }

    /**
     * 更新时段容量。
     *
     * @param request 时段主键与新容量
     */
    @Transactional
    public void updateSlotCapacity(@Valid UpdateHcSlotCapacityRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：更新容量。
        slotCalendarDomainService.updateSlotCapacity(request.getDaySlotId(), request.getNewCapacity(), operatorId);
    }

    /**
     * 设置整日休息。
     *
     * @param request 机构、日期与休息标记
     */
    @Transactional
    public void setDayRest(@Valid SetHcDayRestRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：更新日计划休息标记。
        slotCalendarDomainService.setDayRest(
            request.getOrganizationId(), request.getPlanDate(), request.getRestFlag(), operatorId);
    }

    /**
     * 设置时段开放/关闭。
     *
     * @param request 时段主键与开放标记
     */
    @Transactional
    public void setSlotOpen(@Valid SetHcSlotOpenRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：更新开放标记。
        slotCalendarDomainService.setSlotOpen(request.getDaySlotId(), request.getOpenFlag(), operatorId);
    }

    /**
     * 全量替换日开放合同类型。
     *
     * @param request 机构、日期与类型列表
     */
    @Transactional
    public void setDayContractTypes(@Valid SetHcDayContractTypesRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：确保日计划存在。
        HcDayPlan plan = slotCalendarQueryService.findDayPlan(request.getOrganizationId(), request.getPlanDate())
            .orElseGet(() -> {
                HcDayPlan created = new HcDayPlan();
                created.setOrganizationId(request.getOrganizationId());
                created.setPlanDate(request.getPlanDate());
                created.setRestFlag(Boolean.FALSE);
                return slotCalendarDomainService.upsertDayPlan(created, operatorId);
            });
        // Cmd：全量替换开放类型。
        slotCalendarDomainService.replaceDayTypes(plan.getId(), request.getContractTypeCodes(), operatorId);
    }

    /**
     * 手动新增日时段配额。
     *
     * @param request 时段信息
     */
    @Transactional
    public void addDaySlot(@Valid AddHcDaySlotRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：确保日计划存在并解析校区。
        HcDayPlan plan = slotCalendarQueryService.findDayPlan(request.getOrganizationId(), request.getPlanDate())
            .orElseGet(() -> {
                HcDayPlan created = new HcDayPlan();
                created.setOrganizationId(request.getOrganizationId());
                created.setPlanDate(request.getPlanDate());
                created.setRestFlag(Boolean.FALSE);
                return slotCalendarDomainService.upsertDayPlan(created, operatorId);
            });
        String campusCode = this.resolveCampusCodeForWrite(request.getCampusCode());
        HcDaySlot slot = new HcDaySlot();
        slot.setOrganizationId(request.getOrganizationId());
        slot.setDayPlanId(plan.getId());
        slot.setPlanDate(request.getPlanDate());
        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        slot.setContractTypeCode(request.getContractTypeCode());
        slot.setCampusCode(campusCode);
        slot.setCapacity(request.getCapacity());
        slot.setOpenFlag(request.getOpenFlag());
        // Cmd：新增时段配额。
        slotCalendarDomainService.addDaySlot(slot, operatorId);
    }

    /**
     * 按时间窗与间隔批量加档（已存在跳过）。
     *
     * @param request 批量加档参数
     * @return 新建与跳过数量
     */
    @Transactional
    public BatchAddHcDaySlotsResponse batchAddDaySlots(@Valid BatchAddHcDaySlotsRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        HcDayPlan plan = slotCalendarQueryService.findDayPlan(request.getOrganizationId(), request.getPlanDate())
            .orElseGet(() -> {
                HcDayPlan created = new HcDayPlan();
                created.setOrganizationId(request.getOrganizationId());
                created.setPlanDate(request.getPlanDate());
                created.setRestFlag(Boolean.FALSE);
                return slotCalendarDomainService.upsertDayPlan(created, operatorId);
            });
        String campusCode = this.resolveCampusCodeForWrite(request.getCampusCode());
        int capacity = request.getCapacity() == null ? 0 : request.getCapacity();
        boolean openFlag = request.getOpenFlag() == null || Boolean.TRUE.equals(request.getOpenFlag());
        List<String> campusCodes = this.resolveCampusCodesForBatchWrite(request.getCampusCodes(), campusCode);
        Map<String, Integer> capacityByCampus = request.getCapacityByCampus();
        int[] result = slotCalendarDomainService.batchAddDaySlots(
            plan,
            request.getWindowStart(),
            request.getWindowEnd(),
            request.getSlotMinutes(),
            request.getContractTypeCodes(),
            campusCodes,
            capacityByCampus,
            capacity,
            openFlag,
            operatorId);
        BatchAddHcDaySlotsResponse response = new BatchAddHcDaySlotsResponse();
        response.setCreatedCount(result[0]);
        response.setSkippedCount(result[1]);
        return response;
    }

    /**
     * 批量加档校区列表：优先 campusCodes；否则单 campusCode；都空则无校区（null）。
     */
    private List<String> resolveCampusCodesForBatchWrite(List<String> campusCodes, String fallbackCampusCode) {
        List<String> resolved = new ArrayList<>();
        if (campusCodes != null && bookingCampusSupport.isCampusModuleEnabled()) {
            for (String code : campusCodes) {
                String normalized = this.resolveCampusCodeForWrite(code);
                if (normalized != null) {
                    resolved.add(normalized);
                }
            }
        }
        if (!resolved.isEmpty()) {
            return resolved;
        }
        if (fallbackCampusCode != null) {
            resolved.add(fallbackCampusCode);
            return resolved;
        }
        resolved.add(null);
        return resolved;
    }

    /**
     * 删除日时段配额（已约不可删）。
     *
     * @param request 时段主键
     */
    @Transactional
    public void deleteDaySlot(@Valid DeleteHcDaySlotRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：软删时段配额。
        slotCalendarDomainService.deleteDaySlot(request.getDaySlotId(), operatorId);
    }

    /**
     * 复制源日计划到多个目标日期。
     *
     * @param request 源日期与目标日期列表
     */
    @Transactional
    public void copyDayPlan(@Valid CopyHcDayPlanRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：复制日计划（目标有已约则整次中止）。
        slotCalendarDomainService.copyDayPlan(
            request.getOrganizationId(), request.getSourceDate(), request.getTargetDates(), operatorId);
    }

    /**
     * 解析月历查询日期区间。
     *
     * @param request 月历请求
     * @return [fromDate, toDate]
     */
    private LocalDate[] resolveOverviewDateRange(LoadHcMonthOverviewRequest request) {
        if (request.getYearMonth() != null) {
            YearMonth ym = request.getYearMonth();
            LocalDate from = ym.atDay(1);
            LocalDate to = ym.atEndOfMonth();
            return new LocalDate[] {from, to};
        }
        if (request.getFromDate() != null && request.getToDate() != null) {
            if (request.getToDate().isBefore(request.getFromDate())) {
                throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "结束日期不得早于起始日期");
            }
            return new LocalDate[] {request.getFromDate(), request.getToDate()};
        }
        throw new BusinessException(ErrorCodeConstants.APPOINTMENT_INVALID_ARGUMENT, "yearMonth 或 fromDate/toDate 须传一组");
    }

    /**
     * 构建区间内所有日期列表。
     *
     * @param fromDate 起始日期
     * @param toDate 结束日期
     * @return 日期列表
     */
    private List<LocalDate> buildDateList(LocalDate fromDate, LocalDate toDate) {
        List<LocalDate> dates = new ArrayList<>();
        LocalDate cursor = fromDate;
        while (!cursor.isAfter(toDate)) {
            dates.add(cursor);
            cursor = cursor.plusDays(1);
        }
        return dates;
    }

    /**
     * 构建单日摘要 DTO。
     *
     * @param planDate 计划日期
     * @param plan 日计划（可空）
     * @param daySlots 当日时段列表
     * @return 日摘要
     */
    private HcDaySummaryDto buildDaySummary(LocalDate planDate, HcDayPlan plan, List<HcDaySlot> daySlots) {
        HcDaySummaryDto dto = new HcDaySummaryDto();
        dto.setPlanDate(planDate);
        boolean restFlag = false;
        if (plan != null && Boolean.TRUE.equals(plan.getRestFlag())) {
            restFlag = true;
        }
        dto.setRestFlag(restFlag);
        int totalCapacity = 0;
        int totalBooked = 0;
        for (HcDaySlot slot : daySlots) {
            if (slot.getCapacity() != null) {
                totalCapacity = totalCapacity + slot.getCapacity();
            }
            if (slot.getBookedCount() != null) {
                totalBooked = totalBooked + slot.getBookedCount();
            }
        }
        int remaining = totalCapacity - totalBooked;
        if (remaining < 0) {
            remaining = 0;
        }
        dto.setTotalCapacity(totalCapacity);
        dto.setTotalBooked(totalBooked);
        dto.setRemaining(remaining);
        dto.setStatus(this.resolveDayStatus(restFlag, totalCapacity, remaining));
        return dto;
    }

    /**
     * 解析日状态码。
     *
     * @param restFlag 是否休息
     * @param totalCapacity 总容量
     * @param remaining 剩余
     * @return OPEN / REST / FULL / EMPTY
     */
    private String resolveDayStatus(boolean restFlag, int totalCapacity, int remaining) {
        if (restFlag) {
            return "REST";
        }
        if (totalCapacity <= 0) {
            return "EMPTY";
        }
        if (remaining <= 0) {
            return "FULL";
        }
        return "OPEN";
    }

    /**
     * 写路径解析校区编码（模块关闭时强制 null）。
     *
     * @param campusCode 请求校区编码
     * @return 有效校区编码或 null
     */
    private String resolveCampusCodeForWrite(String campusCode) {
        if (!bookingCampusSupport.isCampusModuleEnabled()) {
            return null;
        }
        if (campusCode == null || campusCode.isBlank()) {
            return null;
        }
        return campusCode.trim();
    }
}
