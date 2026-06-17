package com.openhis.whale.booking.application.outpatient.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.request.GenerateFromTemplateRequest;
import com.openhis.whale.booking.application.outpatient.request.ProvisionSlotBatchRequest;
import com.openhis.whale.booking.application.outpatient.request.SlotTemplateRequest;
import com.openhis.whale.booking.application.outpatient.response.GenerateFromTemplateResponse;
import com.openhis.whale.booking.application.support.BookingAuditSupport;
import com.openhis.whale.booking.application.support.ScheduleActorMasterDataValidator;
import com.openhis.whale.booking.domain.calendarexception.queries.model.DayGeneratePlanModel;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.schedule.command.CreateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.PublishScheduleCommand;
import com.openhis.whale.booking.domain.schedule.service.ScheduleDomainService;
import com.openhis.whale.booking.domain.share.ScheduleTemplateKindConstants;
import com.openhis.whale.booking.domain.slot.command.ProvisionSlotBatchCommand;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.openhis.whale.booking.domain.template.service.TemplateQueryService;
import com.openhis.whale.booking.domain.template.service.WeekScheduleTemplateQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 从日/周模板按日生成 Schedule（2.17 · 2.25）。
 */
@Service
@RequiredArgsConstructor
public class GenerateFromTemplateSupport {

    private static final int MAX_GENERATE_DAYS = 62;

    private final TemplateQueryService templateQueryService;
    private final WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    private final CalendarExceptionQueryService calendarExceptionQueryService;
    private final PolicyQueryService policyQueryService;
    private final ScheduleDomainService scheduleDomainService;
    private final SlotDomainService slotDomainService;
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    private final ScheduleActorMasterDataValidator scheduleActorMasterDataValidator;
    private final BookingAuditSupport bookingAuditSupport;

