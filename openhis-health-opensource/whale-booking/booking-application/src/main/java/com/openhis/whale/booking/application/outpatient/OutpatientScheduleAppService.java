package com.openhis.whale.booking.application.outpatient;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.request.CopyScheduleToNextWeekRequest;
import com.openhis.whale.booking.application.outpatient.request.CreateScheduleRequest;
import com.openhis.whale.booking.application.outpatient.request.DeactivateScheduleRequest;
import com.openhis.whale.booking.application.outpatient.request.GenerateFromTemplateRequest;
import com.openhis.whale.booking.application.outpatient.request.LoadScheduleFormInitRequest;
import com.openhis.whale.booking.application.outpatient.request.PageSchedulesByCalendarRequest;
import com.openhis.whale.booking.application.outpatient.request.PageSchedulesByMonthRequest;
import com.openhis.whale.booking.application.outpatient.request.PreviewPublishScheduleConflictsRequest;
import com.openhis.whale.booking.application.outpatient.request.ProvisionSlotBatchRequest;
import com.openhis.whale.booking.application.outpatient.request.PublishScheduleRequest;
import com.openhis.whale.booking.application.outpatient.request.ReactivateScheduleRequest;
import com.openhis.whale.booking.application.outpatient.request.ReleaseStopClinicPreviewFreezeRequest;
import com.openhis.whale.booking.application.outpatient.request.PreviewStopClinicImpactRequest;
import com.openhis.whale.booking.application.outpatient.request.TransferAppointmentsOnStopClinicRequest;
import com.openhis.whale.booking.application.outpatient.response.PreviewStopClinicImpactResponse;
import com.openhis.whale.booking.application.outpatient.response.TransferAppointmentsOnStopClinicResponse;
import com.openhis.whale.booking.application.outpatient.request.SubstitutePractitionerRequest;
import com.openhis.whale.booking.application.outpatient.request.SlotTemplateRequest;
import com.openhis.whale.booking.application.outpatient.request.UpdateScheduleDraftRequest;
import com.openhis.whale.booking.application.outpatient.response.CopyScheduleToNextWeekResponse;
import com.openhis.whale.booking.application.outpatient.response.CreateScheduleResponse;
import com.openhis.whale.booking.application.outpatient.response.GenerateFromTemplateResponse;
import com.openhis.whale.booking.application.outpatient.response.LoadScheduleFormInitResponse;
import com.openhis.whale.booking.application.outpatient.response.PageSchedulesByCalendarResponse;
import com.openhis.whale.booking.application.outpatient.response.PageSchedulesByMonthResponse;
import com.openhis.whale.booking.application.outpatient.response.PreviewPublishScheduleConflictsResponse;
import com.openhis.whale.booking.application.outpatient.response.ReactivateScheduleResponse;
import com.openhis.whale.booking.application.support.MonthCalendarRangeSupport;
import com.openhis.whale.booking.application.outpatient.response.SubstitutePractitionerResponse;
import com.openhis.whale.booking.application.support.BookingAuditSupport;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.ScheduleActorMasterDataValidator;
import com.openhis.whale.booking.application.support.StopClinicImpactService;
import com.openhis.whale.booking.application.outpatient.response.ProvisionSlotBatchResponse;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarCellDto;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarDayDto;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarMonthDayDto;
import com.openhis.whale.booking.application.outpatient.support.GenerateFromTemplateSupport;
import com.openhis.whale.booking.application.outpatient.support.SchedulePublishConflictSupport;
import com.openhis.whale.booking.application.outpatient.support.WeekTemplateFallbackSupport;
import com.openhis.whale.booking.contracts.dto.ScheduleFormMasterDataSnapshot;
import com.openhis.whale.booking.contracts.reader.HealthScheduleMasterDataReader;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.schedule.command.CreateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.AdjustPlanningHorizonCommand;
import com.openhis.whale.booking.domain.schedule.command.SubstitutePractitionerCommand;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.openhis.whale.booking.domain.schedule.command.DeactivateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.PublishScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.ReactivateScheduleCommand;
import com.openhis.whale.booking.domain.schedule.command.ReplaceScheduleActorsCommand;
import com.openhis.whale.booking.domain.schedule.command.UpdateScheduleCommentCommand;
import com.openhis.whale.booking.domain.schedule.command.UpdateServiceProfileCommand;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleDetailModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleDomainService;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.slot.command.ProvisionSlotBatchCommand;
import com.openhis.whale.booking.domain.slot.queries.FindSlotsByScheduleAndTimeRangeQuery;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 门诊排班应用服务（O 线，AS 2.x）。
 *
 * <p>职责边界：编排 Schedule 创建/发布/放号/停诊/模板生成；不维护 PractitionerRole 岗位主档。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/outpatientSchedule/{methodName}}。</p>
 * <p>依赖：主数据经 {@link HealthScheduleMasterDataReader} 读 health；写侧委托 Schedule/Slot 领域服务。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class OutpatientScheduleAppService {

    private final HealthScheduleMasterDataReader healthScheduleMasterDataReader;
    private final ScheduleDomainService scheduleDomainService;
    private final ScheduleQueryService scheduleQueryService;
    private final SlotDomainService slotDomainService;
    private final SlotQueryService slotQueryService;
    private final PolicyQueryService policyQueryService;
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    private final BookingOperatorResolver bookingOperatorResolver;
    private final BookingAuditSupport bookingAuditSupport;
    private final ScheduleActorMasterDataValidator scheduleActorMasterDataValidator;
    private final StopClinicImpactService stopClinicImpactService;
    private final GenerateFromTemplateSupport generateFromTemplateSupport;
    private final WeekTemplateFallbackSupport weekTemplateFallbackSupport;
    private final SchedulePublishConflictSupport schedulePublishConflictSupport;
    private final AppointmentQueryService appointmentQueryService;

    /**
     * 排班台初始化：科室、医师、诊室、号源类型下拉（2.1）。
     *
     * @param request 初始化请求；可为 null
     * @return 初始化数据
     */
    public LoadScheduleFormInitResponse loadScheduleFormInit(LoadScheduleFormInitRequest request) {
        Long organizationId = null;
        if (request != null) {
            organizationId = request.getOrganizationId();
        }
        // Pre：经 Reader/Writer/Client 读 health 主数据，booking-application 不直连 health-domain。
        ScheduleFormMasterDataSnapshot snapshot = healthScheduleMasterDataReader.loadOutpatientScheduleFormOptions(organizationId);
        // Asm：映射排班台初始化响应。
        return outpatientScheduleMapper.toLoadScheduleFormInitResponse(snapshot);
    }

    /**
     * 周历/月历格子数据（2.2）。
     *
     * @param request 日历查询条件
     * @return 按日分组的日程摘要
     */
    public PageSchedulesByCalendarResponse pageSchedulesByCalendar(@Valid PageSchedulesByCalendarRequest request) {
        // Pre：读侧按机构+区间+参与者过滤日程（无规划窗口草稿不占格）。
        List<ScheduleCalendarItemModel> schedules = scheduleQueryService.listSchedulesForCalendar(
            outpatientScheduleMapper.toCalendarScheduleQuery(request));
        // Asm：映射为按日分组的日历格子 DTO。
        PageSchedulesByCalendarResponse response = outpatientScheduleMapper.toPageSchedulesByCalendarResponse(
            schedules, request.getRangeStart(), request.getRangeEnd());
        // Pre：无 Schedule 时合并 TW 模板虚拟可约态。
        weekTemplateFallbackSupport.mergeCalendarFallback(response, request);
        this.enrichCalendarDisplay(response, request.getOrganizationId());
        return response;
    }

    /**
     * 月历格子与顶栏摘要（2.23）。
     *
     * @param request 月历查询条件
     * @return 月历网格与摘要
     */
    public PageSchedulesByMonthResponse pageSchedulesByMonth(@Valid PageSchedulesByMonthRequest request) {
        LocalDate[] monthRange = MonthCalendarRangeSupport.resolveMonthRange(request.getYearMonth());
        // Pre：月内日程 + 活跃预约计数。
        List<ScheduleCalendarItemModel> schedules = scheduleQueryService.listSchedulesForCalendar(
            outpatientScheduleMapper.toCalendarScheduleQuery(request));
        int bookedCount = appointmentQueryService.countActiveOutpatientBookingsInRange(
            request.getOrganizationId(), monthRange[0], monthRange[1]);
        PageSchedulesByMonthResponse response = outpatientScheduleMapper.toPageSchedulesByMonthResponse(
            schedules, request.getYearMonth(), bookedCount);
        weekTemplateFallbackSupport.mergeMonthFallback(response, request);
        this.enrichMonthDisplay(response, request.getOrganizationId());
        // Asm：返回月历网格与顶栏摘要。
        return response;
    }

    /**
     * 创建门诊日程草稿（2.3）。
     *
     * @param request 创建请求
     * @return 新日程主键
     */
    public CreateScheduleResponse createSchedule(@Valid CreateScheduleRequest request) {
        // Gate：写路径要求登录用户。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        scheduleActorMasterDataValidator.assertActorsReferenceValid(request.getOrganizationId(), request.getActors());
        // Cmd：落草稿 Schedule + actors。
        CreateScheduleCommand command = outpatientScheduleMapper.toCreateScheduleCommand(request, operatorId);
        Long scheduleId = scheduleDomainService.createSchedule(command);
        if (request.getPlanningHorizonStart() != null && request.getPlanningHorizonEnd() != null) {
            AdjustPlanningHorizonCommand horizonCommand = outpatientScheduleMapper.toAdjustPlanningHorizonCommand(
                scheduleId,
                request.getPlanningHorizonStart(),
                request.getPlanningHorizonEnd(),
                operatorId);
            scheduleDomainService.adjustPlanningHorizon(horizonCommand);
        }
        // Asm：返回新日程主键。
        CreateScheduleResponse response = new CreateScheduleResponse();
        response.setScheduleId(scheduleId);
        return response;
    }

    /**
     * 编辑草稿日程（2.6）：仅 draft 状态允许修改参与者/窗口/服务口径/说明。
     *
     * @param request 编辑请求
     */
    public void updateScheduleDraft(@Valid UpdateScheduleDraftRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Gate：仅草稿可编辑，避免已发布日程被应用层误改。
        this.requireDraftSchedule(request.getScheduleId());
        // Cmd：按入参非空字段逐项编排领域命令。
        if (request.getName() != null) {
            scheduleDomainService.updateName(request.getScheduleId(), request.getName());
        }
        if (request.getActors() != null && !request.getActors().isEmpty()) {
            ScheduleCalendarItemModel draft = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
            scheduleActorMasterDataValidator.assertActorsReferenceValid(draft.getOrganizationId(), request.getActors());
            ReplaceScheduleActorsCommand actorsCommand = outpatientScheduleMapper.toReplaceScheduleActorsCommand(request, operatorId);
            scheduleDomainService.replaceScheduleActors(actorsCommand);
        }
        if (request.getPlanningHorizonStart() != null && request.getPlanningHorizonEnd() != null) {
            AdjustPlanningHorizonCommand horizonCommand = outpatientScheduleMapper.toAdjustPlanningHorizonCommand(request, operatorId);
            scheduleDomainService.adjustPlanningHorizon(horizonCommand);
        }
        if (request.getServiceProfile() != null) {
            UpdateServiceProfileCommand profileCommand = outpatientScheduleMapper.toUpdateServiceProfileCommand(request, operatorId);
            scheduleDomainService.updateServiceProfile(profileCommand);
        }
        if (request.getCommentMarkdown() != null) {
            UpdateScheduleCommentCommand commentCommand = outpatientScheduleMapper.toUpdateScheduleCommentCommand(request, operatorId);
            scheduleDomainService.updateComment(commentCommand);
        }
    }

    /**
     * 发布日程（2.7）。
     *
     * @param request 发布请求
     */
    public void publishSchedule(@Valid PublishScheduleRequest request) {
        // Gate：写路径须登录；参与者主数据须有效。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleCalendarItemModel draft = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        scheduleActorMasterDataValidator.assertActorModelsReferenceValid(draft.getOrganizationId(), draft.getActors());
        // Pre：发布前冲突预览（BR-EXT-005/006）；未确认则拒绝发布。
        PreviewPublishScheduleConflictsResponse conflicts = schedulePublishConflictSupport.previewConflicts(
            request.getScheduleId(),
            draft.getOrganizationId(),
            request.getPlanningHorizonStart(),
            request.getPlanningHorizonEnd(),
            draft.getActors());
        if (Boolean.TRUE.equals(conflicts.getHasConflict())
            && !Boolean.TRUE.equals(request.getAcknowledgeConflictWarnings())) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "发布存在医师/诊室冲突，请先预览冲突并确认后继续");
        }
        // Cmd：草稿→启用并绑定规划窗口。
        PublishScheduleCommand command = outpatientScheduleMapper.toPublishScheduleCommand(request, operatorId);
        scheduleDomainService.publishSchedule(command);
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "PUBLISH_SCHEDULE", null);
    }

    /**
     * 发布前冲突预览（BR-EXT-005/006）。
     */
    public PreviewPublishScheduleConflictsResponse previewPublishScheduleConflicts(
        @Valid PreviewPublishScheduleConflictsRequest request) {
        // Gate：写路径须登录。
        bookingOperatorResolver.requireOperatorId();
        // Pre：加载草稿日程并预览医师/诊室冲突。
        ScheduleCalendarItemModel draft = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        // Asm：返回发布前冲突预览结果。
        return schedulePublishConflictSupport.previewConflicts(
            request.getScheduleId(),
            draft.getOrganizationId(),
            request.getPlanningHorizonStart(),
            request.getPlanningHorizonEnd(),
            draft.getActors());
    }

    /**
     * 批量放号（2.8）。P0 直接按请求模板出号；放号策略表 P1 再接入。
     *
     * @param request 放号请求
     * @return 新建号源主键列表
     */
    public ProvisionSlotBatchResponse provisionSlotBatch(@Valid ProvisionSlotBatchRequest request) {
        // Gate：写路径须登录操作人。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        List<SlotTemplateRequest> templates = request.getSlotTemplates();
        // Pre：未传模板时按放号策略 + 日程规划窗口自动切分时段。
        if (CollectionUtils.isEmpty(templates)) {
            ReleasePolicy policy = this.resolveReleasePolicy(request);
            ScheduleCalendarItemModel schedule = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
            templates = outpatientScheduleMapper.buildSlotTemplatesFromPolicy(
                policy, schedule.getPlanningHorizonStart(), schedule.getPlanningHorizonEnd());
            if (templates.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "无法从放号策略生成时段模板");
            }
            request.setSlotTemplates(templates);
        }
        // Cmd：批量落 slot 并维护容量/不重叠不变量。
        ProvisionSlotBatchCommand command = outpatientScheduleMapper.toProvisionSlotBatchCommand(request, operatorId);
        List<Long> slotIds = slotDomainService.provisionSlotBatch(command);
        String slotCountAudit;
        if (slotIds != null) {
            slotCountAudit = String.valueOf(slotIds.size());
        } else {
            slotCountAudit = "0";
        }
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "PROVISION_SLOT_BATCH",
            slotCountAudit);
        // Asm：返回生成数量与 slotId 列表。
        return outpatientScheduleMapper.toProvisionSlotBatchResponse(slotIds);
    }

    /**
     * 从 T2 日程模板按区间生成 Schedule（2.17）。
     *
     * @param request 生成请求
     * @return 生成结果
     */
    public GenerateFromTemplateResponse generateFromTemplate(@Valid GenerateFromTemplateRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：委托 support 按 T2 模板批量生成 Schedule。
        // Asm：返回模板生成结果摘要。
        return generateFromTemplateSupport.generate(request, operatorId);
    }

    /**
     * 复制日程到下一周（2.11）：按源周区间批量复制 Schedule，可选重新放号。
     */
    public CopyScheduleToNextWeekResponse copyScheduleToNextWeek(@Valid CopyScheduleToNextWeekRequest request) {
        // Gate：写路径须登录；源/目标周跨度须一致。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        this.assertCopyScheduleRangeValid(request);
        this.assertTargetWeekHasNoSchedules(request);

        long dayOffset = ChronoUnit.DAYS.between(request.getSourceRangeStart(), request.getTargetRangeStart());
        LocalDateTime sourceWindowStart = request.getSourceRangeStart().atStartOfDay();
        LocalDateTime sourceWindowEndExclusive = request.getSourceRangeEnd().plusDays(1).atStartOfDay();

        // Pre：加载源周内全部有效日程。
        PageSchedulesByCalendarRequest calendarQuery = new PageSchedulesByCalendarRequest();
        calendarQuery.setOrganizationId(request.getOrganizationId());
        calendarQuery.setRangeStart(request.getSourceRangeStart());
        calendarQuery.setRangeEnd(request.getSourceRangeEnd());
        List<ScheduleCalendarItemModel> sources = scheduleQueryService.listSchedulesForCalendar(
            outpatientScheduleMapper.toCalendarScheduleQuery(calendarQuery));

        int copiedCount = 0;
        int provisionedCount = 0;
        Long lastNewScheduleId = null;

        // Cmd：逐条复制 Schedule（窗口平移 dayOffset），可选复制号源；单条失败不阻断其余。
        for (ScheduleCalendarItemModel source : sources) {
            if (source == null || source.getId() == null) {
                continue;
            }
            // 跳过无规划窗口的草稿，避免复制无效日程。
            if (source.getPlanningHorizonStart() == null || source.getPlanningHorizonEnd() == null) {
                continue;
            }
            try {
                Long newScheduleId = this.copyScheduleWithOffset(source, operatorId, dayOffset);
                lastNewScheduleId = newScheduleId;
                copiedCount++;

                if (Boolean.TRUE.equals(request.getCopySlotProvision())) {
                    try {
                        provisionedCount += this.copySlotProvisionForSchedule(
                            source,
                            newScheduleId,
                            request.getOrganizationId(),
                            sourceWindowStart,
                            sourceWindowEndExclusive,
                            dayOffset);
                    } catch (Exception ex) {
                        log.warn("复制日程 {} 的号源失败，日程已保留: {}", source.getId(), ex.getMessage(), ex);
                    }
                }
            } catch (Exception ex) {
                log.warn("复制日程 {} 失败: {}", source.getId(), ex.getMessage(), ex);
            }
        }

        // Asm：汇总复制与放号数量。
        CopyScheduleToNextWeekResponse response = new CopyScheduleToNextWeekResponse();
        response.setCopiedScheduleCount(copiedCount);
        response.setProvisionedSlotCount(provisionedCount);
        response.setNewScheduleId(lastNewScheduleId);
        return response;
    }

    private void assertCopyScheduleRangeValid(CopyScheduleToNextWeekRequest request) {
        if (request.getSourceRangeEnd().isBefore(request.getSourceRangeStart())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "sourceRangeEnd 不能早于 sourceRangeStart");
        }
        if (request.getTargetRangeEnd().isBefore(request.getTargetRangeStart())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "targetRangeEnd 不能早于 targetRangeStart");
        }
        long sourceDays = ChronoUnit.DAYS.between(request.getSourceRangeStart(), request.getSourceRangeEnd());
        long targetDays = ChronoUnit.DAYS.between(request.getTargetRangeStart(), request.getTargetRangeEnd());
        if (sourceDays != targetDays) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "源周与目标周跨度须一致");
        }
    }

    /** 目标周（次周）已有正式日程时不允许复制，避免重复排班。 */
    private void assertTargetWeekHasNoSchedules(CopyScheduleToNextWeekRequest request) {
        PageSchedulesByCalendarRequest targetQuery = new PageSchedulesByCalendarRequest();
        targetQuery.setOrganizationId(request.getOrganizationId());
        targetQuery.setRangeStart(request.getTargetRangeStart());
        targetQuery.setRangeEnd(request.getTargetRangeEnd());
        List<ScheduleCalendarItemModel> existing = scheduleQueryService.listSchedulesForCalendar(
            outpatientScheduleMapper.toCalendarScheduleQuery(targetQuery));
        boolean hasSchedules = existing.stream().anyMatch(
            schedule -> schedule != null
                && schedule.getId() != null
                && schedule.getPlanningHorizonStart() != null
                && schedule.getPlanningHorizonEnd() != null);
        if (hasSchedules) {
            throw new BusinessException(
                ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT,
                "目标周已有排班，无法复制。请先清空或调整目标周日程后再试");
        }
    }

    private Long copyScheduleWithOffset(ScheduleCalendarItemModel source, Long operatorId, long dayOffset) {
        String copyName;
        if (source.getName() == null) {
            copyName = "复制日程";
        } else {
            copyName = source.getName() + " (复制)";
        }
        CreateScheduleCommand createCommand = outpatientScheduleMapper.toCopyScheduleCommand(
            source, source.getOrganizationId(), copyName, operatorId);
        Long newScheduleId = scheduleDomainService.createSchedule(createCommand);

        LocalDateTime newStart = source.getPlanningHorizonStart().plusDays(dayOffset);
        LocalDateTime newEnd = source.getPlanningHorizonEnd().plusDays(dayOffset);
        AdjustPlanningHorizonCommand horizonCommand = outpatientScheduleMapper.toAdjustPlanningHorizonCommand(
            newScheduleId, newStart, newEnd, operatorId);
        scheduleDomainService.adjustPlanningHorizon(horizonCommand);

        if (ScheduleStatus.ACTIVE.getCode().equals(source.getStatusEnum())) {
            PublishScheduleCommand publishCommand = new PublishScheduleCommand();
            publishCommand.setOperatorId(operatorId);
            PublishScheduleCommand.PublishScheduleItemCommand item = new PublishScheduleCommand.PublishScheduleItemCommand();
            item.setScheduleId(newScheduleId);
            item.setPlanningHorizonStart(newStart);
            item.setPlanningHorizonEnd(newEnd);
            publishCommand.setItem(item);
            scheduleDomainService.publishSchedule(publishCommand);
        }
        return newScheduleId;
    }

    private int copySlotProvisionForSchedule(
        ScheduleCalendarItemModel source,
        Long newScheduleId,
        Long organizationId,
        LocalDateTime sourceWindowStart,
        LocalDateTime sourceWindowEndExclusive,
        long dayOffset) {
        if (!ScheduleStatus.ACTIVE.getCode().equals(source.getStatusEnum())) {
            return 0;
        }
        LocalDateTime queryEnd = sourceWindowEndExclusive.minusNanos(1);
        if (queryEnd.isBefore(sourceWindowStart)) {
            return 0;
        }
        FindSlotsByScheduleAndTimeRangeQuery slotQuery = new FindSlotsByScheduleAndTimeRangeQuery();
        slotQuery.setScheduleId(source.getId());
        slotQuery.setStartTime(sourceWindowStart);
        slotQuery.setEndTime(queryEnd);
        List<SlotDetailModel> sourceSlots = slotQueryService.findSlotsByScheduleAndTimeRange(slotQuery);
        List<SlotTemplateRequest> templates = outpatientScheduleMapper.buildSlotTemplatesFromSourceSlots(sourceSlots, dayOffset);
        if (templates.isEmpty()) {
            return 0;
        }
        ProvisionSlotBatchRequest batchRequest = new ProvisionSlotBatchRequest();
        batchRequest.setScheduleId(newScheduleId);
        batchRequest.setOrganizationId(organizationId);
        batchRequest.setSlotTemplates(templates);
        ProvisionSlotBatchResponse batchResponse = this.provisionSlotBatch(batchRequest);
        if (batchResponse.getGeneratedCount() == null) {
            return 0;
        }
        return batchResponse.getGeneratedCount();
    }

    /**
     * 停诊影响预览（2.18）。
     *
     * @param request 预览请求
     * @return 影响摘要与映射表
     */
    public PreviewStopClinicImpactResponse previewStopClinicImpact(@Valid PreviewStopClinicImpactRequest request) {
        // Gate：须登录；委托 StopClinicImpactService 聚合预约/号源影响并可选冻结 slot。
        bookingOperatorResolver.requireOperatorId();
        // Asm：返回停诊影响预览摘要与映射表。
        return stopClinicImpactService.previewImpact(request);
    }

    /**
     * 释放停诊预览冻结（向导取消，TC-FRZ-01 / BR-EXT-004）。
     *
     * @param request 机构、scheduleId、待释放 slotId 列表
     */
    public void releaseStopClinicPreviewFreeze(@Valid ReleaseStopClinicPreviewFreezeRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：TTL 冻结记录软删，恢复 slot 可约。
        stopClinicImpactService.releasePreviewFreeze(
            request.getOrganizationId(),
            request.getScheduleId(),
            request.getSlotIds(),
            operatorId);
    }

    /**
     * 批量转交/取消预约并停诊（IF-2.19）。
     *
     * @param request 停诊执行方案
     * @return 转交/取消统计
     */
    public TransferAppointmentsOnStopClinicResponse transferAppointmentsOnStopClinic(
        @Valid TransferAppointmentsOnStopClinicRequest request) {
        // Gate：写路径须登录。
        bookingOperatorResolver.requireOperatorId();
        // Cmd：按院点策略处置未处理预约后停用 schedule。
        // Asm：返回转交/取消统计。
        return stopClinicImpactService.transferAndStop(request);
    }

    /**
     * 替诊（IF-2.21 · CHG-SCH-08）：设置或撤销顶班医师，不迁移预约。
     *
     * @param request scheduleId、替诊医师（null 表示撤销）
     * @return 原/替诊医师信息
     */
    public SubstitutePractitionerResponse substitutePractitioner(@Valid SubstitutePractitionerRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Pre：加载日程并解析原出诊医师。
        ScheduleCalendarItemModel schedule = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        Long originalPractitionerId = this.resolvePractitionerId(schedule.getActors());
        if (originalPractitionerId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日程无医师参与者，无法替诊");
        }
        // Gate：替诊医师不得与原医师相同；须通过主数据校验。
        if (request.getSubstitutePractitionerId() != null) {
            if (request.getSubstitutePractitionerId().equals(originalPractitionerId)) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "替诊医师不能与原出诊医师相同");
            }
            scheduleActorMasterDataValidator.assertPractitionerReferenceValid(
                schedule.getOrganizationId(), request.getSubstitutePractitionerId());
        }
        SubstitutePractitionerCommand command = outpatientScheduleMapper.toSubstitutePractitionerCommand(request, operatorId);
        // Cmd：设置或撤销替诊医师。
        scheduleDomainService.substitutePractitioner(command);
        String auditAction;
        if (request.getSubstitutePractitionerId() == null) {
            auditAction = "CLEAR_SUBSTITUTE";
        } else {
            auditAction = "SUBSTITUTE_PRACTITIONER";
        }
        String auditDetail;
        if (request.getSubstitutePractitionerId() == null) {
            auditDetail = null;
        } else {
            auditDetail = String.valueOf(request.getSubstitutePractitionerId());
        }
        bookingAuditSupport.record(
            "Schedule",
            request.getScheduleId(),
            auditAction,
            auditDetail);
        SubstitutePractitionerResponse response = new SubstitutePractitionerResponse();
        response.setScheduleId(request.getScheduleId());
        response.setOriginalPractitionerId(originalPractitionerId);
        response.setSubstitutePractitionerId(request.getSubstitutePractitionerId());
        response.setSubstituteReason(request.getReason());
        response.setCleared(request.getSubstitutePractitionerId() == null);
        // Asm：返回替诊结果快照。
        return response;
    }

    /**
     * 停诊：停用日程（IF-2.9）。
     *
     * @param request 停用原因
     */
    public void deactivateSchedule(@Valid DeactivateScheduleRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：active/inactive 状态迁移；不自动处置预约（停诊向导单独 IF）。
        DeactivateScheduleCommand command = outpatientScheduleMapper.toDeactivateScheduleCommand(request, operatorId);
        scheduleDomainService.deactivateSchedule(command);
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "DEACTIVATE_SCHEDULE", request.getReason());
    }

    /**
     * 恢复开诊（CHG-SCH-10 / BR-EXT-012）：inactive → active，不自动恢复号源。
     *
     * @param request 日程主键
     * @return 提示需重新放号
     */
    public ReactivateScheduleResponse reactivateSchedule(@Valid ReactivateScheduleRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：inactive → active 恢复开诊（不自动恢复号源）。
        ReactivateScheduleCommand command = outpatientScheduleMapper.toReactivateScheduleCommand(request, operatorId);
        scheduleDomainService.reactivateSchedule(command);
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "REACTIVATE_SCHEDULE", null);
        // Asm：返回需重新放号提示。
        ReactivateScheduleResponse response = new ReactivateScheduleResponse();
        response.setScheduleId(request.getScheduleId());
        response.setNeedReprovision(true);
        return response;
    }

    /**
     * 断言日程处于草稿态，供编辑门控使用。
     */
    private void requireDraftSchedule(Long scheduleId) {
        ScheduleDetailModel detail = scheduleQueryService.loadScheduleById(scheduleId);
        if (!ScheduleStatus.DRAFT.getCode().equals(detail.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "仅草稿状态允许编辑");
        }
    }

    private ReleasePolicy resolveReleasePolicy(ProvisionSlotBatchRequest request) {
        ReleasePolicy policy = null;
        if (request.getReleasePolicyId() != null) {
            policy = policyQueryService.loadReleasePolicyById(request.getReleasePolicyId());
        }
        if (policy == null || Boolean.TRUE.equals(policy.getIsDeleted())) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "放号策略不存在");
        }
        return policy;
    }

    private void enrichCalendarDisplay(PageSchedulesByCalendarResponse response, Long organizationId) {
        List<ScheduleCalendarCellDto> cells = this.collectCalendarCells(response);
        if (cells.isEmpty()) {
            return;
        }
        ScheduleFormMasterDataSnapshot snapshot = healthScheduleMasterDataReader.loadOutpatientScheduleFormOptions(organizationId);
        outpatientScheduleMapper.enrichCalendarActorLabels(cells, snapshot);
        outpatientScheduleMapper.enrichSubstituteDisplay(cells, snapshot.getPractitioners());
    }

    private void enrichMonthDisplay(PageSchedulesByMonthResponse response, Long organizationId) {
        if (response == null || response.getDays() == null) {
            return;
        }
        List<ScheduleCalendarCellDto> cells = new ArrayList<>();
        for (ScheduleCalendarMonthDayDto day : response.getDays()) {
            if (day != null && day.getSchedules() != null) {
                cells.addAll(day.getSchedules());
            }
        }
        if (cells.isEmpty()) {
            return;
        }
        ScheduleFormMasterDataSnapshot snapshot = healthScheduleMasterDataReader.loadOutpatientScheduleFormOptions(organizationId);
        outpatientScheduleMapper.enrichCalendarActorLabels(cells, snapshot);
        outpatientScheduleMapper.enrichSubstituteDisplay(cells, snapshot.getPractitioners());
    }

    private List<ScheduleCalendarCellDto> collectCalendarCells(PageSchedulesByCalendarResponse response) {
        if (response == null || response.getDays() == null) {
            return List.of();
        }
        List<ScheduleCalendarCellDto> cells = new ArrayList<>();
        for (ScheduleCalendarDayDto day : response.getDays()) {
            if (day != null && day.getSchedules() != null) {
                cells.addAll(day.getSchedules());
            }
        }
        return cells;
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
}
