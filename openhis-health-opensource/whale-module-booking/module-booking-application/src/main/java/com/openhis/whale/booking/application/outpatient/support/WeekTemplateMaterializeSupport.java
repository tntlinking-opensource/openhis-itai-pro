/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.request.ProvisionSlotBatchRequest;
import com.openhis.whale.booking.application.outpatient.request.SlotTemplateRequest;
import com.openhis.whale.booking.application.share.ScheduleActorMasterDataValidator;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.schedule.command.AdjustPlanningHorizonCommand;
import com.openhis.whale.booking.domain.schedule.command.CreateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.PublishScheduleCommand;
import com.openhis.whale.booking.domain.schedule.service.ScheduleDomainService;
import com.openhis.whale.booking.domain.share.VirtualSlotKeySupport;
import com.openhis.whale.booking.domain.slot.command.ProvisionSlotBatchCommand;
import com.openhis.whale.booking.domain.slot.entity.Slot;
import com.openhis.whale.booking.domain.slot.repository.SlotRepository;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.service.TemplateQueryService;
import com.openhis.whale.booking.domain.template.service.WeekScheduleTemplateQueryService;
import com.openhis.whale.booking.application.share.BookingConcurrencySupport;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 周模板虚拟号源 lazy 物化（2.25 · TC-TPL-06）。
 */
@Service
@RequiredArgsConstructor
public class WeekTemplateMaterializeSupport {

    private final CalendarExceptionQueryService calendarExceptionQueryService;
    private final WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    private final TemplateQueryService templateQueryService;
    private final PolicyQueryService policyQueryService;
    private final ScheduleDomainService scheduleDomainService;
    private final SlotDomainService slotDomainService;
    private final SlotRepository slotRepository;
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    private final ScheduleActorMasterDataValidator scheduleActorMasterDataValidator;
    private final BookingConcurrencySupport bookingConcurrencySupport;

