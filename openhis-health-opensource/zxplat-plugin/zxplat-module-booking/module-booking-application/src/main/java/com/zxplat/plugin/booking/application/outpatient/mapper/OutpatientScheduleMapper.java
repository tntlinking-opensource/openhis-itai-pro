/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.mapper;

import com.zxplat.domain.share.enums.ScheduleActorKind;
import com.zxplat.plugin.booking.application.outpatient.request.CreateScheduleRequest;
import com.zxplat.plugin.booking.application.outpatient.request.DeactivateScheduleRequest;
import com.zxplat.plugin.booking.application.outpatient.request.PageSchedulesByCalendarRequest;
import com.zxplat.plugin.booking.application.outpatient.request.PageSchedulesByMonthRequest;
import com.zxplat.plugin.booking.application.outpatient.request.ProvisionSlotBatchRequest;
import com.zxplat.plugin.booking.application.outpatient.request.PublishScheduleRequest;
import com.zxplat.plugin.booking.application.outpatient.request.ReactivateScheduleRequest;
import com.zxplat.plugin.booking.application.outpatient.request.RetireScheduleRequest;
import com.zxplat.plugin.booking.application.outpatient.request.ScheduleActorRequest;
import com.zxplat.plugin.booking.application.outpatient.request.ScheduleServiceProfileRequest;
import com.zxplat.plugin.booking.domain.slot.queries.model.SlotDetailModel;
import com.zxplat.plugin.booking.application.outpatient.request.SlotTemplateRequest;
import com.zxplat.plugin.booking.application.outpatient.request.SubstitutePractitionerRequest;
import com.zxplat.plugin.booking.application.outpatient.request.UpdateScheduleDraftRequest;
import com.zxplat.plugin.booking.application.outpatient.response.LoadScheduleFormInitResponse;
import com.zxplat.plugin.booking.application.outpatient.response.PageSchedulesByCalendarResponse;
import com.zxplat.plugin.booking.application.outpatient.response.PageSchedulesByMonthResponse;
import com.zxplat.plugin.booking.application.outpatient.response.ScheduleCalendarMonthDayDto;
import com.zxplat.plugin.booking.application.outpatient.response.ScheduleMonthSummaryDto;
import com.zxplat.plugin.booking.application.share.MonthCalendarRangeSupport;
import com.zxplat.plugin.booking.application.outpatient.response.ProvisionSlotBatchResponse;
import com.zxplat.plugin.booking.application.outpatient.response.ScheduleActorDto;
import com.zxplat.plugin.booking.application.outpatient.response.ScheduleCalendarCellDto;
import com.zxplat.plugin.booking.application.outpatient.response.ScheduleCalendarDayDto;
import com.zxplat.plugin.booking.domain.masterdata.model.MasterDataOption;
import com.zxplat.plugin.booking.domain.masterdata.result.ScheduleFormMasterDataSnapshot;
import com.zxplat.plugin.booking.application.template.response.SlotProvisionTemplateItemDto;
import com.zxplat.plugin.booking.application.template.support.TemplateJsonSupport;
import com.zxplat.plugin.booking.domain.policy.entity.ReleasePolicy;
import com.zxplat.plugin.booking.domain.template.entity.ScheduleTemplate;
import com.zxplat.plugin.booking.domain.template.entity.SlotProvisionTemplate;
import com.zxplat.plugin.booking.domain.schedule.command.AdjustPlanningHorizonCommand;
import com.zxplat.plugin.booking.domain.schedule.command.CreateScheduleCommand;
import com.zxplat.plugin.booking.domain.schedule.command.DeactivateScheduleCommand;
import com.zxplat.plugin.booking.domain.schedule.command.PublishScheduleCommand;
import com.zxplat.plugin.booking.domain.schedule.command.ReactivateScheduleCommand;
import com.zxplat.plugin.booking.domain.schedule.command.SubstitutePractitionerCommand;
import com.zxplat.plugin.booking.domain.schedule.command.ReplaceScheduleActorsCommand;
import com.zxplat.plugin.booking.domain.schedule.command.RetireScheduleCommand;
import com.zxplat.plugin.booking.domain.schedule.command.UpdateScheduleCommentCommand;
import com.zxplat.plugin.booking.domain.schedule.command.UpdateServiceProfileCommand;
import com.zxplat.plugin.booking.domain.schedule.queries.CalendarScheduleQuery;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.zxplat.plugin.booking.domain.slot.command.ProvisionSlotBatchCommand;
import com.zxplat.plugin.booking.domain.slot.command.RetireUnbookedSlotsByScheduleCommand;
import com.zxplat.framework.core.application.dto.OptionDto;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 门诊排班应用层映射器。
 * <p>职责：排班/放号/停诊/月历 HTTP 契约 ↔ 领域 Command/Query/DTO；模板与策略时段切分在此完成。</p>
 */
@Component
public class OutpatientScheduleMapper {

    /**
     * HealthScheduleMasterDataReader 主数据快照 → 初始化响应。
     *
     * @param snapshot 排班表单主数据快照
     * @return 排班表单初始化响应
     */
    public LoadScheduleFormInitResponse toLoadScheduleFormInitResponse(ScheduleFormMasterDataSnapshot snapshot) {
        LoadScheduleFormInitResponse response = new LoadScheduleFormInitResponse();
        if (snapshot == null) {
            response.setDepartments(new ArrayList<>());
            response.setPractitioners(new ArrayList<>());
            response.setClinicLocations(new ArrayList<>());
            response.setHealthcareServices(new ArrayList<>());
            response.setActorTypeOptions(this.buildActorTypeOptions());
            return response;
        }
        response.setDepartments(this.toOptionDtoList(snapshot.getDepartments()));
        response.setPractitioners(this.toOptionDtoList(snapshot.getPractitioners()));
        response.setClinicLocations(this.toOptionDtoList(snapshot.getClinicLocations()));
        response.setHealthcareServices(this.toOptionDtoList(snapshot.getHealthcareServices()));
        response.setActorTypeOptions(this.buildActorTypeOptions());
        return response;
    }

