/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.support;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.request.GenerateFromTemplateRequest;
import com.openhis.whale.booking.application.outpatient.request.ProvisionSlotBatchRequest;
import com.openhis.whale.booking.application.outpatient.request.SlotTemplateRequest;
import com.openhis.whale.booking.application.outpatient.response.GenerateFromTemplateResponse;
import com.openhis.whale.booking.application.share.BookingAuditSupport;
import com.openhis.whale.booking.application.share.ScheduleActorMasterDataValidator;
import com.openhis.whale.booking.domain.calendarexception.queries.model.DayGeneratePlanModel;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.schedule.command.AdjustPlanningHorizonCommand;
import com.openhis.whale.booking.domain.schedule.command.CreateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.PublishScheduleCommand;
import com.openhis.whale.booking.domain.schedule.constants.GenerateScheduleConstants;
import com.openhis.whale.booking.domain.schedule.service.ScheduleDomainService;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
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
 * 从日/周排班模板按区间生成正式 Schedule 的应用编排（AS 2.17 · 2.25）。
 *
 * <p>写路径经 {@link ScheduleDomainService}；读 T3 日历例外经 {@link CalendarExceptionQueryService}。
 * 生成草稿时须写入当日规划窗口，否则读侧过滤无窗口草稿导致周历不可见、无法手动发布。</p>
 */
@Service
@RequiredArgsConstructor
public class GenerateFromTemplateSupport {

    private final TemplateQueryService templateQueryService;
    private final WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    private final CalendarExceptionQueryService calendarExceptionQueryService;
    private final PolicyQueryService policyQueryService;
    private final ScheduleDomainService scheduleDomainService;
    private final SlotDomainService slotDomainService;
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    private final ScheduleActorMasterDataValidator scheduleActorMasterDataValidator;
    private final BookingAuditSupport bookingAuditSupport;
    private final ScheduleQueryService scheduleQueryService;

