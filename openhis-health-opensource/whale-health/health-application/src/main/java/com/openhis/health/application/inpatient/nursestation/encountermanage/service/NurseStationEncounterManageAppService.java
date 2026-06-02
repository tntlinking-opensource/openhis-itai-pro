package com.openhis.health.application.inpatient.nursestation.encountermanage.service;

import com.openhis.health.application.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler;
import com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.NurseStationQueueDisplayEnrichment;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.NurseStationTaskCountBundle;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.ParticipantDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.PatientQueueItemDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.mapper.NurseStationEncounterManageMapper;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.GetPatientDetailRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.LoadWardTreeRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.PagePatientQueueRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.SavePatientInfoRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.GetPatientDetailResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.LoadWardTreeResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.PagePatientQueueResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.SavePatientInfoResponse;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.SaveNurseStationPatientCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.utils.EncounterParticipantRoleDisplayUtil;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站患者管理应用服务（AS-IP-NUR-001，IF-IP-NUR-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate/Pre：管理病区树、在院队列分页、IMP 就诊可见性、任务筛选与 per-encounter 计数。</li>
 *   <li>Cmd：患者信息维护写路径（病情、责任医护；禁止改床与状态）。</li>
 *   <li>Asm：经 {@link NurseStationEncounterManageMapper} 与 {@link InpatientInfoQueueDisplayAssembler} 装配响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientNurseStation/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationEncounterManageAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 当前登录用户：解析 practitionerId、管理病区。 */
    private final ICurrentUser currentUser;

    /** 位置读侧：病区树与病房解析。 */
    private final LocationQueryService locationQueryService;

    /** 就诊读侧：护士站队列、门禁与参与者。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextFacade encounterContextFacade;

    /** 就诊写侧：护士站患者信息维护。 */
    private final EncounterDomainService encounterDomainService;

    /** 患者扩展档读侧。 */
    private final PatientQueryService patientQueryService;

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;

    /** 收费明细读侧（待结算合计 → 展示余额）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 药品申请批量读侧。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 诊疗申请批量读侧。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 执行记录批量读侧。 */
    private final ProcedureQueryService procedureQueryService;

    /** 医务人员展示名解析。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 护士站 DTO/Command 映射。 */
    private final NurseStationEncounterManageMapper nurseStationEncounterManageMapper;

    /** 住院队列读模型展示回填。 */
    private final InpatientInfoQueueDisplayAssembler inpatientInfoQueueDisplayAssembler;

    /**
     * IF-IP-NUR-01：病区病房树查询。
     *
     * @param request 无业务字段；登录者从会话解析
     * @return 病区根节点及子病房
     * @throws BusinessException 位置查询失败时抛出 EX-IP-NUR-101
     */
    public LoadWardTreeResponse loadWardTree(LoadWardTreeRequest request) {
        try {
            Long practitionerId = currentUser.getCurrentPractitionerId();
            // Pre：按登录者管理病区加载病区根；无分配时域层回落全部启用病区（BR-IP-NUR-001）。
            List<Location> wards = locationQueryService.listManagedWardsForPractitioner(practitionerId);
            Map<Long, List<Location>> housesByWardId = new HashMap<>();
            for (Location ward : wards) {
                if (ward == null || ward.getId() == null) {
                    continue;
                }
                // Pre：每个病区下挂启用病房节点，树不含床位（BR-IP-NUR-001）。
                List<Location> houses = locationQueryService.listHousesUnderWard(ward.getId());
                housesByWardId.put(ward.getId(), houses);
            }
            return nurseStationEncounterManageMapper.toLoadWardTreeResponse(wards, housesByWardId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NUR_101,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-NUR-02：在院患者列表分页查询。
     *
     * @param request 地理筛选、关键字、任务筛选与分页
     * @return 富字段患者卡片分页
     * @throws BusinessException 分页非法、任务筛选非法或查询失败
     */
    public PagePatientQueueResponse pagePatientQueue(PagePatientQueueRequest request) {
        try {
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_NUR_101,
                "分页参数不合法，请检查页码与每页条数");
            // Gate：任务筛选 code 须在白名单内或为空（BR-IP-NUR-007）。
            String taskFilterCode = this.resolveNurseStationTaskFilterFromRequest(request);
            this.assertTaskFilterAllowed(taskFilterCode);
            Long practitionerId = currentUser.getCurrentPractitionerId();
            // Pre：管理病区并集写入查询，限制可见地理范围（BR-IP-NUR-003）。
            List<Location> managedWards = locationQueryService.listManagedWardsForPractitioner(practitionerId);
            List<Long> managedWardIds = new ArrayList<>();
            for (Location ward : managedWards) {
                if (ward == null || ward.getId() == null) {
                    continue;
                }
                managedWardIds.add(ward.getId());
            }
            Map<String, String> houseNameByBusNo = this.buildHouseNameByBusNoIndex(managedWards);
            LocalDate executionDate = this.resolveNurseStationExecutionDateFromRequest(request);
            Collection<Long> encounterIdsIn = null;
            String taskFilter = taskFilterCode;
            if (StringUtils.isNotBlank(taskFilter)) {
                // Pre：任务筛选须先在同一地理范围内拉候选 encounter，再按计数收窄（BR-IP-NUR-007）。
                InpatientEncounterPageQuery candidateQuery =
                    nurseStationEncounterManageMapper.toInpatientEncounterPageQuery(request, managedWardIds, null);
                PageQueryBinder.bind(request, candidateQuery);
                List<InpatientEncounterContextQueryModel> candidates =
                    encounterContextFacade.listInpatientEncounterContexts(candidateQuery);
                Set<Long> candidateIds = new LinkedHashSet<>();
                for (InpatientEncounterContextQueryModel row : candidates) {
                    if (row != null && row.getEncounterId() != null) {
                        candidateIds.add(row.getEncounterId());
                    }
                }
                Map<Long, NurseStationTaskCountBundle> countsForCandidates =
                    this.buildTaskCountsForEncounters(candidateIds, executionDate);
                encounterIdsIn = this.filterEncounterIdsByTaskFilter(candidateIds, countsForCandidates, taskFilter);
            }
            InpatientEncounterPageQuery pageQuery =
                nurseStationEncounterManageMapper.toInpatientEncounterPageQuery(request, managedWardIds, encounterIdsIn);
            PageQueryBinder.bind(request, pageQuery);
            Page<InpatientEncounterContextQueryModel> page =
                encounterContextFacade.pageInpatientNurseStationQueue(pageQuery);
            Set<Long> pageEncounterIds = new LinkedHashSet<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    pageEncounterIds.add(row.getEncounterId());
                }
            }
            // Pre：当前页批量账户，禁止 N+1（BR-IP-NUR-006）。
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(pageEncounterIds);
            // Pre：病情优先级读模型未投影，批量查就诊主档回填列表卡片。
            Map<Long, String> priorityEnumByEncounterId =
                encounterQueryService.mapPriorityEnumByEncounterIds(pageEncounterIds);
            Map<Long, NurseStationTaskCountBundle> taskCounts =
                this.buildTaskCountsForEncounters(pageEncounterIds, executionDate);
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent());
            Map<Long, NurseStationQueueDisplayEnrichment> enrichmentByEncounterId =
                this.buildDisplayEnrichment(page.getContent(), houseNameByBusNo);
            Map<Long, BigDecimal> chargedOccupancyByEncounterId =
                chargeItemQueryService.mapInpatientChargedOccupancyTotalPriceByEncounterIds(pageEncounterIds);
            List<PatientQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row == null) {
                    continue;
                }
                Account account = null;
                NurseStationTaskCountBundle counts = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                NurseStationQueueDisplayEnrichment enrichment = null;
                String priorityEnum = null;
                if (row.getEncounterId() != null) {
                    account = accountByEncounterId.get(row.getEncounterId());
                    counts = taskCounts.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                    enrichment = enrichmentByEncounterId.get(row.getEncounterId());
                    priorityEnum = priorityEnumByEncounterId.get(row.getEncounterId());
                }
                // Asm：展示余额 = 预交金净值 −（待结算 + 已结算占用）；PLANNED 待收费不扣减。
                BigDecimal chargedOccupancy = BigDecimal.ZERO;
                if (row.getEncounterId() != null) {
                    BigDecimal total = chargedOccupancyByEncounterId.get(row.getEncounterId());
                    if (total != null) {
                        chargedOccupancy = total;
                    }
                }
                BigDecimal displayBalance =
                    InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
                        account, chargedOccupancy);
                boolean lowBalance = this.isLowBalanceWarning(displayBalance);
                Long accountId = null;
                if (account != null) {
                    accountId = account.getId();
                }
                content.add(nurseStationEncounterManageMapper.toPatientQueueItemDto(
                    row, counts, displayBalance, lowBalance, enrichment, rowOverlay, priorityEnum, accountId));
            }
            return nurseStationEncounterManageMapper.toPagePatientQueueResponse(content, page);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NUR_101,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-NUR-03：患者详情查询。
     *
     * @param request 就诊主键
     * @return 全景详情（列表字段超集）
     * @throws BusinessException 就诊不可见或读失败
     */
    public GetPatientDetailResponse getPatientDetail(GetPatientDetailRequest request) {
        try {
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "就诊标识不能为空");
            }
            Long encounterId = request.getEncounterId();
            // Pre：须为住院类就诊（BR-IP-NUR-002）。
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientNurseStationConstants.EX_IP_NUR_102,
                "患者不可用，请刷新");
            InpatientEncounterContextQueryModel contextRow;
            try {
                contextRow = encounterContextFacade.mustGetInpatientContextByEncounterId(encounterId);
            } catch (BusinessException ex) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_NUR_102,
                    "患者不可用，请刷新");
            }
            Long practitionerId = currentUser.getCurrentPractitionerId();
            List<Location> managedWards = locationQueryService.listManagedWardsForPractitioner(practitionerId);
            Map<String, String> houseNameByBusNo = this.buildHouseNameByBusNoIndex(managedWards);
            Set<Long> singleEncounter = Collections.singleton(encounterId);
            Map<Long, Account> accountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(singleEncounter);
            Account account = accountByEncounterId.get(encounterId);
            Map<Long, NurseStationTaskCountBundle> taskCounts =
                this.buildTaskCountsForEncounters(singleEncounter, LocalDate.now());
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(Collections.singletonList(contextRow));
            Map<Long, NurseStationQueueDisplayEnrichment> enrichmentByEncounterId =
                this.buildDisplayEnrichment(Collections.singletonList(contextRow), houseNameByBusNo);
            BigDecimal chargedOccupancy =
                chargeItemQueryService.sumInpatientChargedOccupancyTotalPriceByEncounterId(encounterId);
            BigDecimal displayBalance =
                InpatientDisplayAccountBalanceAssembler.computeDisplayAccountBalance(
                    account, chargedOccupancy);
            boolean lowBalance = this.isLowBalanceWarning(displayBalance);
            NurseStationTaskCountBundle counts = taskCounts.get(encounterId);
            InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay =
                displayOverlay.get(encounterId);
            NurseStationQueueDisplayEnrichment enrichment = enrichmentByEncounterId.get(encounterId);
            Long accountId = null;
            if (account != null) {
                accountId = account.getId();
            }
            PatientQueueItemDto base = nurseStationEncounterManageMapper.toPatientQueueItemDto(
                contextRow,
                counts,
                displayBalance,
                lowBalance,
                enrichment,
                rowOverlay,
                encounter.getPriorityEnum(),
                accountId);
            // Pre：全角色活动参与者与患者扩展档补全详情增量字段。
            List<EncounterParticipantQueryModel> participantRows =
                encounterQueryService.listActiveEncounterParticipants(encounterId);
            List<ParticipantDto> participants = this.toParticipantDtos(participantRows);
            PatientDetailQueryModel patientDetail = null;
            if (contextRow.getPatientId() != null) {
                patientDetail = patientQueryService.loadPatientDetail(contextRow.getPatientId());
            }
            String phone = null;
            String idCardMasked = null;
            if (patientDetail != null && patientDetail.getPatient() != null) {
                PatientCoreQueryModel core = patientDetail.getPatient();
                phone = core.getPhone();
                idCardMasked = this.maskIdCard(core.getIdCard());
            }
            GetPatientDetailResponse response = new GetPatientDetailResponse();
            response.setDetail(nurseStationEncounterManageMapper.toPatientDetailDto(
                base, phone, idCardMasked, participants));
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NUR_101,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-NUR-04：患者信息维护。
     *
     * @param request 病情与责任医护变更
     * @return 保存结果
     * @throws BusinessException 状态不允许或领域校验失败
     */
    public SavePatientInfoResponse savePatientInfo(SavePatientInfoRequest request) {
        if (request == null || request.getEncounterId() == null) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                "就诊标识不能为空");
        }
        // Gate：写路径须已登录操作人（B.6.1）。
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_NUR_201,
            "保存失败，请联系管理员");
        try {
            Long encounterId = request.getEncounterId();
            // Pre：住院类就诊门禁。
            EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
                encounterId,
                EncounterClass.IMP.getCode(),
                InpatientNurseStationConstants.EX_IP_NUR_102,
                "患者不可用，请刷新");
            // Pre：仅 REGISTERED/IN_PROGRESS 允许维护，禁止改床与状态（BR-IP-NUR-010）。
            this.assertEncounterSavableForNurseStation(encounter.getStatusEnum());
            // Pre：请求映射为领域命令，携带操作人主键供审计（§6.5）。
            SaveNurseStationPatientCommand command =
                nurseStationEncounterManageMapper.toSaveNurseStationPatientCommand(request, operatorId);
            // Cmd：仅更新病情与责任医护；禁止改床与就诊状态（BR-IP-NUR-010）；读模型投影异步刷新。
            encounterDomainService.saveNurseStationPatient(command);
            return nurseStationEncounterManageMapper.toSavePatientInfoResponse(encounterId);
        } catch (BusinessException ex) {
            // 患者不可用类错误须原样透出，便于前端刷新队列。
            if (InpatientNurseStationConstants.EX_IP_NUR_102.equals(ex.getErrorCode())) {
                throw ex;
            }
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NUR_201,
                "保存失败，请联系管理员");
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NUR_201,
                "保存失败，请联系管理员");
        }
    }

    /**
     * 从分页请求解析任务筛选 code（未传则为 null）。
     *
     * @param request 分页请求；可 null
     * @return 筛选 code 或 null
     */
    private String resolveNurseStationTaskFilterFromRequest(PagePatientQueueRequest request) {
        if (request == null) {
            return null;
        }
        return request.getNurseStationTaskFilter();
    }

    /**
     * 从分页请求解析待执行计数自然日（未传则为当天）。
     *
     * @param request 分页请求；可 null
     * @return 自然日
     */
    private LocalDate resolveNurseStationExecutionDateFromRequest(PagePatientQueueRequest request) {
        if (request != null && request.getNurseStationExecutionDate() != null) {
            return request.getNurseStationExecutionDate();
        }
        return LocalDate.now();
    }

    /**
     * 任务筛选 code 白名单校验。
     *
     * @param taskFilter 请求筛选 code
     */
    private void assertTaskFilterAllowed(String taskFilter) {
        if (StringUtils.isBlank(taskFilter)) {
            return;
        }
        String trimmed = taskFilter.trim();
        if (InpatientNurseStationConstants.TASK_FILTER_PENDING_AUDIT.equals(trimmed)) {
            return;
        }
        if (InpatientNurseStationConstants.TASK_FILTER_PENDING_EXECUTE.equals(trimmed)) {
            return;
        }
        if (InpatientNurseStationConstants.TASK_FILTER_PROCESSED.equals(trimmed)) {
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "任务筛选参数不合法");
    }

    /**
     * 护士站维护允许的就诊状态（BR-IP-NUR-010）。
     *
     * @param statusEnum 就诊状态 code
     */
    private void assertEncounterSavableForNurseStation(String statusEnum) {
        if (EncounterStatus.REGISTERED.getCode().equals(statusEnum)) {
            return;
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusEnum)) {
            return;
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_NUR_201,
            "保存失败，请联系管理员");
    }

    /**
     * 展示余额是否低于预警阈值（BR-IP-NUR-006）。
     *
     * @param displayBalance 展示余额
     * @return true 表示需预警
     */
    private boolean isLowBalanceWarning(BigDecimal displayBalance) {
        if (displayBalance == null) {
            return false;
        }
        return displayBalance.compareTo(InpatientNurseStationConstants.LOW_BALANCE_WARNING_THRESHOLD) < 0;
    }

    /**
     * 管理病区下全部病房 bus_no → 名称索引，供床位 bus_no 前缀匹配病房名。
     *
     * @param managedWards 管理病区列表
     * @return bus_no → 病房名称
     */
    private Map<String, String> buildHouseNameByBusNoIndex(List<Location> managedWards) {
        Map<String, String> houseNameByBusNo = new HashMap<>();
        if (managedWards == null) {
            return houseNameByBusNo;
        }
        for (Location ward : managedWards) {
            if (ward == null || ward.getId() == null) {
                continue;
            }
            List<Location> houses = locationQueryService.listHousesUnderWard(ward.getId());
            for (Location house : houses) {
                if (house != null && StringUtils.isNotBlank(house.getBusNo())) {
                    houseNameByBusNo.put(house.getBusNo().trim(), house.getName());
                }
            }
        }
        return houseNameByBusNo;
    }

    /**
     * 为当前页读模型行构建展示增补（病房名、检索码）。
     *
     * @param rows              读模型行
     * @param houseNameByBusNo  病房 bus_no 索引
     * @return encounterId → 增补
     */
    private Map<Long, NurseStationQueueDisplayEnrichment> buildDisplayEnrichment(
        List<InpatientEncounterContextQueryModel> rows,
        Map<String, String> houseNameByBusNo) {
        Map<Long, NurseStationQueueDisplayEnrichment> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Set<Long> patientIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
        }
        Map<Long, String> pinyinByPatientId = new HashMap<>();
        Map<Long, String> wubiByPatientId = new HashMap<>();
        // Pre：读模型未投影检索码，按当前页患者主键逐条补全（页大小受 Gate 约束，避免全表 N+1）。
        for (Long patientId : patientIds) {
            PatientDetailQueryModel detail = patientQueryService.loadPatientDetail(patientId);
            if (detail == null || detail.getPatient() == null) {
                continue;
            }
            pinyinByPatientId.put(patientId, detail.getPatient().getPyStr());
            wubiByPatientId.put(patientId, detail.getPatient().getWbStr());
        }
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            NurseStationQueueDisplayEnrichment enrichment = new NurseStationQueueDisplayEnrichment();
            enrichment.setHouseName(this.resolveHouseName(row.getBedId(), houseNameByBusNo));
            if (row.getPatientId() != null) {
                enrichment.setPinyinCode(pinyinByPatientId.get(row.getPatientId()));
                enrichment.setWubiCode(wubiByPatientId.get(row.getPatientId()));
            }
            result.put(row.getEncounterId(), enrichment);
        }
        return result;
    }

    /**
     * 由床位 bus_no 前缀匹配病房名称。
     *
     * @param bedId            床位主键
     * @param houseNameByBusNo 病房 bus_no 索引
     * @return 病房名或 null
     */
    private String resolveHouseName(Long bedId, Map<String, String> houseNameByBusNo) {
        if (bedId == null || houseNameByBusNo == null || houseNameByBusNo.isEmpty()) {
            return null;
        }
        Location bed = locationQueryService.findOptionalById(bedId).orElse(null);
        if (bed == null || StringUtils.isBlank(bed.getBusNo())) {
            return null;
        }
        String bedBusNo = bed.getBusNo().trim();
        String matchedName = null;
        int matchedPrefixLen = 0;
        for (Map.Entry<String, String> entry : houseNameByBusNo.entrySet()) {
            String houseBusNo = entry.getKey();
            if (StringUtils.isBlank(houseBusNo)) {
                continue;
            }
            String prefix = houseBusNo.trim();
            if (bedBusNo.startsWith(prefix) && prefix.length() > matchedPrefixLen) {
                matchedPrefixLen = prefix.length();
                matchedName = entry.getValue();
            }
        }
        return matchedName;
    }

    /**
     * 批量计算就诊维度任务计数（BR-IP-NUR-004/005）。
     *
     * @param encounterIds  就诊主键集合
     * @param executionDate 待执行统计自然日
     * @return encounterId → 计数包
     */
    private Map<Long, NurseStationTaskCountBundle> buildTaskCountsForEncounters(
        Collection<Long> encounterIds,
        LocalDate executionDate) {
        Map<Long, NurseStationTaskCountBundle> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> idSet = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                idSet.add(id);
            }
        }
        if (idSet.isEmpty()) {
            return result;
        }
        for (Long encounterId : idSet) {
            NurseStationTaskCountBundle bundle = new NurseStationTaskCountBundle();
            bundle.setPendingAuditCount(0);
            bundle.setPendingExecuteCount(0);
            result.put(encounterId, bundle);
        }
        String activeCode = RequestStatus.ACTIVE.getCode();
        String verifiedCode = RequestStatus.VERIFIED.getCode();
        String stoppedCode = RequestStatus.STOPPED.getCode();
        String nursePricingSourceCode = GenerateSource.NURSE_PRICING.getCode();
        List<MedicationRequest> activeMeds =
            medicationRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, activeCode);
        List<ServiceRequest> activeServices =
            serviceRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, activeCode);
        for (MedicationRequest row : activeMeds) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 护士划价来源不计入待校对（BR-IP-NUR-004）。
            if (nursePricingSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
            if (bundle != null) {
                bundle.setPendingAuditCount(bundle.getPendingAuditCount() + 1);
            }
        }
        for (ServiceRequest row : activeServices) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 护士划价来源不计入待校对（兼容历史 ACTIVE 脏数据）。
            if (nursePricingSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
            if (bundle != null) {
                bundle.setPendingAuditCount(bundle.getPendingAuditCount() + 1);
            }
        }
        List<MedicationRequest> verifiedMeds =
            medicationRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, verifiedCode);
        List<ServiceRequest> verifiedServices =
            serviceRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, verifiedCode);
        List<Procedure> procedures = procedureQueryService.listByEncounterIds(idSet);
        LocalDateTime dayStart = executionDate.atStartOfDay();
        LocalDateTime dayEnd = executionDate.plusDays(1).atStartOfDay();
        Map<String, Integer> netCompletedByAnchor = this.buildNetCompletedByAnchorForDay(
            procedures, dayStart, dayEnd);
        this.addPendingExecuteForMedicationRequests(
            verifiedMeds, stoppedCode, netCompletedByAnchor, result);
        this.addPendingExecuteForServiceRequests(
            verifiedServices, stoppedCode, netCompletedByAnchor, result);
        return result;
    }

    /**
     * 当日窗口内按请求锚点统计净 COMPLETED 次数（完成 − 取消，BR-NUR-005 口径）。
     *
     * @param procedures 执行记录列表
     * @param dayStart   自然日起点（含）
     * @param dayEnd     自然日终点（不含）
     * @return 请求锚点 → 净次数
     */
    private Map<String, Integer> buildNetCompletedByAnchorForDay(
        List<Procedure> procedures,
        LocalDateTime dayStart,
        LocalDateTime dayEnd) {
        Map<String, Integer> netByAnchor = new HashMap<>();
        if (procedures == null || procedures.isEmpty()) {
            return netByAnchor;
        }
        char separator = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getRequestId() == null
                || StringUtils.isBlank(procedure.getRequestTable())) {
                continue;
            }
            LocalDateTime occurred = procedure.getOccurrenceTime();
            if (occurred == null) {
                occurred = procedure.getRecordedTime();
            }
            if (occurred == null) {
                continue;
            }
            if (occurred.isBefore(dayStart) || !occurred.isBefore(dayEnd)) {
                continue;
            }
            String anchor = procedure.getRequestTable() + separator + procedure.getRequestId();
            int delta = 0;
            if (EventStatus.COMPLETED.getCode().equals(procedure.getStatusEnum())) {
                delta = 1;
            }
            if (EventStatus.isCancelExecutionStatusCode(procedure.getStatusEnum())) {
                delta = -1;
            }
            if (delta == 0) {
                continue;
            }
            Integer current = netByAnchor.get(anchor);
            if (current == null) {
                current = 0;
            }
            netByAnchor.put(anchor, current + delta);
        }
        return netByAnchor;
    }

    /**
     * 将 VERIFIED 药品请求中「非停嘱且当日净执行=0」的行计入待执行条数。
     *
     * @param requests             药品申请列表
     * @param stoppedCode          停嘱状态 code
     * @param netCompletedByAnchor 当日净执行索引
     * @param result               输出计数包
     */
    private void addPendingExecuteForMedicationRequests(
        List<MedicationRequest> requests,
        String stoppedCode,
        Map<String, Integer> netCompletedByAnchor,
        Map<Long, NurseStationTaskCountBundle> result) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        char separator = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        String requestTable = TableNameConstants.MED_MEDICATION_REQUEST;
        for (MedicationRequest row : requests) {
            if (row == null || row.getEncounterId() == null || row.getId() == null) {
                continue;
            }
            if (stoppedCode.equals(row.getStatusEnum())) {
                continue;
            }
            String anchor = requestTable + separator + row.getId();
            if (this.hasPositiveNetCompleted(netCompletedByAnchor, anchor)) {
                continue;
            }
            NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
            if (bundle != null) {
                bundle.setPendingExecuteCount(bundle.getPendingExecuteCount() + 1);
            }
        }
    }

    /**
     * 将 VERIFIED 诊疗请求中「非停嘱且当日净执行=0」的行计入待执行条数。
     *
     * @param requests             诊疗申请列表
     * @param stoppedCode          停嘱状态 code
     * @param netCompletedByAnchor 当日净执行索引
     * @param result               输出计数包
     */
    private void addPendingExecuteForServiceRequests(
        List<ServiceRequest> requests,
        String stoppedCode,
        Map<String, Integer> netCompletedByAnchor,
        Map<Long, NurseStationTaskCountBundle> result) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        char separator = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        String requestTable = TableNameConstants.WKF_SERVICE_REQUEST;
        for (ServiceRequest row : requests) {
            if (row == null || row.getEncounterId() == null || row.getId() == null) {
                continue;
            }
            if (stoppedCode.equals(row.getStatusEnum())) {
                continue;
            }
            // 护士划价不经执行 Tab，不计入待执行（BR-IP-NUR-005）。
            if (GenerateSource.NURSE_PRICING.getCode().equals(row.getGenerateSourceEnum())) {
                continue;
            }
            String anchor = requestTable + separator + row.getId();
            if (this.hasPositiveNetCompleted(netCompletedByAnchor, anchor)) {
                continue;
            }
            NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
            if (bundle != null) {
                bundle.setPendingExecuteCount(bundle.getPendingExecuteCount() + 1);
            }
        }
    }

    /**
     * 判断请求锚点在当日是否已有正向净执行（已完成次数大于 0）。
     *
     * @param netCompletedByAnchor 当日净执行索引
     * @param anchor               请求锚点
     * @return true 表示当日已执行过
     */
    private boolean hasPositiveNetCompleted(Map<String, Integer> netCompletedByAnchor, String anchor) {
        if (netCompletedByAnchor == null || anchor == null) {
            return false;
        }
        Integer net = netCompletedByAnchor.get(anchor);
        if (net == null) {
            return false;
        }
        if (net > 0) {
            return true;
        }
        return false;
    }

    /**
     * 按任务筛选 code 收窄候选就诊集合（BR-IP-NUR-007）。
     *
     * @param candidateIds 候选 encounterId
     * @param counts       计数包
     * @param taskFilter   筛选 code
     * @return 收窄后的 id 集合
     */
    private Collection<Long> filterEncounterIdsByTaskFilter(
        Set<Long> candidateIds,
        Map<Long, NurseStationTaskCountBundle> counts,
        String taskFilter) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> retained = new ArrayList<>();
        for (Long encounterId : candidateIds) {
            if (encounterId == null) {
                continue;
            }
            NurseStationTaskCountBundle bundle = counts.get(encounterId);
            int audit = 0;
            int execute = 0;
            if (bundle != null) {
                if (bundle.getPendingAuditCount() != null) {
                    audit = bundle.getPendingAuditCount();
                }
                if (bundle.getPendingExecuteCount() != null) {
                    execute = bundle.getPendingExecuteCount();
                }
            }
            boolean match = false;
            if (InpatientNurseStationConstants.TASK_FILTER_PENDING_AUDIT.equals(taskFilter)) {
                if (audit > 0) {
                    match = true;
                }
            } else if (InpatientNurseStationConstants.TASK_FILTER_PENDING_EXECUTE.equals(taskFilter)) {
                if (execute > 0) {
                    match = true;
                }
            } else if (InpatientNurseStationConstants.TASK_FILTER_PROCESSED.equals(taskFilter)) {
                if (audit == 0 && execute == 0) {
                    match = true;
                }
            }
            if (match) {
                retained.add(encounterId);
            }
        }
        return retained;
    }

    /**
     * 活动参与者 → 展示 DTO 列表。
     *
     * @param rows 参与者查询模型
     * @return 参与者 DTO
     */
    private List<ParticipantDto> toParticipantDtos(List<EncounterParticipantQueryModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> practitionerIds = new LinkedHashSet<>();
        for (EncounterParticipantQueryModel row : rows) {
            if (row != null && row.getPractitionerId() != null) {
                practitionerIds.add(row.getPractitionerId());
            }
        }
        Map<Long, String> nameByPractitionerId =
            practitionerQueryService.mapDisplayNameByEntererIds(practitionerIds);
        List<ParticipantDto> result = new ArrayList<>();
        for (EncounterParticipantQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            ParticipantDto dto = new ParticipantDto();
            dto.setRoleCode(row.getRoleCode());
            dto.setRoleName(EncounterParticipantRoleDisplayUtil.resolveDisplayText(row.getRoleCode()));
            dto.setPractitionerId(row.getPractitionerId());
            if (row.getPractitionerId() != null) {
                dto.setPractitionerName(nameByPractitionerId.get(row.getPractitionerId()));
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 证件号展示脱敏（§6.4）。
     *
     * @param idCard 原始证件号
     * @return 脱敏后证件号或 null
     */
    private String maskIdCard(String idCard) {
        if (StringUtils.isBlank(idCard)) {
            return null;
        }
        String trimmed = idCard.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        String prefix = trimmed.substring(0, 4);
        String suffix = trimmed.substring(trimmed.length() - 4);
        return prefix + "********" + suffix;
    }
}