    /**
     * 日历 HTTP 请求 → 领域查询。
     *
     * @param request 按日历分页排班请求
     * @return 日历日程查询
     */
    public CalendarScheduleQuery toCalendarScheduleQuery(PageSchedulesByCalendarRequest request) {
        return this.toCalendarScheduleQuery(
            request.getOrganizationId(),
            request.getRangeStart(),
            request.getRangeEnd(),
            request.getPractitionerId(),
            request.getLocationId(),
            request.getHealthcareServiceId());
    }

    /**
     * 月历 HTTP 请求 → 领域查询（网格区间）。
     *
     * @param request 按月分页排班请求
     * @return 日历日程查询
     */
    public CalendarScheduleQuery toCalendarScheduleQuery(PageSchedulesByMonthRequest request) {
        LocalDate[] monthRange = MonthCalendarRangeSupport.resolveMonthRange(request.getYearMonth());
        LocalDate[] gridRange = MonthCalendarRangeSupport.resolveGridRange(monthRange[0], monthRange[1]);
        return this.toCalendarScheduleQuery(
            request.getOrganizationId(),
            gridRange[0],
            gridRange[1],
            request.getPractitionerId(),
            request.getLocationId(),
            request.getHealthcareServiceId());
    }

    private CalendarScheduleQuery toCalendarScheduleQuery(
        Long organizationId,
        LocalDate rangeStart,
        LocalDate rangeEnd,
        Long practitionerId,
        Long locationId,
        Long healthcareServiceId) {
        CalendarScheduleQuery query = new CalendarScheduleQuery();
        query.setOrganizationId(organizationId);
        query.setRangeStart(rangeStart);
        query.setRangeEnd(rangeEnd);
        query.setPractitionerId(practitionerId);
        query.setLocationId(locationId);
        query.setHealthcareServiceId(healthcareServiceId);
        return query;
    }

    /**
     * 领域日历项 → 按日分组响应。
     *
     * @param schedules 日历日程项列表
     * @param rangeStart 区间起始日期
     * @param rangeEnd 区间结束日期
     * @return 按日历分页排班响应
     */
    public PageSchedulesByCalendarResponse toPageSchedulesByCalendarResponse(
        List<ScheduleCalendarItemModel> schedules,
        LocalDate rangeStart,
        LocalDate rangeEnd) {
        PageSchedulesByCalendarResponse response = new PageSchedulesByCalendarResponse();
        Map<LocalDate, ScheduleCalendarDayDto> dayMap = new LinkedHashMap<>();
        LocalDate cursor = rangeStart;
        while (!cursor.isAfter(rangeEnd)) {
            ScheduleCalendarDayDto day = new ScheduleCalendarDayDto();
            day.setDate(cursor);
            day.setSchedules(new ArrayList<>());
            dayMap.put(cursor, day);
            cursor = cursor.plusDays(1);
        }
        if (schedules != null) {
            for (ScheduleCalendarItemModel schedule : schedules) {
                for (Map.Entry<LocalDate, ScheduleCalendarDayDto> entry : dayMap.entrySet()) {
                    LocalDate day = entry.getKey();
                    if (this.scheduleCoversDay(schedule, day)) {
                        entry.getValue().getSchedules().add(this.toCalendarCellDto(schedule));
                    }
                }
            }
        }
        response.setDays(new ArrayList<>(dayMap.values()));
        return response;
    }

