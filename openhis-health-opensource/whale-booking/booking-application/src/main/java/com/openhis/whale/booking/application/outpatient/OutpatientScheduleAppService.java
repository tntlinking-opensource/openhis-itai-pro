package com.openhis.whale.booking.application.outpatient;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.whale.booking.application.outpatient.mapper.OutpatientScheduleMapper;
import com.openhis.whale.booking.application.outpatient.request.CopyScheduleToNextWeekRequest;
import com.openhis.whale.booking.application.outpatient.request.CreateScheduleRequest;
import com.openhis.whale.booking.application.outpatient.request.DeactivateScheduleRequest;
import com.openhis.whale.booking.application.outpatient.request.GenerateDepartmentFromPractitionerRolesRequest;
import com.openhis.whale.booking.application.outpatient.request.GenerateFromPractitionerTemplateRequest;
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
import com.openhis.whale.booking.application.outpatient.response.GenerateDepartmentFromPractitionerRolesResponse;
import com.openhis.whale.booking.application.outpatient.response.GenerateFromPractitionerTemplateResponse;
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
import com.openhis.whale.booking.application.outpatient.response.DepartmentPractitionerGenerateItemDto;
import com.openhis.whale.booking.application.outpatient.response.ProvisionSlotBatchResponse;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarCellDto;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarDayDto;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarMonthDayDto;
import com.openhis.whale.booking.application.outpatient.support.GenerateFromTemplateSupport;
import com.openhis.whale.booking.application.outpatient.support.PractitionerScheduleGenerateSupport;
import com.openhis.whale.booking.application.outpatient.support.SchedulePublishConflictSupport;
import com.openhis.whale.booking.application.outpatient.support.WeekTemplateFallbackSupport;
import com.openhis.whale.booking.contracts.dto.MasterDataOption;
import com.openhis.whale.booking.contracts.dto.ScheduleFormMasterDataSnapshot;
import com.openhis.whale.booking.contracts.port.BookingScheduleMasterDataPort;
import com.openhis.whale.booking.domain.appointment.service.AppointmentQueryService;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import com.openhis.whale.booking.domain.schedule.command.AdjustPlanningHorizonCommand;
import com.openhis.whale.booking.domain.schedule.command.CreateScheduleCommand;
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
import com.openhis.whale.booking.domain.slot.service.SlotDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门诊排班应用服务（O 线）。
 *
 * <p>职责边界：编排 Schedule 创建/发布/放号；不维护 PractitionerRole 岗位主档。</p>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/outpatientSchedule/{methodName}</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class OutpatientScheduleAppService {

    private final BookingScheduleMasterDataPort bookingScheduleMasterDataPort;
    private final ScheduleDomainService scheduleDomainService;
    private final ScheduleQueryService scheduleQueryService;
    private final SlotDomainService slotDomainService;
    private final PolicyQueryService policyQueryService;
    private final OutpatientScheduleMapper outpatientScheduleMapper;
    private final BookingOperatorResolver bookingOperatorResolver;
    private final BookingAuditSupport bookingAuditSupport;
    private final ScheduleActorMasterDataValidator scheduleActorMasterDataValidator;
    private final StopClinicImpactService stopClinicImpactService;
    private final GenerateFromTemplateSupport generateFromTemplateSupport;
    private final WeekTemplateFallbackSupport weekTemplateFallbackSupport;
    private final PractitionerScheduleGenerateSupport practitionerScheduleGenerateSupport;
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
        // Pre：经 Port 读 health 主数据，booking-application 不直连 health-domain。
        return outpatientScheduleMapper.toLoadScheduleFormInitResponse(
            bookingScheduleMasterDataPort.loadOutpatientScheduleFormOptions(organizationId));
    }

    /**
     * 周历/月历格子数据（2.2）。
     *
     * @param request 日历查询条件
     * @return 按日分组的日程摘要
     */
    public PageSchedulesByCalendarResponse pageSchedulesByCalendar(@Valid PageSchedulesByCalendarRequest request) {
        List<ScheduleCalendarItemModel> schedules = scheduleQueryService.listSchedulesForCalendar(
            outpatientScheduleMapper.toCalendarScheduleQuery(request));
        PageSchedulesByCalendarResponse response = outpatientScheduleMapper.toPageSchedulesByCalendarResponse(
            schedules, request.getRangeStart(), request.getRangeEnd());
        this.enrichCalendarSubstituteDisplay(response, request.getOrganizationId());
        weekTemplateFallbackSupport.mergeCalendarFallback(response, request);
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
        List<ScheduleCalendarItemModel> schedules = scheduleQueryService.listSchedulesForCalendar(
            outpatientScheduleMapper.toCalendarScheduleQuery(request));
        int bookedCount = appointmentQueryService.countActiveOutpatientBookingsInRange(
            request.getOrganizationId(), monthRange[0], monthRange[1]);
        PageSchedulesByMonthResponse response = outpatientScheduleMapper.toPageSchedulesByMonthResponse(
            schedules, request.getYearMonth(), bookedCount);
        this.enrichMonthSubstituteDisplay(response, request.getOrganizationId());
        weekTemplateFallbackSupport.mergeMonthFallback(response, request);
        return response;
    }

    /**
     * 创建门诊日程草稿（2.3）。
     *
     * @param request 创建请求
     * @return 新日程主键
     */
    @Transactional
    public CreateScheduleResponse createSchedule(@Valid CreateScheduleRequest request) {
        // Gate：写路径要求登录用户。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        scheduleActorMasterDataValidator.assertActorsReferenceValid(request.getOrganizationId(), request.getActors());
        // Cmd：落草稿 Schedule + actors。
        CreateScheduleCommand command = outpatientScheduleMapper.toCreateScheduleCommand(request, operatorId);
        Long scheduleId = scheduleDomainService.createSchedule(command);
        CreateScheduleResponse response = new CreateScheduleResponse();
        response.setScheduleId(scheduleId);
        return response;
    }

    /**
     * 编辑草稿日程（2.6）：仅 draft 状态允许修改参与者/窗口/服务口径/说明。
     *
     * @param request 编辑请求
     */
    @Transactional
    public void updateScheduleDraft(@Valid UpdateScheduleDraftRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Gate：仅草稿可编辑，避免已发布日程被应用层误改。
        this.requireDraftSchedule(request.getScheduleId());
        // Cmd：按入参非空字段逐项编排领域命令。
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
    @Transactional
    public void publishSchedule(@Valid PublishScheduleRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleCalendarItemModel draft = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        scheduleActorMasterDataValidator.assertActorModelsReferenceValid(draft.getOrganizationId(), draft.getActors());
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
        PublishScheduleCommand command = outpatientScheduleMapper.toPublishScheduleCommand(request, operatorId);
        scheduleDomainService.publishSchedule(command);
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "PUBLISH_SCHEDULE", null);
    }

    /**
     * 发布前冲突预览（BR-EXT-005/006）。
     */
    public PreviewPublishScheduleConflictsResponse previewPublishScheduleConflicts(
        @Valid PreviewPublishScheduleConflictsRequest request) {
        bookingOperatorResolver.requireOperatorId();
        ScheduleCalendarItemModel draft = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
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
    @Transactional
    public ProvisionSlotBatchResponse provisionSlotBatch(@Valid ProvisionSlotBatchRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        List<SlotTemplateRequest> templates = request.getSlotTemplates();
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
        ProvisionSlotBatchCommand command = outpatientScheduleMapper.toProvisionSlotBatchCommand(request, operatorId);
        List<Long> slotIds = slotDomainService.provisionSlotBatch(command);
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "PROVISION_SLOT_BATCH",
            slotIds != null ? String.valueOf(slotIds.size()) : "0");
        return outpatientScheduleMapper.toProvisionSlotBatchResponse(slotIds);
    }

    /**
     * 从岗位出诊模板生成 Schedule 草稿（2.10）。
     */
    @Transactional
    public GenerateFromPractitionerTemplateResponse generateFromPractitionerTemplate(
        @Valid GenerateFromPractitionerTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        return practitionerScheduleGenerateSupport.generateForPractitioner(
            request.getOrganizationId(), request.getPractitionerId(), operatorId);
    }

    /**
     * 科室级岗位批量生成 Schedule 草稿（2.22）。
     */
    @Transactional
    public GenerateDepartmentFromPractitionerRolesResponse generateDepartmentFromPractitionerRoles(
        @Valid GenerateDepartmentFromPractitionerRolesRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleFormMasterDataSnapshot snapshot = bookingScheduleMasterDataPort.loadOutpatientScheduleFormOptions(
            request.getOrganizationId());
        Set<Long> targetIds = this.resolveTargetPractitionerIds(request.getPractitionerIds(), snapshot);
        GenerateDepartmentFromPractitionerRolesResponse response = new GenerateDepartmentFromPractitionerRolesResponse();
        for (Long practitionerId : targetIds) {
            DepartmentPractitionerGenerateItemDto item = new DepartmentPractitionerGenerateItemDto();
            item.setPractitionerId(practitionerId);
            item.setPractitionerName(this.resolvePractitionerName(practitionerId, snapshot));
            try {
                GenerateFromPractitionerTemplateResponse generated = practitionerScheduleGenerateSupport.generateForPractitioner(
                    request.getOrganizationId(), practitionerId, operatorId);
                item.getScheduleIds().addAll(generated.getScheduleIds());
                response.getSuccessItems().add(item);
                response.setTotalScheduleCount(response.getTotalScheduleCount() + item.getScheduleIds().size());
            } catch (BusinessException ex) {
                item.setMessage(ex.getMessage());
                response.getFailedItems().add(item);
            }
        }
        if (response.getSuccessItems().isEmpty() && response.getFailedItems().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "科室无可生成岗位模板的医师");
        }
        return response;
    }

    /**
     * 从 T2 日程模板按区间生成 Schedule（2.17）。
     *
     * @param request 生成请求
     * @return 生成结果
     */
    @Transactional
    public GenerateFromTemplateResponse generateFromTemplate(@Valid GenerateFromTemplateRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        return generateFromTemplateSupport.generate(request, operatorId);
    }

    /**
     * 复制日程到下一周（2.11）：复制参与者并创建新草稿。
     */
    @Transactional
    public CopyScheduleToNextWeekResponse copyScheduleToNextWeek(@Valid CopyScheduleToNextWeekRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleCalendarItemModel source = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        String copyName = source.getName() == null ? "复制日程" : source.getName() + " (下周)";
        CreateScheduleCommand createCommand = outpatientScheduleMapper.toCopyScheduleCommand(
            source, source.getOrganizationId(), copyName, operatorId);
        Long newScheduleId = scheduleDomainService.createSchedule(createCommand);
        if (source.getPlanningHorizonStart() != null && source.getPlanningHorizonEnd() != null) {
            PublishScheduleCommand publishCommand = new PublishScheduleCommand();
            publishCommand.setOperatorId(operatorId);
            PublishScheduleCommand.PublishScheduleItemCommand item = new PublishScheduleCommand.PublishScheduleItemCommand();
            item.setScheduleId(newScheduleId);
            item.setPlanningHorizonStart(source.getPlanningHorizonStart().plusDays(7));
            item.setPlanningHorizonEnd(source.getPlanningHorizonEnd().plusDays(7));
            publishCommand.setItem(item);
            scheduleDomainService.publishSchedule(publishCommand);
        }
        CopyScheduleToNextWeekResponse response = new CopyScheduleToNextWeekResponse();
        response.setNewScheduleId(newScheduleId);
        return response;
    }

    /**
     * 停诊影响预览（2.18）。
     *
     * @param request 预览请求
     * @return 影响摘要与映射表
     */
    public PreviewStopClinicImpactResponse previewStopClinicImpact(@Valid PreviewStopClinicImpactRequest request) {
        bookingOperatorResolver.requireOperatorId();
        return stopClinicImpactService.previewImpact(request);
    }

    /**
     * 释放停诊预览冻结（向导取消，TC-FRZ-01）。
     */
    @Transactional
    public void releaseStopClinicPreviewFreeze(@Valid ReleaseStopClinicPreviewFreezeRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        stopClinicImpactService.releasePreviewFreeze(
            request.getOrganizationId(),
            request.getScheduleId(),
            request.getSlotIds(),
            operatorId);
    }

    /**
     * 批量转交/取消预约并停诊（2.19）。
     *
     * @param request 执行请求
     * @return 执行结果
     */
    @Transactional
    public TransferAppointmentsOnStopClinicResponse transferAppointmentsOnStopClinic(
        @Valid TransferAppointmentsOnStopClinicRequest request) {
        bookingOperatorResolver.requireOperatorId();
        return stopClinicImpactService.transferAndStop(request);
    }

    /**
     * 替诊（2.21 · CHG-SCH-08）：设置或撤销顶班医师，不迁移预约。
     */
    @Transactional
    public SubstitutePractitionerResponse substitutePractitioner(@Valid SubstitutePractitionerRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleCalendarItemModel schedule = scheduleQueryService.loadCalendarItemById(request.getScheduleId());
        Long originalPractitionerId = this.resolvePractitionerId(schedule.getActors());
        if (originalPractitionerId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "日程无医师参与者，无法替诊");
        }
        if (request.getSubstitutePractitionerId() != null) {
            if (request.getSubstitutePractitionerId().equals(originalPractitionerId)) {
                throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "替诊医师不能与原出诊医师相同");
            }
            scheduleActorMasterDataValidator.assertPractitionerReferenceValid(
                schedule.getOrganizationId(), request.getSubstitutePractitionerId());
        }
        SubstitutePractitionerCommand command = outpatientScheduleMapper.toSubstitutePractitionerCommand(request, operatorId);
        scheduleDomainService.substitutePractitioner(command);
        bookingAuditSupport.record(
            "Schedule",
            request.getScheduleId(),
            request.getSubstitutePractitionerId() == null ? "CLEAR_SUBSTITUTE" : "SUBSTITUTE_PRACTITIONER",
            request.getSubstitutePractitionerId() == null ? null : String.valueOf(request.getSubstitutePractitionerId()));
        SubstitutePractitionerResponse response = new SubstitutePractitionerResponse();
        response.setScheduleId(request.getScheduleId());
        response.setOriginalPractitionerId(originalPractitionerId);
        response.setSubstitutePractitionerId(request.getSubstitutePractitionerId());
        response.setSubstituteReason(request.getReason());
        response.setCleared(request.getSubstitutePractitionerId() == null);
        return response;
    }

    /**
     * 停诊：停用日程（2.9）。
     *
     * @param request 停用请求
     */
    @Transactional
    public void deactivateSchedule(@Valid DeactivateScheduleRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        DeactivateScheduleCommand command = outpatientScheduleMapper.toDeactivateScheduleCommand(request, operatorId);
        scheduleDomainService.deactivateSchedule(command);
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "DEACTIVATE_SCHEDULE", request.getReason());
    }

    /**
     * 恢复开诊（CHG-SCH-10 / BR-EXT-012）：inactive → active，不自动恢复号源。
     */
    @Transactional
    public ReactivateScheduleResponse reactivateSchedule(@Valid ReactivateScheduleRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ReactivateScheduleCommand command = outpatientScheduleMapper.toReactivateScheduleCommand(request, operatorId);
        scheduleDomainService.reactivateSchedule(command);
        bookingAuditSupport.record("Schedule", request.getScheduleId(), "REACTIVATE_SCHEDULE", null);
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

    private Set<Long> resolveTargetPractitionerIds(List<Long> requestedIds, ScheduleFormMasterDataSnapshot snapshot) {
        Set<Long> targetIds = new HashSet<>();
        if (requestedIds != null && !requestedIds.isEmpty()) {
            targetIds.addAll(requestedIds);
            return targetIds;
        }
        if (snapshot != null && snapshot.getPractitioners() != null) {
            for (MasterDataOption option : snapshot.getPractitioners()) {
                if (option == null || option.getValue() == null) {
                    continue;
                }
                try {
                    targetIds.add(Long.parseLong(option.getValue()));
                } catch (NumberFormatException ignored) {
                    // skip invalid option
                }
            }
        }
        return targetIds;
    }

    private String resolvePractitionerName(Long practitionerId, ScheduleFormMasterDataSnapshot snapshot) {
        if (snapshot == null || snapshot.getPractitioners() == null || practitionerId == null) {
            return null;
        }
        String idText = String.valueOf(practitionerId);
        for (MasterDataOption option : snapshot.getPractitioners()) {
            if (option != null && idText.equals(option.getValue())) {
                return option.getLabel();
            }
        }
        return null;
    }

    private void enrichCalendarSubstituteDisplay(PageSchedulesByCalendarResponse response, Long organizationId) {
        if (response == null || response.getDays() == null) {
            return;
        }
        List<ScheduleCalendarCellDto> cells = new ArrayList<>();
        for (ScheduleCalendarDayDto day : response.getDays()) {
            if (day != null && day.getSchedules() != null) {
                cells.addAll(day.getSchedules());
            }
        }
        ScheduleFormMasterDataSnapshot snapshot = bookingScheduleMasterDataPort.loadOutpatientScheduleFormOptions(organizationId);
        outpatientScheduleMapper.enrichSubstituteDisplay(cells, snapshot.getPractitioners());
    }

    private void enrichMonthSubstituteDisplay(PageSchedulesByMonthResponse response, Long organizationId) {
        if (response == null || response.getDays() == null) {
            return;
        }
        List<ScheduleCalendarCellDto> cells = new ArrayList<>();
        for (ScheduleCalendarMonthDayDto day : response.getDays()) {
            if (day != null && day.getSchedules() != null) {
                cells.addAll(day.getSchedules());
            }
        }
        ScheduleFormMasterDataSnapshot snapshot = bookingScheduleMasterDataPort.loadOutpatientScheduleFormOptions(organizationId);
        outpatientScheduleMapper.enrichSubstituteDisplay(cells, snapshot.getPractitioners());
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