    /**
     * 解析 virtualSlotKey，物化 Schedule+Slot 并返回真实 slotId。
     */
    public Long materializeVirtualSlot(String virtualSlotKey, Long operatorId) {
        VirtualSlotKeySupport.VirtualSlotKeyParts parts = VirtualSlotKeySupport.decode(virtualSlotKey);
        if (calendarExceptionQueryService.resolveDayGeneratePlan(parts.getOrganizationId(), parts.getDate()).isSkipNormal()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "该日不可预约（日历例外 CLOSED）");
        }
        WeekScheduleTemplate week = weekScheduleTemplateQueryService.findDefaultForOrganization(parts.getOrganizationId());
        if (week == null || !Boolean.TRUE.equals(week.getFallbackBookable())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "科室未启用模板态可约");
        }
        ScheduleTemplate dayTemplate = templateQueryService.loadScheduleTemplateById(parts.getDayScheduleTemplateId());
        this.assertDayTemplateUsable(dayTemplate, parts.getOrganizationId());

        Optional<Slot> existing = slotRepository.findFirstByOrganizationIdAndStartTimeAndEndTimeAndIsDeletedFalse(
            parts.getOrganizationId(), parts.getStartTime(), parts.getEndTime());
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        Long scheduleId = this.createScheduleForDay(dayTemplate, parts.getOrganizationId(), parts.getDate(), operatorId);
        SlotProvisionTemplate slotTemplate = this.resolveSlotTemplate(dayTemplate);
        LocalDateTime[] horizon = outpatientScheduleMapper.resolvePlanningHorizonFromT1(slotTemplate, parts.getDate());
        this.publishSchedule(scheduleId, horizon[0], horizon[1], operatorId);
        ReleasePolicy releasePolicy = this.resolveReleasePolicy(dayTemplate);
        try {
            this.provisionSlotsForDay(
                scheduleId, parts.getOrganizationId(), parts.getDate(), horizon[0], horizon[1],
                slotTemplate, releasePolicy, operatorId);
        } catch (RuntimeException ex) {
            if (bookingConcurrencySupport.isVirtualSlotMaterializeConflict(ex)) {
                return slotRepository.findFirstByOrganizationIdAndStartTimeAndEndTimeAndIsDeletedFalse(
                        parts.getOrganizationId(), parts.getStartTime(), parts.getEndTime())
                    .map(Slot::getId)
                    .orElseThrow(() -> new BusinessException(
                        ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "lazy 物化并发冲突后未找到号源"));
            }
            throw ex;
        }

        return slotRepository.findFirstByOrganizationIdAndStartTimeAndEndTimeAndIsDeletedFalse(
                parts.getOrganizationId(), parts.getStartTime(), parts.getEndTime())
            .map(Slot::getId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "lazy 物化后未找到匹配号源"));
    }

    private void assertDayTemplateUsable(ScheduleTemplate template, Long organizationId) {
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日模板未启用");
        }
        if (template.getOrganizationId() != null && !template.getOrganizationId().equals(organizationId)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板与科室不匹配");
        }
    }

    private Long createScheduleForDay(
        ScheduleTemplate template, Long organizationId, LocalDate date, Long operatorId) {
        CreateScheduleCommand command = outpatientScheduleMapper.toCreateScheduleFromScheduleTemplate(
            template, organizationId, date, operatorId);
        scheduleActorMasterDataValidator.assertCreateCommandActorsReferenceValid(
            organizationId, command.getItem().getActors());
        Long scheduleId = scheduleDomainService.createSchedule(command);
        SlotProvisionTemplate slotTemplate = this.resolveSlotTemplate(template);
        LocalDateTime[] horizon = outpatientScheduleMapper.resolvePlanningHorizonFromT1(slotTemplate, date);
        AdjustPlanningHorizonCommand horizonCommand = outpatientScheduleMapper.toAdjustPlanningHorizonCommand(
            scheduleId, horizon[0], horizon[1], operatorId);
        scheduleDomainService.adjustPlanningHorizon(horizonCommand);
        return scheduleId;
    }

    private void publishSchedule(Long scheduleId, LocalDateTime start, LocalDateTime end, Long operatorId) {
        PublishScheduleCommand command = new PublishScheduleCommand();
        command.setOperatorId(operatorId);
        PublishScheduleCommand.PublishScheduleItemCommand item = new PublishScheduleCommand.PublishScheduleItemCommand();
        item.setScheduleId(scheduleId);
        item.setPlanningHorizonStart(start);
        item.setPlanningHorizonEnd(end);
        command.setItem(item);
        scheduleDomainService.publishSchedule(command);
    }

    private SlotProvisionTemplate resolveSlotTemplate(ScheduleTemplate template) {
        if (template.getSlotProvisionTemplateId() == null) {
            return null;
        }
        return templateQueryService.loadSlotProvisionTemplateById(template.getSlotProvisionTemplateId());
    }

    private ReleasePolicy resolveReleasePolicy(ScheduleTemplate template) {
        if (template.getDefaultReleasePolicyId() == null) {
            return null;
        }
        ReleasePolicy policy = policyQueryService.loadReleasePolicyById(template.getDefaultReleasePolicyId());
        if (policy == null || Boolean.TRUE.equals(policy.getIsDeleted())) {
            return null;
        }
        return policy;
    }

    private void provisionSlotsForDay(
        Long scheduleId,
        Long organizationId,
        LocalDate date,
        LocalDateTime dayStart,
        LocalDateTime dayEnd,
        SlotProvisionTemplate slotTemplate,
        ReleasePolicy releasePolicy,
        Long operatorId) {
        List<SlotTemplateRequest> templates = null;
        if (slotTemplate != null) {
            templates = outpatientScheduleMapper.buildSlotTemplatesFromT1(slotTemplate, date);
        }
        if (templates == null || templates.isEmpty()) {
            if (releasePolicy != null) {
                templates = outpatientScheduleMapper.buildSlotTemplatesFromPolicy(releasePolicy, dayStart, dayEnd);
            }
        }
        if (templates == null || templates.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "模板未关联 T1 或放号策略，无法 lazy 物化");
        }
        ProvisionSlotBatchRequest batchRequest = new ProvisionSlotBatchRequest();
        batchRequest.setScheduleId(scheduleId);
        batchRequest.setOrganizationId(organizationId);
        if (releasePolicy != null) {
            batchRequest.setReleasePolicyId(releasePolicy.getId());
        } else {
            batchRequest.setReleasePolicyId(null);
        }
        batchRequest.setSlotTemplates(templates);
        ProvisionSlotBatchCommand command = outpatientScheduleMapper.toProvisionSlotBatchCommand(batchRequest, operatorId);
        slotDomainService.provisionSlotBatch(command);
    }
}