    /**
     * 领域日历项 → 月历响应（含摘要，2.23）。
     *
     * @param schedules 日历日程项列表
     * @param yearMonth 年月（yyyy-MM）
     * @param bookedAppointmentCount 当月已预约数量
     * @return 按月分页排班响应
     */
    public PageSchedulesByMonthResponse toPageSchedulesByMonthResponse(
        List<ScheduleCalendarItemModel> schedules,
        String yearMonth,
        int bookedAppointmentCount) {
        LocalDate[] monthRange = MonthCalendarRangeSupport.resolveMonthRange(yearMonth);
        LocalDate monthStart = monthRange[0];
        LocalDate monthEnd = monthRange[1];
        LocalDate[] gridRange = MonthCalendarRangeSupport.resolveGridRange(monthStart, monthEnd);
        LocalDate gridStart = gridRange[0];
        LocalDate gridEnd = gridRange[1];

        PageSchedulesByMonthResponse response = new PageSchedulesByMonthResponse();
        response.setYearMonth(yearMonth);
        response.setMonthStart(monthStart);
        response.setMonthEnd(monthEnd);
        response.setGridStart(gridStart);
        response.setGridEnd(gridEnd);

        Map<LocalDate, ScheduleCalendarMonthDayDto> dayMap = new LinkedHashMap<>();
        LocalDate cursor = gridStart;
        while (!cursor.isAfter(gridEnd)) {
            ScheduleCalendarMonthDayDto day = new ScheduleCalendarMonthDayDto();
            day.setDate(cursor);
            day.setInMonth(!cursor.isBefore(monthStart) && !cursor.isAfter(monthEnd));
            day.setSchedules(new ArrayList<>());
            day.setActiveCount(0);
            day.setDraftCount(0);
            day.setInactiveCount(0);
            dayMap.put(cursor, day);
            cursor = cursor.plusDays(1);
        }

        int totalScheduleCount = 0;
        if (schedules != null) {
            for (ScheduleCalendarItemModel schedule : schedules) {
                for (Map.Entry<LocalDate, ScheduleCalendarMonthDayDto> entry : dayMap.entrySet()) {
                    LocalDate day = entry.getKey();
                    if (!this.scheduleCoversDay(schedule, day)) {
                        continue;
                    }
                    ScheduleCalendarCellDto cell = this.toCalendarCellDto(schedule);
                    entry.getValue().getSchedules().add(cell);
                    this.incrementDayStatusCount(entry.getValue(), schedule.getStatusEnum());
                    if (Boolean.TRUE.equals(entry.getValue().getInMonth())) {
                        totalScheduleCount++;
                    }
                }
            }
        }

        ScheduleMonthSummaryDto summary = new ScheduleMonthSummaryDto();
        summary.setPublishedDayCount(0);
        summary.setDraftDayCount(0);
        summary.setUnscheduledDayCount(0);
        summary.setInactiveDayCount(0);
        summary.setBookedAppointmentCount(bookedAppointmentCount);
        summary.setTotalScheduleCount(totalScheduleCount);

        for (ScheduleCalendarMonthDayDto day : dayMap.values()) {
            if (!Boolean.TRUE.equals(day.getInMonth())) {
                continue;
            }
            boolean hasActive = day.getActiveCount() != null && day.getActiveCount() > 0;
            boolean hasDraft = day.getDraftCount() != null && day.getDraftCount() > 0;
            boolean hasInactive = day.getInactiveCount() != null && day.getInactiveCount() > 0;
            boolean hasAny = day.getSchedules() != null && !day.getSchedules().isEmpty();
            if (hasActive) {
                summary.setPublishedDayCount(summary.getPublishedDayCount() + 1);
            }
            if (hasDraft) {
                summary.setDraftDayCount(summary.getDraftDayCount() + 1);
            }
            if (hasInactive) {
                summary.setInactiveDayCount(summary.getInactiveDayCount() + 1);
            }
            if (!hasAny) {
                summary.setUnscheduledDayCount(summary.getUnscheduledDayCount() + 1);
            }
        }

        response.setDays(new ArrayList<>(dayMap.values()));
        response.setSummary(summary);
        return response;
    }

    private void incrementDayStatusCount(ScheduleCalendarMonthDayDto day, String statusEnum) {
        // 按 statusEnum 累加月历格子的 active/draft/inactive 计数
        if ("active".equalsIgnoreCase(statusEnum)) {
            day.setActiveCount(day.getActiveCount() + 1);
            return;
        }
        if ("draft".equalsIgnoreCase(statusEnum)) {
            day.setDraftCount(day.getDraftCount() + 1);
            return;
        }
        if ("inactive".equalsIgnoreCase(statusEnum) || "retired".equalsIgnoreCase(statusEnum)) {
            day.setInactiveCount(day.getInactiveCount() + 1);
        }
    }

    /**
     * 创建请求 → 领域命令。
     *
     * @param request 创建排班请求
     * @param operatorId 操作人主键
     * @return 创建排班命令
     */
    public CreateScheduleCommand toCreateScheduleCommand(CreateScheduleRequest request, Long operatorId) {
        CreateScheduleCommand command = new CreateScheduleCommand();
        command.setOperatorId(operatorId);
        CreateScheduleCommand.CreateScheduleItemCommand item = new CreateScheduleCommand.CreateScheduleItemCommand();
        item.setOrganizationId(request.getOrganizationId());
        item.setName(request.getName());
        item.setSlotProvisionTemplateId(request.getSlotProvisionTemplateId());
        item.setActors(this.toCreateActorCommands(request.getActors()));
        command.setItem(item);
        return command;
    }

    /**
     * 发布请求 → 领域命令。
     *
     * @param request 发布排班请求
     * @param operatorId 操作人主键
     * @return 发布排班命令
     */
    public PublishScheduleCommand toPublishScheduleCommand(PublishScheduleRequest request, Long operatorId) {
        PublishScheduleCommand command = new PublishScheduleCommand();
        command.setOperatorId(operatorId);
        PublishScheduleCommand.PublishScheduleItemCommand item = new PublishScheduleCommand.PublishScheduleItemCommand();
        item.setScheduleId(request.getScheduleId());
        item.setPlanningHorizonStart(request.getPlanningHorizonStart());
        item.setPlanningHorizonEnd(request.getPlanningHorizonEnd());
        command.setItem(item);
        return command;
    }

    /**
     * 停用请求 → 领域命令。
     *
     * @param request 停用排班请求
     * @param operatorId 操作人主键
     * @return 停用排班命令
     */
    public DeactivateScheduleCommand toDeactivateScheduleCommand(DeactivateScheduleRequest request, Long operatorId) {
        DeactivateScheduleCommand command = new DeactivateScheduleCommand();
        command.setOperatorId(operatorId);
        DeactivateScheduleCommand.DeactivateScheduleItemCommand item = new DeactivateScheduleCommand.DeactivateScheduleItemCommand();
        item.setScheduleId(request.getScheduleId());
        item.setReason(request.getReason());
        command.setItem(item);
        return command;
    }