    /**
     * 按日/周模板与日期区间批量生成 Schedule（可仅草稿、可自动发布/放号）。
     *
     * @param request    科室、模板粒度、区间与自动发布/放号开关
     * @param operatorId 操作人主键
     * @return 生成/跳过/放号计数及 scheduleId 列表
     * @throws BusinessException 区间非法、模板不可用或区间内无可生成日时
     */
    public GenerateFromTemplateResponse generate(GenerateFromTemplateRequest request, Long operatorId) {
        // Gate：区间倒序则无法逐日遍历
        if (request.getRangeEnd().isBefore(request.getRangeStart())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "rangeEnd 不能早于 rangeStart");
        }
        long daySpan = request.getRangeEnd().toEpochDay() - request.getRangeStart().toEpochDay() + 1;
        // Gate：单次批量过大将拖垮写侧与日历刷新
        if (daySpan > GenerateScheduleConstants.MAX_GENERATE_DAYS) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "单次生成区间不能超过 " + GenerateScheduleConstants.MAX_GENERATE_DAYS + " 天");
        }
        boolean autoPublish = Boolean.TRUE.equals(request.getAutoPublish());
        boolean autoProvision = Boolean.TRUE.equals(request.getAutoProvision());
        // Gate：未发布日程无法放号，避免产生无号源却标记已放号的中间态
        if (autoProvision && !autoPublish) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "autoProvision 须配合 autoPublish");
        }

        String kind;
        if (StringUtils.isBlank(request.getTemplateKind())) {
            // 未传粒度时默认按日模板逐日生成
            kind = ScheduleTemplateKindConstants.DAY;
        } else {
            kind = request.getTemplateKind().trim().toUpperCase();
        }
        if (ScheduleTemplateKindConstants.WEEK.equals(kind)) {
            return this.generateFromWeekTemplate(request, operatorId, autoPublish, autoProvision);
        }
        return this.generateFromDayTemplate(request, operatorId, autoPublish, autoProvision);
    }

    /**
     * 按单一 T2 日模板在区间内逐日生成。
     *
     * @param request        生成请求
     * @param operatorId     操作人
     * @param autoPublish    是否生成后立即发布
     * @param autoProvision  是否在发布后按 T1/策略放号
     * @return 生成结果
     */
    private GenerateFromTemplateResponse generateFromDayTemplate(
        GenerateFromTemplateRequest request, Long operatorId, boolean autoPublish, boolean autoProvision) {
        if (request.getScheduleTemplateId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleTemplateId 不能为空");
        }
        // Pre：加载日模板及关联 T1、放号策略
        ScheduleTemplate template = templateQueryService.loadScheduleTemplateById(request.getScheduleTemplateId());
        this.assertTemplateUsable(template, request.getOrganizationId());

        SlotProvisionTemplate slotTemplate = this.resolveSlotTemplate(template);
        ReleasePolicy releasePolicy = this.resolveReleasePolicy(template);

        GenerateFromTemplateResponse response = this.newResponse();
        LocalDate cursor = request.getRangeStart();
        // Cmd：逐日套用 T3 例外计划后物化 Schedule
        while (!cursor.isAfter(request.getRangeEnd())) {
            this.generateDayWithT3(
                request, cursor, template, slotTemplate, releasePolicy, operatorId, autoPublish, autoProvision, response);
            cursor = cursor.plusDays(1);
        }
        this.assertGenerated(response);
        bookingAuditSupport.record("ScheduleTemplate", template.getId(), "GENERATE_FROM_TEMPLATE",
            String.valueOf(response.getGeneratedCount()));
        return response;
    }

    /**
     * 按 TW 周模板在区间内按 weekday 映射 T2 后逐日生成。
     *
     * @param request        生成请求
     * @param operatorId     操作人
     * @param autoPublish    是否生成后立即发布
     * @param autoProvision  是否在发布后按 T1/策略放号
     * @return 生成结果
     */
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
        // Gate：周模板归属科室须与本次生成科室一致
        if (weekTemplate.getOrganizationId() != null
            && !weekTemplate.getOrganizationId().equals(request.getOrganizationId())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "周模板与科室不匹配");
        }

        GenerateFromTemplateResponse response = this.newResponse();
        LocalDate cursor = request.getRangeStart();
        while (!cursor.isAfter(request.getRangeEnd())) {
            int weekday = cursor.getDayOfWeek().getValue();
            List<WeekScheduleTemplateItem> items = weekScheduleTemplateQueryService.listEnabledItemsForWeekday(
                weekTemplate.getId(), weekday);
            if (items.isEmpty()) {
                // 该 weekday 未配置日排班：跳过本日，不视为失败
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

    /**
     * 单日生成：先解析 T3 日历例外，再按正常/加班模板物化 Schedule。
     *
     * @param request         生成请求（含科室与自动发布开关）
     * @param cursor          当前日历日
     * @param baseTemplate    默认日排班模板
     * @param baseSlotTemplate  默认 T1 放号时段
     * @param baseReleasePolicy 默认放号策略
     * @param operatorId      操作人
     * @param autoPublish     是否自动发布
     * @param autoProvision   是否自动放号
     * @param response        累计生成结果的响应对象
     */
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
            // T3 停诊日：不生成常规模板对应的 Schedule
            response.setSkippedCount(response.getSkippedCount() + 1);
        } else {
            ScheduleTemplate dayTemplate = baseTemplate;
            SlotProvisionTemplate daySlotTemplate = baseSlotTemplate;
            ReleasePolicy dayReleasePolicy = baseReleasePolicy;
            if (plan.getOverrideTemplateId() != null) {
                // T3 指定替班日模板：覆盖默认 T2
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
            // T3 加班日：在常规模板之外额外生成一条 Schedule
            ScheduleTemplate extraTemplate = templateQueryService.loadScheduleTemplateById(plan.getExtraTemplateId());
            this.assertTemplateUsable(extraTemplate, request.getOrganizationId());
            this.generateForTemplateOnDay(
                extraTemplate, request, cursor,
                this.resolveSlotTemplate(extraTemplate),
                this.resolveReleasePolicy(extraTemplate),
                operatorId, autoPublish, autoProvision, false, response);
        }
    }

    /**
     * 初始化生成结果计数器（生成/跳过/放号均为 0）。
     *
     * @return 空的结果对象
     */
    private GenerateFromTemplateResponse newResponse() {
        GenerateFromTemplateResponse response = new GenerateFromTemplateResponse();
        response.setGeneratedCount(0);
        response.setSkippedCount(0);
        response.setProvisionedSlotCount(0);
        return response;
    }

    /**
     * 区间内至少生成一条 Schedule，否则前端无法感知「已执行但全跳过」。
     *
     * @param response 本次生成累计结果
     * @throws BusinessException {@code generatedCount == 0} 时
     */
    private void assertGenerated(GenerateFromTemplateResponse response) {
        if (response.getGeneratedCount() == 0) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "区间内无有效日期可生成（请检查 T3 日历例外或周模板 weekday 配置）");
        }
    }

    /**
     * 按日模板关联主键加载 T1 放号时段；未关联时返回 {@code null}。
     *
     * @param template 日排班模板
     * @return T1 实体或 {@code null}
     */
    private SlotProvisionTemplate resolveSlotTemplate(ScheduleTemplate template) {
        if (template.getSlotProvisionTemplateId() == null) {
            return null;
        }
        return templateQueryService.loadSlotProvisionTemplateById(template.getSlotProvisionTemplateId());
    }

    /**
     * 按日模板默认放号策略主键加载策略；未配置或已软删时返回 {@code null}。
     *
     * @param template 日排班模板
     * @return 放号策略或 {@code null}
     */
    private ReleasePolicy resolveReleasePolicy(ScheduleTemplate template) {
        if (template.getDefaultReleasePolicyId() == null) {
            return null;
        }
        ReleasePolicy policy = policyQueryService.loadReleasePolicyById(template.getDefaultReleasePolicyId());
        if (policy == null || Boolean.TRUE.equals(policy.getIsDeleted())) {
            // 策略已软删：视为未配置，自动放号时走 T1
            return null;
        }
        return policy;
    }

    /**
     * 在指定日期为单条日模板创建 Schedule，并按需发布/放号。
     *
     * @param dayTemplate     日排班模板
     * @param request         生成请求
     * @param cursor          目标日期
     * @param slotTemplate    T1 放号时段（可空）
     * @param releasePolicy   放号策略（可空）
     * @param operatorId      操作人
     * @param autoPublish     是否自动发布
     * @param autoProvision   是否自动放号
     * @param reducedCapacity T3 减容日是否减半容量
     * @param response        累计生成结果
     */
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
        if (scheduleQueryService.existsNonRetiredScheduleForTemplateOnDay(
            request.getOrganizationId(), dayTemplate.getId(), cursor)) {
            response.setSkippedCount(response.getSkippedCount() + 1);
            return;
        }
        Long scheduleId = this.createScheduleForDay(
            dayTemplate, request.getOrganizationId(), cursor, slotTemplate, operatorId);
        response.getScheduleIds().add(scheduleId);
        response.setGeneratedCount(response.getGeneratedCount() + 1);

        if (autoPublish) {
            LocalDateTime[] horizon = outpatientScheduleMapper.resolvePlanningHorizonFromT1(slotTemplate, cursor);
            this.publishSchedule(scheduleId, horizon[0], horizon[1], operatorId);
            if (autoProvision) {
                int slotCount = this.provisionSlotsForDay(
                    scheduleId, request.getOrganizationId(), cursor, horizon[0], horizon[1],
                    slotTemplate, releasePolicy, operatorId, reducedCapacity);
                response.setProvisionedSlotCount(response.getProvisionedSlotCount() + slotCount);
            }
        }
    }

    /**
     * 断言日排班模板可用于本次生成（启用、科室一致、已配置号源类型）。
     *
     * @param template       日排班模板
     * @param organizationId 本次生成所属科室
     * @throws BusinessException 模板不可用或科室不匹配时
     */
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

    /**
     * 创建草稿 Schedule 并绑定当日规划窗口（与「新建日程」口径一致，保证周历可见）。
     *
     * @param template       日排班模板
     * @param organizationId 科室主键
     * @param date           目标日期
     * @param operatorId     操作人
     * @return 新 Schedule 主键
     */
    private Long createScheduleForDay(
        ScheduleTemplate template,
        Long organizationId,
        LocalDate date,
        SlotProvisionTemplate slotTemplate,
        Long operatorId) {
        CreateScheduleCommand command = outpatientScheduleMapper.toCreateScheduleFromScheduleTemplate(
            template, organizationId, date, operatorId);
        scheduleActorMasterDataValidator.assertCreateCommandActorsReferenceValid(
            organizationId, command.getItem().getActors());
        Long scheduleId = scheduleDomainService.createSchedule(command);
        LocalDateTime[] horizon = outpatientScheduleMapper.resolvePlanningHorizonFromT1(slotTemplate, date);
        AdjustPlanningHorizonCommand horizonCommand = outpatientScheduleMapper.toAdjustPlanningHorizonCommand(
            scheduleId, horizon[0], horizon[1], operatorId);
        scheduleDomainService.adjustPlanningHorizon(horizonCommand);
        return scheduleId;
    }

    /**
     * 将草稿 Schedule 发布为 active 并写入规划窗口。
     *
     * @param scheduleId 日程主键
     * @param start        规划窗口起
     * @param end          规划窗口止
     * @param operatorId   操作人
     */
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

    /**
     * 按 T1 或放号策略为已发布 Schedule 批量放号。
     *
     * @param scheduleId      日程主键
     * @param organizationId  科室主键
     * @param date            目标日期
     * @param dayStart        当日 00:00
     * @param dayEnd          当日 23:59:59
     * @param slotTemplate    T1 放号时段（优先）
     * @param releasePolicy   放号策略（T1 缺失时回落）
     * @param operatorId      操作人
     * @param reducedCapacity 是否减半各时段容量
     * @return 新建号源数量
     * @throws BusinessException T1 与策略均无法切分时段时
     */
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
                // T1 未配置时回落到放号策略切分时段
                templates = outpatientScheduleMapper.buildSlotTemplatesFromPolicy(releasePolicy, dayStart, dayEnd);
            }
        }
        if (templates == null || templates.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "模板未关联 T1 或放号策略，无法自动放号");
        }
        if (reducedCapacity) {
            // T3 减容日：各时段容量减半，至少保留 1
            for (SlotTemplateRequest template : templates) {
                Integer capacity = template.getCapacity();
                int base;
                if (capacity == null || capacity <= 0) {
                    base = 1;
                } else {
                    base = capacity;
                }
                template.setCapacity(Math.max(1, base / 2));
            }
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
        List<Long> slotIds = slotDomainService.provisionSlotBatch(command);
        if (slotIds == null) {
            return 0;
        }
        return slotIds.size();
    }
}