    public GenerateFromTemplateResponse generate(GenerateFromTemplateRequest request, Long operatorId) {
        if (request.getRangeEnd().isBefore(request.getRangeStart())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "rangeEnd 不能早于 rangeStart");
        }
        long daySpan = request.getRangeEnd().toEpochDay() - request.getRangeStart().toEpochDay() + 1;
        if (daySpan > MAX_GENERATE_DAYS) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "单次生成区间不能超过 " + MAX_GENERATE_DAYS + " 天");
        }
        boolean autoPublish = Boolean.TRUE.equals(request.getAutoPublish());
        boolean autoProvision = Boolean.TRUE.equals(request.getAutoProvision());
        if (autoProvision && !autoPublish) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "autoProvision 须配合 autoPublish");
        }

        String kind = StringUtils.isBlank(request.getTemplateKind())
            ? ScheduleTemplateKindConstants.DAY : request.getTemplateKind().trim().toUpperCase();
        if (ScheduleTemplateKindConstants.WEEK.equals(kind)) {
            return this.generateFromWeekTemplate(request, operatorId, autoPublish, autoProvision);
        }
        return this.generateFromDayTemplate(request, operatorId, autoPublish, autoProvision);
    }

    private GenerateFromTemplateResponse generateFromDayTemplate(
        GenerateFromTemplateRequest request, Long operatorId, boolean autoPublish, boolean autoProvision) {
        if (request.getScheduleTemplateId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleTemplateId 不能为空");
        }
        ScheduleTemplate template = templateQueryService.loadScheduleTemplateById(request.getScheduleTemplateId());
        this.assertTemplateUsable(template, request.getOrganizationId());

        SlotProvisionTemplate slotTemplate = this.resolveSlotTemplate(template);
        ReleasePolicy releasePolicy = this.resolveReleasePolicy(template);

        GenerateFromTemplateResponse response = this.newResponse();
        LocalDate cursor = request.getRangeStart();
        while (!cursor.isAfter(request.getRangeEnd())) {
            this.generateDayWithT3(request, cursor, template, slotTemplate, releasePolicy, operatorId, autoPublish, autoProvision, response);
            cursor = cursor.plusDays(1);
        }
        this.assertGenerated(response);
        bookingAuditSupport.record("ScheduleTemplate", template.getId(), "GENERATE_FROM_TEMPLATE",
            String.valueOf(response.getGeneratedCount()));
        return response;
    }

    private GenerateFromTemplateResponse generateFromWeekTemplate(
        GenerateFromTemplateRequest request, Long operatorId, boolean autoPublish, boolean autoProvision) {
        if (request.getWeekScheduleTemplateId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "weekScheduleTemplateId 不能为空");
        }
        WeekScheduleTemplate weekTemplate = weekScheduleTemplateQueryService.loadWeekScheduleTemplateById(
            request.getWeekScheduleTemplateId());
        if (!Boolean.TRUE.equals(weekTemplate.getEnabled())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "周模板未启用");
        }
        if (weekTemplate.getOrganizationId() != null
            && !weekTemplate.getOrganizationId().equals(request.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "周模板与科室不匹配");
        }

        GenerateFromTemplateResponse response = this.newResponse();
        LocalDate cursor = request.getRangeStart();
        while (!cursor.isAfter(request.getRangeEnd())) {
            if (!this.isEffectiveOn(weekTemplate, cursor)) {
                response.setSkippedCount(response.getSkippedCount() + 1);
                cursor = cursor.plusDays(1);
                continue;
            }
            int weekday = cursor.getDayOfWeek().getValue();
            List<WeekScheduleTemplateItem> items = weekScheduleTemplateQueryService.listEnabledItemsForWeekday(
                weekTemplate.getId(), weekday);
            if (items.isEmpty()) {
                response.setSkippedCount(response.getSkippedCount() + 1);
            } else {
                for (WeekScheduleTemplateItem item : items) {
                    ScheduleTemplate dayTemplate = templateQueryService.loadScheduleTemplateById(
                        item.getDayScheduleTemplateId());
                    this.assertTemplateUsable(dayTemplate, request.getOrganizationId());
                    this.generateDayWithT3(
                        request, cursor, dayTemplate,
                        this.resolveSlotTemplate(dayTemplate),
                        this.resolveReleasePolicy(dayTemplate),
                        operatorId, autoPublish, autoProvision, response);
                }
            }
            cursor = cursor.plusDays(1);
        }
        this.assertGenerated(response);
        bookingAuditSupport.record("WeekScheduleTemplate", weekTemplate.getId(), "GENERATE_FROM_WEEK_TEMPLATE",
            String.valueOf(response.getGeneratedCount()));
        return response;
    }

    private void generateDayWithT3(
        GenerateFromTemplateRequest request,
        LocalDate cursor,
        ScheduleTemplate baseTemplate,
        SlotProvisionTemplate baseSlotTemplate,
        ReleasePolicy baseReleasePolicy,
        Long operatorId,
        boolean autoPublish,
        boolean autoProvision,
        GenerateFromTemplateResponse response) {
        DayGeneratePlanModel plan = calendarExceptionQueryService.resolveDayGeneratePlan(
            request.getOrganizationId(), cursor);
        if (plan.isSkipNormal()) {
            response.setSkippedCount(response.getSkippedCount() + 1);
        } else {
            ScheduleTemplate dayTemplate = baseTemplate;
            SlotProvisionTemplate daySlotTemplate = baseSlotTemplate;
            ReleasePolicy dayReleasePolicy = baseReleasePolicy;
            if (plan.getOverrideTemplateId() != null) {
                dayTemplate = templateQueryService.loadScheduleTemplateById(plan.getOverrideTemplateId());
                this.assertTemplateUsable(dayTemplate, request.getOrganizationId());
                daySlotTemplate = this.resolveSlotTemplate(dayTemplate);
                dayReleasePolicy = this.resolveReleasePolicy(dayTemplate);
            }
            this.generateForTemplateOnDay(
                dayTemplate, request, cursor, daySlotTemplate, dayReleasePolicy, operatorId,
                autoPublish, autoProvision, plan.isReducedCapacity(), response);
        }
        if (plan.isGenerateExtra() && plan.getExtraTemplateId() != null) {
            ScheduleTemplate extraTemplate = templateQueryService.loadScheduleTemplateById(plan.getExtraTemplateId());
            this.assertTemplateUsable(extraTemplate, request.getOrganizationId());
            this.generateForTemplateOnDay(
                extraTemplate, request, cursor,
                this.resolveSlotTemplate(extraTemplate),
                this.resolveReleasePolicy(extraTemplate),
                operatorId, autoPublish, autoProvision, false, response);
        }
    }

    private GenerateFromTemplateResponse newResponse() {
        GenerateFromTemplateResponse response = new GenerateFromTemplateResponse();
        response.setGeneratedCount(0);
        response.setSkippedCount(0);
        response.setProvisionedSlotCount(0);
        return response;
    }

    private void assertGenerated(GenerateFromTemplateResponse response) {
        if (response.getGeneratedCount() == 0) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "区间内无有效日期可生成（请检查模板有效期或 T3 日历例外）");
        }
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

    private void generateForTemplateOnDay(
        ScheduleTemplate dayTemplate,
        GenerateFromTemplateRequest request,
        LocalDate cursor,
        SlotProvisionTemplate slotTemplate,
        ReleasePolicy releasePolicy,
        Long operatorId,
        boolean autoPublish,
        boolean autoProvision,
        boolean reducedCapacity,
        GenerateFromTemplateResponse response) {
        if (!this.isEffectiveOn(dayTemplate, cursor)) {
            response.setSkippedCount(response.getSkippedCount() + 1);
            return;
        }
        Long scheduleId = this.createScheduleForDay(dayTemplate, request.getOrganizationId(), cursor, operatorId);
        response.getScheduleIds().add(scheduleId);
        response.setGeneratedCount(response.getGeneratedCount() + 1);

        if (autoPublish) {
            LocalDateTime dayStart = cursor.atStartOfDay();
            LocalDateTime dayEnd = cursor.atTime(23, 59, 59);
            this.publishSchedule(scheduleId, dayStart, dayEnd, operatorId);
            if (autoProvision) {
                int slotCount = this.provisionSlotsForDay(
                    scheduleId, request.getOrganizationId(), cursor, dayStart, dayEnd,
                    slotTemplate, releasePolicy, operatorId, reducedCapacity);
                response.setProvisionedSlotCount(response.getProvisionedSlotCount() + slotCount);
            }
        }
    }

    private void assertTemplateUsable(ScheduleTemplate template, Long organizationId) {
        if (!Boolean.TRUE.equals(template.getEnabled())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日程模板未启用");
        }
        if (template.getOrganizationId() != null && !template.getOrganizationId().equals(organizationId)) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板与科室不匹配");
        }
        if (template.getHealthcareServiceId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "模板未配置号源类型");
        }
    }

    private boolean isEffectiveOn(ScheduleTemplate template, LocalDate date) {
        if (template.getEffectiveStart() != null && date.isBefore(template.getEffectiveStart())) {
            return false;
        }
        if (template.getEffectiveEnd() != null && date.isAfter(template.getEffectiveEnd())) {
            return false;
        }
        return true;
    }

    private boolean isEffectiveOn(WeekScheduleTemplate template, LocalDate date) {
        if (template.getEffectiveStart() != null && date.isBefore(template.getEffectiveStart())) {
            return false;
        }
        if (template.getEffectiveEnd() != null && date.isAfter(template.getEffectiveEnd())) {
            return false;
        }
        return true;
    }

    private Long createScheduleForDay(
        ScheduleTemplate template, Long organizationId, LocalDate date, Long operatorId) {
        CreateScheduleCommand command = outpatientScheduleMapper.toCreateScheduleFromScheduleTemplate(
            template, organizationId, date, operatorId);
        scheduleActorMasterDataValidator.assertCreateCommandActorsReferenceValid(
            organizationId, command.getItem().getActors());
        return scheduleDomainService.createSchedule(command);
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

    private int provisionSlotsForDay(
        Long scheduleId,
        Long organizationId,
        LocalDate date,
        LocalDateTime dayStart,
        LocalDateTime dayEnd,
        SlotProvisionTemplate slotTemplate,
        ReleasePolicy releasePolicy,
        Long operatorId,
        boolean reducedCapacity) {
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
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "模板未关联 T1 或放号策略，无法自动放号");
        }
        if (reducedCapacity) {
            for (SlotTemplateRequest template : templates) {
                Integer capacity = template.getCapacity();
                int base = capacity == null || capacity <= 0 ? 1 : capacity;
                template.setCapacity(Math.max(1, base / 2));
            }
        }
        ProvisionSlotBatchRequest batchRequest = new ProvisionSlotBatchRequest();
        batchRequest.setScheduleId(scheduleId);
        batchRequest.setOrganizationId(organizationId);
        batchRequest.setReleasePolicyId(releasePolicy != null ? releasePolicy.getId() : null);
        batchRequest.setSlotTemplates(templates);
        ProvisionSlotBatchCommand command = outpatientScheduleMapper.toProvisionSlotBatchCommand(batchRequest, operatorId);
        List<Long> slotIds = slotDomainService.provisionSlotBatch(command);
        return slotIds == null ? 0 : slotIds.size();
    }
}