    /**
     * 停诊后回收无预约号源 → 领域命令。
     *
     * @param scheduleId 日程主键
     * @param operatorId 操作人主键
     * @return 回收命令
     */
    public RetireUnbookedSlotsByScheduleCommand toRetireUnbookedSlotsByScheduleCommand(
        Long scheduleId, Long operatorId) {
        RetireUnbookedSlotsByScheduleCommand command = new RetireUnbookedSlotsByScheduleCommand();
        command.setOperatorId(operatorId);
        RetireUnbookedSlotsByScheduleCommand.RetireUnbookedSlotsByScheduleItemCommand item =
            new RetireUnbookedSlotsByScheduleCommand.RetireUnbookedSlotsByScheduleItemCommand();
        item.setScheduleId(scheduleId);
        command.setItem(item);
        return command;
    }

    /**
     * 作废日程请求 → 领域命令。
     *
     * @param request 作废请求
     * @param operatorId 操作人主键
     * @return 作废命令
     */
    public RetireScheduleCommand toRetireScheduleCommand(RetireScheduleRequest request, Long operatorId) {
        RetireScheduleCommand command = new RetireScheduleCommand();
        command.setOperatorId(operatorId);
        RetireScheduleCommand.RetireScheduleItemCommand item = new RetireScheduleCommand.RetireScheduleItemCommand();
        item.setScheduleId(request.getScheduleId());
        item.setReason(request.getReason());
        command.setItem(item);
        return command;
    }

    /**
     * 恢复开诊请求 → 领域命令。
     *
     * @param request 恢复开诊请求
     * @param operatorId 操作人主键
     * @return 恢复开诊命令
     */
    public ReactivateScheduleCommand toReactivateScheduleCommand(ReactivateScheduleRequest request, Long operatorId) {
        ReactivateScheduleCommand command = new ReactivateScheduleCommand();
        command.setOperatorId(operatorId);
        ReactivateScheduleCommand.ReactivateScheduleItemCommand item =
            new ReactivateScheduleCommand.ReactivateScheduleItemCommand();
        item.setScheduleId(request.getScheduleId());
        command.setItem(item);
        return command;
    }

    /**
     * 替诊请求 → 领域命令。
     *
     * @param request 替诊请求
     * @param operatorId 操作人主键
     * @return 替诊命令
     */
    public SubstitutePractitionerCommand toSubstitutePractitionerCommand(
        SubstitutePractitionerRequest request, Long operatorId) {
        SubstitutePractitionerCommand command = new SubstitutePractitionerCommand();
        command.setOperatorId(operatorId);
        SubstitutePractitionerCommand.SubstitutePractitionerItemCommand item =
            new SubstitutePractitionerCommand.SubstitutePractitionerItemCommand();
        item.setScheduleId(request.getScheduleId());
        item.setSubstitutePractitionerId(request.getSubstitutePractitionerId());
        item.setReason(request.getReason());
        command.setItem(item);
        return command;
    }

    /**
     * 放号请求 → 领域命令。
     *
     * @param request 批量放号请求
     * @param operatorId 操作人主键
     * @return 批量放号命令
     */
    public ProvisionSlotBatchCommand toProvisionSlotBatchCommand(ProvisionSlotBatchRequest request, Long operatorId) {
        ProvisionSlotBatchCommand command = new ProvisionSlotBatchCommand();
        command.setOperatorId(operatorId);
        ProvisionSlotBatchCommand.ProvisionSlotBatchItemCommand item = new ProvisionSlotBatchCommand.ProvisionSlotBatchItemCommand();
        item.setScheduleId(request.getScheduleId());
        item.setOrganizationId(request.getOrganizationId());
        List<ProvisionSlotBatchCommand.SlotTemplateItemCommand> templates = new ArrayList<>();
        for (SlotTemplateRequest templateRequest : request.getSlotTemplates()) {
            ProvisionSlotBatchCommand.SlotTemplateItemCommand template = new ProvisionSlotBatchCommand.SlotTemplateItemCommand();
            template.setStartTime(templateRequest.getStartTime());
            template.setEndTime(templateRequest.getEndTime());
            template.setCapacity(templateRequest.getCapacity());
            templates.add(template);
        }
        item.setSlotTemplates(templates);
        command.setItem(item);
        return command;
    }

    /**
     * 放号结果 → 响应。
     *
     * @param slotIds 生成的号源主键列表
     * @return 批量放号响应
     */
    public ProvisionSlotBatchResponse toProvisionSlotBatchResponse(List<Long> slotIds) {
        ProvisionSlotBatchResponse response = new ProvisionSlotBatchResponse();
        if (slotIds == null) {
            response.setSlotIds(new ArrayList<>());
            response.setGeneratedCount(0);
            return response;
        }
        response.setSlotIds(slotIds);
        response.setGeneratedCount(slotIds.size());
        return response;
    }

    /**
     * 编辑草稿：参与者替换命令（可选字段）。
     *
     * @param request 更新排班草稿请求
     * @param operatorId 操作人主键
     * @return 替换排班参与者命令
     */
    public ReplaceScheduleActorsCommand toReplaceScheduleActorsCommand(UpdateScheduleDraftRequest request, Long operatorId) {
        ReplaceScheduleActorsCommand command = new ReplaceScheduleActorsCommand();
        command.setOperatorId(operatorId);
        ReplaceScheduleActorsCommand.ReplaceScheduleActorsItemCommand item = new ReplaceScheduleActorsCommand.ReplaceScheduleActorsItemCommand();
        item.setScheduleId(request.getScheduleId());
        item.setActors(this.toReplaceActorCommands(request.getActors()));
        command.setItem(item);
        return command;
    }

    /**
     * 编辑草稿：规划窗口调整命令。
     *
     * @param request 更新排班草稿请求
     * @param operatorId 操作人主键
     * @return 调整规划窗口命令
     */
    public AdjustPlanningHorizonCommand toAdjustPlanningHorizonCommand(UpdateScheduleDraftRequest request, Long operatorId) {
        return this.toAdjustPlanningHorizonCommand(
            request.getScheduleId(),
            request.getPlanningHorizonStart(),
            request.getPlanningHorizonEnd(),
            operatorId);
    }

    /**
     * 创建/编辑：规划窗口调整命令。
     *
     * @param scheduleId 日程主键
     * @param planningHorizonStart 新规划窗口起始
     * @param planningHorizonEnd 新规划窗口结束
     * @param operatorId 操作人主键
     * @return 调整规划窗口命令
     */
    public AdjustPlanningHorizonCommand toAdjustPlanningHorizonCommand(
        Long scheduleId,
        LocalDateTime planningHorizonStart,
        LocalDateTime planningHorizonEnd,
        Long operatorId) {
        AdjustPlanningHorizonCommand command = new AdjustPlanningHorizonCommand();
        command.setOperatorId(operatorId);
        AdjustPlanningHorizonCommand.AdjustPlanningHorizonItemCommand item = new AdjustPlanningHorizonCommand.AdjustPlanningHorizonItemCommand();
        item.setScheduleId(scheduleId);
        item.setNewStart(planningHorizonStart);
        item.setNewEnd(planningHorizonEnd);
        command.setItem(item);
        return command;
    }

    /**
     * 编辑草稿：服务口径更新命令。
     *
     * @param request 更新排班草稿请求
     * @param operatorId 操作人主键
     * @return 更新服务口径命令
     */
    public UpdateServiceProfileCommand toUpdateServiceProfileCommand(UpdateScheduleDraftRequest request, Long operatorId) {
        UpdateServiceProfileCommand command = new UpdateServiceProfileCommand();
        command.setOperatorId(operatorId);
        UpdateServiceProfileCommand.UpdateServiceProfileItemCommand item = new UpdateServiceProfileCommand.UpdateServiceProfileItemCommand();
        item.setScheduleId(request.getScheduleId());
        ScheduleServiceProfileRequest profile = request.getServiceProfile();
        if (profile != null) {
            item.setServiceCategoryJson(this.toConceptSnapshot(profile.getServiceCategoryCode(), profile.getServiceCategoryDisplay()));
            item.setSpecialtyJson(this.toConceptSnapshot(profile.getSpecialtyCode(), profile.getSpecialtyDisplay()));
            item.setServiceTypeConceptJson(this.toConceptSnapshot(profile.getServiceTypeCode(), profile.getServiceTypeDisplay()));
            item.setHealthcareServiceId(profile.getHealthcareServiceId());
            item.setProfileVersion(profile.getProfileVersion());
        }
        command.setItem(item);
        return command;
    }

    /**
     * 编辑草稿：说明更新命令。
     *
     * @param request 更新排班草稿请求
     * @param operatorId 操作人主键
     * @return 更新排班说明命令
     */
    public UpdateScheduleCommentCommand toUpdateScheduleCommentCommand(UpdateScheduleDraftRequest request, Long operatorId) {
        UpdateScheduleCommentCommand command = new UpdateScheduleCommentCommand();
        command.setOperatorId(operatorId);
        UpdateScheduleCommentCommand.UpdateScheduleCommentItemCommand item = new UpdateScheduleCommentCommand.UpdateScheduleCommentItemCommand();
        item.setScheduleId(request.getScheduleId());
        item.setCommentMarkdown(request.getCommentMarkdown());
        command.setItem(item);
        return command;
    }

    private List<OptionDto> toOptionDtoList(List<MasterDataOption> options) {
        List<OptionDto> result = new ArrayList<>();
        if (options == null) {
            return result;
        }
        for (MasterDataOption option : options) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            result.add(new OptionDto(option.getSort(), option.getValue(), option.getLabel()));
        }
        return result;
    }

    private List<OptionDto> buildActorTypeOptions() {
        List<OptionDto> options = new ArrayList<>();
        for (ScheduleActorKind kind : ScheduleActorKind.values()) {
            options.add(new OptionDto(kind.getSortOrder(), String.valueOf(kind.getSortOrder()), kind.getDisplay()));
        }
        return options;
    }

    private boolean scheduleCoversDay(ScheduleCalendarItemModel schedule, LocalDate day) {
        // 规划窗口与日历日有交集即视为当日可见
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEndExclusive = day.plusDays(1).atStartOfDay();
        LocalDateTime horizonStart = schedule.getPlanningHorizonStart();
        LocalDateTime horizonEnd = schedule.getPlanningHorizonEnd();
        if (horizonStart == null && horizonEnd == null) {
            return false;
        }
        if (horizonStart != null && !horizonStart.isBefore(dayEndExclusive)) {
            return false;
        }
        if (horizonEnd != null && !horizonEnd.isAfter(dayStart)) {
            return false;
        }
        return true;
    }

    private ScheduleCalendarCellDto toCalendarCellDto(ScheduleCalendarItemModel schedule) {
        // 领域日历项 → 周历/月历单元格摘要（含替诊原医师快照）
        ScheduleCalendarCellDto cell = new ScheduleCalendarCellDto();
        cell.setScheduleId(schedule.getId());
        cell.setName(schedule.getName());
        cell.setStatusEnum(schedule.getStatusEnum());
        cell.setPlanningHorizonStart(schedule.getPlanningHorizonStart());
        cell.setPlanningHorizonEnd(schedule.getPlanningHorizonEnd());
        cell.setHealthcareServiceId(schedule.getHealthcareServiceId());
        cell.setActors(this.toActorDtoList(schedule.getActors()));
        cell.setSubstitutePractitionerId(schedule.getSubstitutePractitionerId());
        cell.setOriginalPractitionerId(this.resolvePractitionerId(schedule.getActors()));
        cell.setSourceScheduleTemplateId(schedule.getSourceScheduleTemplateId());
        cell.setSlotProvisionTemplateId(schedule.getSlotProvisionTemplateId());
        return cell;
    }

    /**
     * 填充替诊展示文案。
     *
     * @param cells 日历单元格列表
     * @param practitioners 医师主数据选项列表
     */
    public void enrichSubstituteDisplay(List<ScheduleCalendarCellDto> cells, List<MasterDataOption> practitioners) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = this.toNameMap(practitioners);
        for (ScheduleCalendarCellDto cell : cells) {
            if (cell == null || cell.getSubstitutePractitionerId() == null) {
                continue;
            }
            cell.setSubstituteDisplay(this.buildSubstituteDisplay(
                cell.getSubstitutePractitionerId(), cell.getOriginalPractitionerId(), nameMap));
        }
    }

    /**
     * 填充日历格子的参与者展示名（出诊医师、诊室、号源类型）。
     *
     * @param cells 日历单元格列表
     * @param snapshot 排班表单主数据快照
     */
    public void enrichCalendarActorLabels(List<ScheduleCalendarCellDto> cells, ScheduleFormMasterDataSnapshot snapshot) {
        if (cells == null || cells.isEmpty() || snapshot == null) {
            return;
        }
        Map<Long, String> practitionerNames = this.toNameMap(snapshot.getPractitioners());
        Map<Long, String> locationNames = this.toNameMap(snapshot.getClinicLocations());
        Map<Long, String> serviceNames = this.toNameMap(snapshot.getHealthcareServices());
        int practitionerType = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
        int locationType = ScheduleActorKind.LOCATION.getSortOrder().intValue();
        int serviceType = ScheduleActorKind.HEALTHCARE_SERVICE.getSortOrder().intValue();
        for (ScheduleCalendarCellDto cell : cells) {
            if (cell == null || cell.getActors() == null) {
                continue;
            }
            for (ScheduleActorDto actor : cell.getActors()) {
                if (actor == null || actor.getActorId() == null || actor.getActorType() == null) {
                    continue;
                }
                if (practitionerType == actor.getActorType()) {
                    cell.setPractitionerName(practitionerNames.get(actor.getActorId()));
                    if (cell.getOriginalPractitionerId() == null) {
                        cell.setOriginalPractitionerId(actor.getActorId());
                    }
                } else if (locationType == actor.getActorType()) {
                    cell.setLocationName(locationNames.get(actor.getActorId()));
                } else if (serviceType == actor.getActorType()) {
                    cell.setHealthcareServiceName(serviceNames.get(actor.getActorId()));
                }
            }
        }
    }

    /**
     * 构建替诊展示文案（如「B替A」）。
     *
     * @param substituteId 替诊医师主键
     * @param originalId 原出诊医师主键
     * @param nameMap 医师主键 → 姓名映射
     * @return 替诊展示文案
     */
    public String buildSubstituteDisplay(Long substituteId, Long originalId, Map<Long, String> nameMap) {
        if (substituteId == null) {
            return null;
        }
        String substituteName = nameMap.get(substituteId);
        if (substituteName == null) {
            substituteName = String.valueOf(substituteId);
        }
        if (originalId == null) {
            return substituteName + "替诊";
        }
        String originalName = nameMap.get(originalId);
        if (originalName == null) {
            originalName = String.valueOf(originalId);
        }
        return substituteName + "替" + originalName;
    }

    private Long resolvePractitionerId(List<ScheduleActorModel> actors) {
        if (actors == null) {
            return null;
        }
        int practitionerType = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
        for (ScheduleActorModel actor : actors) {
            if (actor != null && practitionerType == actor.getActorType()) {
                return actor.getActorId();
            }
        }
        return null;
    }

    private Map<Long, String> toNameMap(List<MasterDataOption> options) {
        Map<Long, String> nameMap = new HashMap<>();
        if (options == null) {
            return nameMap;
        }
        for (MasterDataOption option : options) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            try {
                nameMap.put(Long.parseLong(option.getValue()), option.getLabel());
            } catch (NumberFormatException ignored) {
                // skip invalid option
            }
        }
        return nameMap;
    }

    private List<ScheduleActorDto> toActorDtoList(List<ScheduleActorModel> actors) {
        List<ScheduleActorDto> result = new ArrayList<>();
        if (actors == null) {
            return result;
        }
        for (ScheduleActorModel actor : actors) {
            ScheduleActorDto dto = new ScheduleActorDto();
            dto.setActorType(actor.getActorType());
            dto.setActorId(actor.getActorId());
            dto.setSortNo(actor.getSortNo());
            result.add(dto);
        }
        return result;
    }

    private List<CreateScheduleCommand.ScheduleActorItemCommand> toCreateActorCommands(List<ScheduleActorRequest> actors) {
        List<CreateScheduleCommand.ScheduleActorItemCommand> result = new ArrayList<>();
        if (actors == null) {
            return result;
        }
        for (ScheduleActorRequest actor : actors) {
            CreateScheduleCommand.ScheduleActorItemCommand item = new CreateScheduleCommand.ScheduleActorItemCommand();
            item.setActorType(actor.getActorType());
            item.setActorId(actor.getActorId());
            item.setSortNo(actor.getSortNo());
            result.add(item);
        }
        return result;
    }

    private List<ReplaceScheduleActorsCommand.ScheduleActorItemCommand> toReplaceActorCommands(List<ScheduleActorRequest> actors) {
        List<ReplaceScheduleActorsCommand.ScheduleActorItemCommand> result = new ArrayList<>();
        if (actors == null) {
            return result;
        }
        for (ScheduleActorRequest actor : actors) {
            ReplaceScheduleActorsCommand.ScheduleActorItemCommand item = new ReplaceScheduleActorsCommand.ScheduleActorItemCommand();
            item.setActorType(actor.getActorType());
            item.setActorId(actor.getActorId());
            item.setSortNo(actor.getSortNo());
            result.add(item);
        }
        return result;
    }

    private String toConceptSnapshot(String code, String display) {
        if (code == null && display == null) {
            return null;
        }
        String safeCode;
        if (code == null) {
            safeCode = "";
        } else {
            safeCode = code.replace("\"", "\\\"");
        }
        String safeDisplay;
        if (display == null) {
            safeDisplay = "";
        } else {
            safeDisplay = display.replace("\"", "\\\"");
        }
        return "{\"code\":\"" + safeCode + "\",\"display\":\"" + safeDisplay + "\"}";
    }

    /**
     * T2 日程模板 → 指定日期的创建命令（2.17）。
     *
     * @param template 日排班模板实体
     * @param organizationId 机构主键
     * @param date 目标日期
     * @param operatorId 操作人主键
     * @return 创建排班命令
     */
    public CreateScheduleCommand toCreateScheduleFromScheduleTemplate(
        ScheduleTemplate template, Long organizationId, LocalDate date, Long operatorId) {
        CreateScheduleCommand command = new CreateScheduleCommand();
        command.setOperatorId(operatorId);
        CreateScheduleCommand.CreateScheduleItemCommand scheduleItem = new CreateScheduleCommand.CreateScheduleItemCommand();
        scheduleItem.setOrganizationId(organizationId);
        scheduleItem.setName(template.getTemplateName() + "-" + date);
        List<CreateScheduleCommand.ScheduleActorItemCommand> actors = new ArrayList<>();
        int sortNo = 0;
        if (template.getPractitionerId() != null) {
            CreateScheduleCommand.ScheduleActorItemCommand practitioner = new CreateScheduleCommand.ScheduleActorItemCommand();
            practitioner.setActorType(ScheduleActorKind.PRACTITIONER.getSortOrder().intValue());
            practitioner.setActorId(template.getPractitionerId());
            practitioner.setSortNo(sortNo);
            actors.add(practitioner);
            sortNo++;
        }
        if (template.getLocationId() != null) {
            CreateScheduleCommand.ScheduleActorItemCommand location = new CreateScheduleCommand.ScheduleActorItemCommand();
            location.setActorType(ScheduleActorKind.LOCATION.getSortOrder().intValue());
            location.setActorId(template.getLocationId());
            location.setSortNo(sortNo);
            actors.add(location);
            sortNo++;
        }
        if (template.getHealthcareServiceId() != null) {
            CreateScheduleCommand.ScheduleActorItemCommand service = new CreateScheduleCommand.ScheduleActorItemCommand();
            service.setActorType(ScheduleActorKind.HEALTHCARE_SERVICE.getSortOrder().intValue());
            service.setActorId(template.getHealthcareServiceId());
            service.setSortNo(sortNo);
            actors.add(service);
        }
        scheduleItem.setActors(actors);
        scheduleItem.setSourceScheduleTemplateId(template.getId());
        scheduleItem.setSlotProvisionTemplateId(template.getSlotProvisionTemplateId());
        command.setItem(scheduleItem);
        return command;
    }

    /**
     * T1 放号时段 → 指定日期的规划窗口起止；无有效时段时返回全天窗口。
     *
     * @param slotTemplate T1 放号时段模板
     * @param date 目标日期
     * @return 长度为 2 的数组：[planningHorizonStart, planningHorizonEnd]
     */
    public LocalDateTime[] resolvePlanningHorizonFromT1(SlotProvisionTemplate slotTemplate, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(23, 59, 59);
        if (slotTemplate == null || date == null) {
            return new LocalDateTime[] { dayStart, dayEnd };
        }
        List<SlotTemplateRequest> slots = this.buildSlotTemplatesFromT1(slotTemplate, date);
        if (slots.isEmpty()) {
            return new LocalDateTime[] { dayStart, dayEnd };
        }
        LocalDateTime horizonStart = null;
        LocalDateTime horizonEnd = null;
        for (SlotTemplateRequest slot : slots) {
            if (slot.getStartTime() == null || slot.getEndTime() == null) {
                continue;
            }
            if (horizonStart == null || slot.getStartTime().isBefore(horizonStart)) {
                horizonStart = slot.getStartTime();
            }
            if (horizonEnd == null || slot.getEndTime().isAfter(horizonEnd)) {
                horizonEnd = slot.getEndTime();
            }
        }
        if (horizonStart == null || horizonEnd == null) {
            return new LocalDateTime[] { dayStart, dayEnd };
        }
        return new LocalDateTime[] { horizonStart, horizonEnd };
    }

    /**
     * T1 放号时段模板 → 指定日期的放号时段列表。
     *
     * @param template 号源时段模板实体
     * @param date 目标日期
     * @return 放号时段请求列表
     */
    public List<SlotTemplateRequest> buildSlotTemplatesFromT1(SlotProvisionTemplate template, LocalDate date) {
        List<SlotTemplateRequest> result = new ArrayList<>();
        if (template == null || date == null) {
            return result;
        }
        List<SlotProvisionTemplateItemDto> items = TemplateJsonSupport.parseSlotItems(template.getSlotTemplatesJson());
        for (SlotProvisionTemplateItemDto item : items) {
            if (item.getStartTime() == null || item.getEndTime() == null) {
                continue;
            }
            LocalDateTime start = this.combineDateAndTime(date, item.getStartTime());
            LocalDateTime end = this.combineDateAndTime(date, item.getEndTime());
            if (!end.isAfter(start)) {
                continue;
            }
            SlotTemplateRequest slot = new SlotTemplateRequest();
            slot.setStartTime(start);
            slot.setEndTime(end);
            if (item.getCapacity() != null) {
                slot.setCapacity(item.getCapacity());
            } else {
                slot.setCapacity(1);
            }
            result.add(slot);
        }
        return result;
    }

    private LocalDateTime combineDateAndTime(LocalDate date, String timeText) {
        String normalized = timeText.trim();
        LocalTime time;
        try {
            if (normalized.length() <= 5) {
                time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm"));
            } else {
                time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm:ss"));
            }
        } catch (DateTimeParseException ex) {
            time = LocalTime.parse(normalized);
        }
        return date.atTime(time);
    }

    /**
     * 复制放号：源 Slot 明细 → 偏移后的时段模板。
     *
     * @param sourceSlots 源号源明细列表
     * @param dayOffset 日期偏移天数
     * @return 放号时段请求列表
     */
    public List<SlotTemplateRequest> buildSlotTemplatesFromSourceSlots(
        List<SlotDetailModel> sourceSlots, long dayOffset) {
        List<SlotTemplateRequest> templates = new ArrayList<>();
        if (sourceSlots == null || sourceSlots.isEmpty()) {
            return templates;
        }
        for (SlotDetailModel slot : sourceSlots) {
            if (slot == null || slot.getStartTime() == null || slot.getEndTime() == null) {
                continue;
            }
            SlotTemplateRequest template = new SlotTemplateRequest();
            template.setStartTime(slot.getStartTime().plusDays(dayOffset));
            template.setEndTime(slot.getEndTime().plusDays(dayOffset));
            template.setCapacity(slot.getCapacity());
            templates.add(template);
        }
        return templates;
    }

    /**
     * 复制日程参与者 → 创建命令。
     *
     * @param source 源日历日程项
     * @param organizationId 机构主键
     * @param name 新日程名称
     * @param operatorId 操作人主键
     * @return 创建排班命令
     */
    public CreateScheduleCommand toCopyScheduleCommand(
        ScheduleCalendarItemModel source, Long organizationId, String name, Long operatorId) {
        CreateScheduleCommand command = new CreateScheduleCommand();
        command.setOperatorId(operatorId);
        CreateScheduleCommand.CreateScheduleItemCommand item = new CreateScheduleCommand.CreateScheduleItemCommand();
        item.setOrganizationId(organizationId);
        item.setName(name);
        item.setSourceScheduleTemplateId(source.getSourceScheduleTemplateId());
        item.setSlotProvisionTemplateId(source.getSlotProvisionTemplateId());
        item.setActors(this.toCreateActorCommandsFromModels(source.getActors()));
        command.setItem(item);
        return command;
    }

    /**
     * 放号策略 → 时段模板（按日程规划窗口整日切分）。
     *
     * @param policy 放号策略实体
     * @param horizonStart 规划窗口起始
     * @param horizonEnd 规划窗口结束
     * @return 放号时段请求列表
     */
    public List<SlotTemplateRequest> buildSlotTemplatesFromPolicy(
        ReleasePolicy policy, LocalDateTime horizonStart, LocalDateTime horizonEnd) {
        List<SlotTemplateRequest> templates = new ArrayList<>();
        if (policy == null || horizonStart == null || horizonEnd == null || !horizonEnd.isAfter(horizonStart)) {
            return templates;
        }
        int slotMinutes;
        if (policy.getSlotMinutes() == null) {
            slotMinutes = 30;
        } else {
            slotMinutes = policy.getSlotMinutes();
        }
        int capacity;
        if (policy.getCapacityPerSlot() == null) {
            capacity = 1;
        } else {
            capacity = policy.getCapacityPerSlot();
        }
        LocalDateTime cursor = horizonStart;
        while (cursor.plusMinutes(slotMinutes).compareTo(horizonEnd) <= 0) {
            SlotTemplateRequest template = new SlotTemplateRequest();
            template.setStartTime(cursor);
            template.setEndTime(cursor.plusMinutes(slotMinutes));
            template.setCapacity(capacity);
            templates.add(template);
            cursor = cursor.plusMinutes(slotMinutes);
        }
        return templates;
    }

    private List<CreateScheduleCommand.ScheduleActorItemCommand> toCreateActorCommandsFromModels(
        List<ScheduleActorModel> actors) {
        List<CreateScheduleCommand.ScheduleActorItemCommand> result = new ArrayList<>();
        if (actors == null) {
            return result;
        }
        for (ScheduleActorModel actor : actors) {
            CreateScheduleCommand.ScheduleActorItemCommand item = new CreateScheduleCommand.ScheduleActorItemCommand();
            item.setActorType(actor.getActorType());
            item.setActorId(actor.getActorId());
            item.setSortNo(actor.getSortNo());
            result.add(item);
        }
        return result;
    }
}
