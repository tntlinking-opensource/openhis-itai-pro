/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.service;

import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayDtoApplier;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayAssembler;
import com.openhis.health.application.shared.inpatient.InpatientAccountBalanceDisplayResult;
import com.openhis.health.application.shared.inpatient.BusinessDischargeTimeResolver;
import com.openhis.health.application.shared.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.inpatient.AdmissionVitalSignSubmitDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.NurseStationQueueDisplayEnrichment;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.NurseStationTaskCountBundle;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.ParticipantDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.PatientQueueItemDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.mapper.NurseStationEncounterManageMapper;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.GetPatientDetailRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.LoadAdmissionParticipantInitRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.LoadWardTreeRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.PagePatientQueueRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.SavePatientInfoRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.GetPatientDetailResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.LoadAdmissionParticipantInitResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.LoadWardTreeResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.PagePatientQueueResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.SavePatientInfoResponse;
import com.openhis.health.application.shared.inpatient.AdmissionVitalSignSaveContext;
import com.openhis.health.application.shared.inpatient.AdmissionVitalSignOrchestrator;
import com.openhis.health.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.InpatientPatientQueueNursingLevelEnrichAssembler;
import com.openhis.health.application.inpatient.nursestation.ATDManager.support.dto.InpatientPatientQueueNursingLevelDisplayRow;
import com.openhis.health.application.inpatient.nursestation.orderaudit.support.NurseStationOrderViewListAssembler;
import com.openhis.health.application.shared.advice.LongTermFirstExecutionSlotSupport;
import com.openhis.health.application.shared.advice.StandingOrderOralPackageInventorySupport;
import com.openhis.health.application.shared.advice.StandingOrderPackageBatchPlanTimeResolver;
import com.openhis.health.application.shared.advice.InpatientStandingOrderSplittableSettingResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.custom.frequency.queries.model.FrequencyExecutionTimePointQueryModel;
import com.openhis.health.domain.custom.frequency.service.FrequencyQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.command.SaveNurseStationPatientCommand;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterDomainService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.queries.model.BatchPatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientCoreQueryModel;
import com.openhis.health.domain.administration.patient.queries.model.PatientDetailQueryModel;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.enums.TherapyType;
import com.openhis.health.domain.share.utils.EncounterParticipantRoleDisplayUtil;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.infrastructure.integration.insurance.benefit.InsuranceRegisterBenefitQueryExtension;
import com.openhis.health.infrastructure.integration.insurance.benefit.ExtensionOut.InsuranceEncounterPsnSnapshot;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
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
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 当前登录医务人员主键（体征 recorder_id）。 */

    /** 分页窗口入站门控（B.10.11）。 */
    private final PageWindowGate pageWindowGate;

    /** 当前登录者管辖病区解析。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;

    /** 位置读侧：病区树与病房解析。 */
    private final LocationQueryService locationQueryService;

    /** 就诊读侧：护士站队列、门禁与参与者。 */
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;

    /** 就诊写侧：护士站患者信息维护。 */
    private final EncounterDomainService encounterDomainService;

    /** 患者扩展档读侧。 */
    private final PatientQueryService patientQueryService;

    /** 就诊账户读侧。 */
    private final AccountQueryService accountQueryService;

    /** 收费明细读侧（待结算合计 → 展示余额）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 支付流水读侧（结算少补 → 展示余额扣减）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

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

    /** 岗位分配 → 医务人员范围解析。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;

    /** 入科入院体征读回填与写编排（001/002 共用）。 */
    private final AdmissionVitalSignOrchestrator admissionVitalSignOrchestrator;

    /** 患者队列护理级别只读 enrich（BR-IP-NUR-015）。 */
    private final InpatientPatientQueueNursingLevelEnrichAssembler inpatientPatientQueueNursingLevelEnrichAssembler;

    /** 医嘱执行列表排除口径（待执行计数与 IF-IP-EXE-01 对齐）。 */
    private final NurseStationOrderViewListAssembler nurseStationOrderViewListAssembler;

    /** 频次展开（诊疗长期待执行计数；药嘱长嘱改走一盒一点解析器）。 */
    private final FrequencyQueryService frequencyQueryService;

    /** 住院长嘱是否可拆；不可拆时药嘱待执与 IF-IP-EXE-01 同源一盒一点。 */
    private final InpatientStandingOrderSplittableSettingResolver standingOrderSplittableSettingResolver;

    /** 长嘱可拆/一盒一点计划点（BR-IP-EXE-035；与执行列表共用）。 */
    private final StandingOrderPackageBatchPlanTimeResolver standingOrderPackageBatchPlanTimeResolver;

    /** 口服一盒一点剩余按天抑制（BR-IP-EXE-038；与 IF-IP-EXE-01 同源）。 */
    private final StandingOrderOralPackageInventorySupport standingOrderOralPackageInventorySupport;

    /** 药品目录批量加载（不可拆切盒取包装拆零比）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 术语下拉（adm_cond_code、method_code 等）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 医保人员编号批量回填（SPI；未装配 Module 时为空 Map）。 */
    private final InsuranceRegisterBenefitQueryExtension insuranceRegisterBenefitQueryExtension;

    /** 业务出院时间：出院医嘱计划时间优先（队列 endTime / 详情粘性条同源）。 */
    private final BusinessDischargeTimeResolver businessDischargeTimeResolver;

    /**
     * IF-IP-NUR-01：病区病房树查询。
     *
     * @param request 无业务字段；登录者从会话解析
     * @return 病区根节点及子病房
     * @throws BusinessException 位置查询失败时抛出 EX-IP-NUR-101
     */
    public LoadWardTreeResponse loadWardTree(LoadWardTreeRequest request) {
        try {
            List<Location> wards = managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty();
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
     * 分床/入科责任医护下拉初始化：住院医师按科室、责任护士按入院病区加载。
     *
     * @param request 可选入院科室与病区位置主键
     * @return 医生与责任护士下拉
     * @throws BusinessException 读失败时 EX-IP-NUR-101
     */
    public LoadAdmissionParticipantInitResponse loadAdmissionParticipantInit(
        LoadAdmissionParticipantInitRequest request) {
        try {
            Long organizationId = null;
            Long wardLocationId = null;
            if (request != null) {
                organizationId = request.getOrganizationId();
                wardLocationId = request.getWardLocationId();
            }
            List<Practitioner> doctors;
            List<Practitioner> nurses;
            if (organizationId != null) {
                // 住院医师：按入院科室挂了激活医师岗的在职人员。
                doctors = practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                    WhalePlatformRoleCodeConstants.ROLE_DOCTOR, organizationId);
            } else {
                // 未传科室：按平台医师角色全量在职人员（不按门诊科室收窄）。
                doctors = practitionerRoleScopeLoader.listActivePractitionersForPlatformRole(
                    WhalePlatformRoleCodeConstants.ROLE_DOCTOR, null);
            }
            if (wardLocationId != null) {
                // 责任护士：仅返回该入院病区挂了激活护士岗的在职人员。
                nurses = practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleAtWardLocation(
                    WhalePlatformRoleCodeConstants.ROLE_NURSE, wardLocationId);
            } else {
                nurses = Collections.emptyList();
            }
            List<OptionDto> admCondCodeOptions = terminologyOptionLoader.loadOptions(
                TerminologyCodeSystemNameConstants.ADM_COND_CODE);
            return nurseStationEncounterManageMapper.toLoadAdmissionParticipantInitResponse(
                doctors, nurses, admCondCodeOptions);
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
            // Gate：列表状态筛选已废止（召回入口迁结算页）；非空一律拒绝。
            this.assertNurseStationStatusFilterRemoved(request.getNurseStationStatusFilter());
            List<Location> managedWards = managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty();
            List<Long> managedWardIds = this.extractWardLocationIds(managedWards);
            Map<String, String> houseNameByBusNo = this.buildHouseNameByBusNoIndex(managedWards);
            LocalDate executionDate = this.resolveNurseStationExecutionDateFromRequest(request);
            Collection<Long> encounterIdsIn = null;
            String taskFilter = taskFilterCode;
            // Pre：任务筛选时会先对候选全集计任务数；本页可切片复用，避免二次扫医嘱（§2.1 #9）。
            // Pre：默认「全部任务」不算待执行槽（极重）；仅 pending-execute/processed 才展开执计数。
            Map<Long, NurseStationTaskCountBundle> taskCountsFromFilterPass = null;
            boolean includePendingExecute = this.shouldIncludePendingExecuteCounts(taskFilter);
            if (StringUtils.isNotEmpty(taskFilter)) {
                // Pre：任务筛选须先在同一地理范围内拉候选 encounter，再按计数收窄（BR-IP-NUR-007）。
                InpatientEncounterPageQuery candidateQuery =
                    nurseStationEncounterManageMapper.toInpatientEncounterPageQuery(request, managedWardIds, null);
                PageQueryBinder.bind(request, candidateQuery);
                List<InpatientEncounterContextQueryModel> candidates =
                    EncounterContextReader.listInpatientEncounterContexts(candidateQuery);
                Set<Long> candidateIds = new LinkedHashSet<>();
                for (InpatientEncounterContextQueryModel row : candidates) {
                    if (row != null && row.getEncounterId() != null) {
                        candidateIds.add(row.getEncounterId());
                    }
                }
                taskCountsFromFilterPass =
                    this.buildTaskCountsForEncounters(candidateIds, executionDate, includePendingExecute);
                encounterIdsIn = this.filterEncounterIdsByTaskFilter(candidateIds, taskCountsFromFilterPass, taskFilter);
            }
            InpatientEncounterPageQuery pageQuery =
                nurseStationEncounterManageMapper.toInpatientEncounterPageQuery(request, managedWardIds, encounterIdsIn);
            PageQueryBinder.bind(request, pageQuery);
            Page<InpatientEncounterContextQueryModel> page =
                EncounterContextReader.pageInpatientNurseStationQueue(pageQuery);
            Set<Long> pageEncounterIds = new LinkedHashSet<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    pageEncounterIds.add(row.getEncounterId());
                }
            }
            // Pre：当前页批量个人现金户（仅余额）；挂账 accountId 另取医保优先（BR：医保入院费用挂医保户）。
            Map<Long, Account> registrationAccountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(pageEncounterIds);
            Map<Long, Account> chargeAccountByEncounterId =
                accountQueryService.mapDefaultChargeAccountsByEncounterIds(pageEncounterIds);
            // Pre：费用性质展示优先医保账户 contract_no（与医生站/门诊同口径）
            Map<Long, String> contractNoByEncounterId =
                accountQueryService.mapPreferredContractNoByEncounterIds(pageEncounterIds);
            // Pre：病情优先级读模型未投影，批量查就诊主档回填列表卡片。
            Map<Long, String> priorityEnumByEncounterId =
                encounterQueryService.mapPriorityEnumByEncounterIds(pageEncounterIds);
            Map<Long, NurseStationTaskCountBundle> taskCounts;
            if (taskCountsFromFilterPass != null) {
                // Pre：筛选通道已对候选计过数，本页切片复用，禁止再跑一遍医嘱/执行计划扫描。
                taskCounts = new HashMap<>();
                for (Long encounterId : pageEncounterIds) {
                    NurseStationTaskCountBundle fromFilter = taskCountsFromFilterPass.get(encounterId);
                    if (fromFilter != null) {
                        taskCounts.put(encounterId, fromFilter);
                    }
                }
            } else {
                // Pre：默认列表只算待校对；待执行明细由右侧页签按选中患者拉取（禁全员展槽）。
                taskCounts =
                    this.buildTaskCountsForEncounters(pageEncounterIds, executionDate, includePendingExecute);
            }
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(page.getContent(), contractNoByEncounterId);
            Map<Long, NurseStationQueueDisplayEnrichment> enrichmentByEncounterId =
                this.buildDisplayEnrichment(page.getContent(), houseNameByBusNo);
            Map<Long, BigDecimal> pendingSettlementByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(pageEncounterIds);
            Map<Long, BigDecimal> settledTotalByEncounterId =
                chargeItemQueryService.mapSettledTotalPriceByEncounterIds(pageEncounterIds);
            Set<Long> reversedPendingResettleEncounterIds =
                paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(pageEncounterIds);
            // Pre：当前页批量解析护理级别展示（BR-IP-NUR-015）
            Map<Long, InpatientPatientQueueNursingLevelDisplayRow> nursingLevelByEncounterId =
                inpatientPatientQueueNursingLevelEnrichAssembler.enrichByEncounterIds(pageEncounterIds, LocalDate.now());
            // Pre：病情术语码 → 展示名（入院状态）。
            Map<String, String> admCondLabelByCode =
                terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.ADM_COND_CODE);
            // Pre：本页业务出院时间（出院医嘱计划时间优先，覆盖脏 end_time）
            Map<Long, LocalDateTime> endTimeByEncounter = new HashMap<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    endTimeByEncounter.put(row.getEncounterId(), row.getEndTime());
                }
            }
            Map<Long, LocalDateTime> businessEndByEncounter =
                businessDischargeTimeResolver.mapByEncounterIds(pageEncounterIds, endTimeByEncounter);
            List<PatientQueueItemDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel row : page.getContent()) {
                if (row == null) {
                    continue;
                }
                Account registrationAccount = null;
                Account chargeAccount = null;
                NurseStationTaskCountBundle counts = null;
                InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay = null;
                NurseStationQueueDisplayEnrichment enrichment = null;
                String priorityEnum = null;
                if (row.getEncounterId() != null) {
                    registrationAccount = registrationAccountByEncounterId.get(row.getEncounterId());
                    chargeAccount = chargeAccountByEncounterId.get(row.getEncounterId());
                    counts = taskCounts.get(row.getEncounterId());
                    rowOverlay = displayOverlay.get(row.getEncounterId());
                    enrichment = enrichmentByEncounterId.get(row.getEncounterId());
                    priorityEnum = priorityEnumByEncounterId.get(row.getEncounterId());
                }
                // Asm：账户余额展示（常态双数；反结后切换主展示文案，写侧多退少补不变）。
                Long encounterId = null;
                if (row != null) {
                    encounterId = row.getEncounterId();
                }
                InpatientAccountBalanceDisplayResult accountBalanceDisplay =
                    this.resolveAccountBalanceDisplay(
                        encounterId,
                        registrationAccount,
                        pendingSettlementByEncounterId,
                        settledTotalByEncounterId,
                        reversedPendingResettleEncounterIds);
                Long accountId = null;
                if (chargeAccount != null) {
                    accountId = chargeAccount.getId();
                }
                PatientQueueItemDto item = nurseStationEncounterManageMapper.toPatientQueueItemDto(
                    row, counts, enrichment, rowOverlay, priorityEnum, accountId);
                InpatientAccountBalanceDisplayDtoApplier.apply(item, accountBalanceDisplay);
                this.applyNursingLevelDisplay(item, nursingLevelByEncounterId.get(row.getEncounterId()));
                if (!StringUtils.isEmpty(priorityEnum)) {
                    item.setPriorityEnumText(admCondLabelByCode.get(priorityEnum.trim()));
                }
                // Asm：队列 endTime 展示业务出院时间
                if (row.getEncounterId() != null && businessEndByEncounter.containsKey(row.getEncounterId())) {
                    item.setEndTime(businessEndByEncounter.get(row.getEncounterId()));
                }
                content.add(item);
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
                contextRow = EncounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
            } catch (BusinessException ex) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_NUR_102,
                    "患者不可用，请刷新");
            }
            List<Location> managedWards = managedWardLocationIdsResolver.listManagedActiveWardsOrEmpty();
            Map<String, String> houseNameByBusNo = this.buildHouseNameByBusNoIndex(managedWards);
            Set<Long> singleEncounter = Collections.singleton(encounterId);
            Map<Long, Account> registrationAccountByEncounterId =
                accountQueryService.mapRegistrationAccountsByEncounterIds(singleEncounter);
            Account registrationAccount = registrationAccountByEncounterId.get(encounterId);
            Map<Long, Account> chargeAccountByEncounterId =
                accountQueryService.mapDefaultChargeAccountsByEncounterIds(singleEncounter);
            Account chargeAccount = chargeAccountByEncounterId.get(encounterId);
            Map<Long, String> contractNoByEncounterId =
                accountQueryService.mapPreferredContractNoByEncounterIds(singleEncounter);
            Map<Long, NurseStationTaskCountBundle> taskCounts =
                this.buildTaskCountsForEncounters(singleEncounter, LocalDate.now(), true);
            Map<Long, InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay> displayOverlay =
                inpatientInfoQueueDisplayAssembler.buildOverlay(
                    Collections.singletonList(contextRow), contractNoByEncounterId);
            Map<Long, NurseStationQueueDisplayEnrichment> enrichmentByEncounterId =
                this.buildDisplayEnrichment(Collections.singletonList(contextRow), houseNameByBusNo);
            Map<Long, BigDecimal> pendingSettlementByEncounterId =
                chargeItemQueryService.mapPendingSettlementTotalPriceByEncounterIds(singleEncounter);
            Map<Long, BigDecimal> settledTotalByEncounterId =
                chargeItemQueryService.mapSettledTotalPriceByEncounterIds(singleEncounter);
            Set<Long> reversedPendingResettleEncounterIds =
                paymentReconciliationQueryService.filterReversedPendingResettleEncounterIds(singleEncounter);
            InpatientAccountBalanceDisplayResult accountBalanceDisplay =
                this.resolveAccountBalanceDisplay(
                    encounterId,
                    registrationAccount,
                    pendingSettlementByEncounterId,
                    settledTotalByEncounterId,
                    reversedPendingResettleEncounterIds);
            NurseStationTaskCountBundle counts = taskCounts.get(encounterId);
            InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay rowOverlay =
                displayOverlay.get(encounterId);
            NurseStationQueueDisplayEnrichment enrichment = enrichmentByEncounterId.get(encounterId);
            Long accountId = null;
            if (chargeAccount != null) {
                accountId = chargeAccount.getId();
            }
            PatientQueueItemDto base = nurseStationEncounterManageMapper.toPatientQueueItemDto(
                contextRow,
                counts,
                enrichment,
                rowOverlay,
                encounter.getPriorityEnum(),
                accountId);
            InpatientAccountBalanceDisplayDtoApplier.apply(base, accountBalanceDisplay);
            // Asm：详情 endTime 展示业务出院时间（与队列同源）
            base.setEndTime(
                businessDischargeTimeResolver.resolve(encounterId, contextRow.getEndTime()));
            // Pre：护理级别展示（BR-IP-NUR-015；口径同列表 IF-IP-NUR-02）
            Map<Long, InpatientPatientQueueNursingLevelDisplayRow> nursingLevelByEncounterId =
                inpatientPatientQueueNursingLevelEnrichAssembler.enrichByEncounterIds(
                    singleEncounter, LocalDate.now());
            this.applyNursingLevelDisplay(base, nursingLevelByEncounterId.get(encounterId));
            // Pre：全角色展示参与者（活动优先，清床/召回后回落末次 COMPLETED）与患者扩展档补全详情增量字段。
            List<EncounterParticipantQueryModel> participantRows =
                encounterQueryService.listDisplayEncounterParticipants(encounterId);
            List<ParticipantDto> participants = this.toParticipantDtos(participantRows);
            PatientDetailQueryModel patientDetail = null;
            if (contextRow.getPatientId() != null) {
                patientDetail = patientQueryService.loadPatientDetail(contextRow.getPatientId());
            }
            String phone = null;
            // 体征卡/详情展示完整证件号（与队列 IF-IP-NUR-02 同源，不脱敏）
            String idCardNo = base.getIdCardNo();
            if (patientDetail != null && patientDetail.getPatient() != null) {
                PatientCoreQueryModel core = patientDetail.getPatient();
                phone = core.getPhone();
                if (StringUtils.isEmpty(idCardNo) && !StringUtils.isEmpty(core.getIdCard())) {
                    idCardNo = core.getIdCard().trim();
                }
            }
            GetPatientDetailResponse response = new GetPatientDetailResponse();
            AdmissionVitalSignSubmitDto admissionVitalSigns = null;
            // Pre：按 start_time 锚点加载入院体征（BR-IP-NUR-014；禁止 getLatestByEncounterId）
            if (encounter.getStartTime() != null) {
                admissionVitalSigns = admissionVitalSignOrchestrator.toSubmitDto(
                    encounterId, encounter.getStartTime());
            }
            // Asm：列表行 + 详情增量（含 admissionVitalSigns）装配响应
            response.setDetail(nurseStationEncounterManageMapper.toPatientDetailDto(
                base, phone, idCardNo, participants, admissionVitalSigns));
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
        Long operatorId = currentPractitionerResolver.requireOperatorId(
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
            // Cmd：可选维护入院体征（须在 saveNurseStationPatient 成功后；规则同 002 BR-IP-ATD-023/024）
            this.saveAdmissionVitalSignsIfPresent(request, encounter, operatorId);
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
     * 从病区位置列表提取 id（与 {@link #loadWardTree} 同一数据源，供队列 geographic 收窄）。
     */
    private List<Long> extractWardLocationIds(List<Location> wards) {
        if (wards == null || wards.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> wardIds = new LinkedHashSet<>();
        for (Location ward : wards) {
            if (ward != null && ward.getId() != null) {
                wardIds.add(ward.getId());
            }
        }
        return new ArrayList<>(wardIds);
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
     * 回填护理级别展示字段至护士站患者列表行（BR-IP-NUR-015）。
     *
     * @param item    列表行 DTO
     * @param nursing 护理级别 enrich 行；无护理时为 null
     */
    private void applyNursingLevelDisplay(
        PatientQueueItemDto item,
        InpatientPatientQueueNursingLevelDisplayRow nursing) {
        if (item == null || nursing == null) {
            return;
        }
        item.setNursingLevelServiceRequestId(nursing.getServiceRequestId());
        item.setNursingLevelActivityId(nursing.getActivityId());
        item.setNursingLevelName(nursing.getActivityName());
        item.setNursingLevelStatusEnum(nursing.getStatusEnum());
    }

    /**
     * 任务筛选 code 白名单校验。
     *
     * @param taskFilter 请求筛选 code
     */
    private void assertTaskFilterAllowed(String taskFilter) {
        if (StringUtils.isEmpty(taskFilter)) {
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
     * 列表状态筛选已废止：仅允许空（默认在院）；召回入口在收银台结算页。
     *
     * @param statusFilter 请求中的 {@code nurseStationStatusFilter}
     */
    private void assertNurseStationStatusFilterRemoved(String statusFilter) {
        if (StringUtils.isEmpty(statusFilter)) {
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "列表状态筛选已停用，请刷新后重试");
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
     * 解析单就诊账户余额展示读模型（批量 Map 预取，禁止 N+1）。
     *
     * @param encounterId                         就诊主键；可 null
     * @param account                             个人现金户；可 null
     * @param pendingSettlementByEncounterId      待结算合计 Map
     * @param settledTotalByEncounterId           已结算合计 Map（已结清文案）
     * @param reversedPendingResettleEncounterIds 反结待再结就诊集合
     * @return 展示读模型
     */
    private InpatientAccountBalanceDisplayResult resolveAccountBalanceDisplay(
        Long encounterId,
        Account account,
        Map<Long, BigDecimal> pendingSettlementByEncounterId,
        Map<Long, BigDecimal> settledTotalByEncounterId,
        Set<Long> reversedPendingResettleEncounterIds) {
        BigDecimal pending = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        boolean reversedPendingResettle = false;
        if (encounterId != null) {
            BigDecimal pendingTotal = pendingSettlementByEncounterId.get(encounterId);
            if (pendingTotal != null) {
                pending = pendingTotal;
            }
            BigDecimal settledTotal = settledTotalByEncounterId.get(encounterId);
            if (settledTotal != null) {
                settled = settledTotal;
            }
            if (reversedPendingResettleEncounterIds != null
                && reversedPendingResettleEncounterIds.contains(encounterId)) {
                reversedPendingResettle = true;
            }
        }
        return InpatientAccountBalanceDisplayAssembler.assemble(
            account,
            pending,
            settled,
            reversedPendingResettle);
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
                if (house != null && StringUtils.isNotEmpty(house.getBusNo())) {
                    houseNameByBusNo.put(house.getBusNo().trim(), house.getName());
                }
            }
        }
        return houseNameByBusNo;
    }

    /**
     * 为当前页读模型行构建展示增补（病房名、检索码、证件号、住址）。
     *
     * <p>患者主档与床位位置均按当前页主键批量装载，禁止逐患者/逐床位查询（§2.1 #9）。</p>
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
        Set<Long> bedIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
            if (row.getBedId() != null) {
                bedIds.add(row.getBedId());
            }
        }
        Map<Long, String> pinyinByPatientId = new HashMap<>();
        Map<Long, String> wubiByPatientId = new HashMap<>();
        Map<Long, String> idCardByPatientId = new HashMap<>();
        Map<Long, String> addressByPatientId = new HashMap<>();
        Map<Long, String> phoneByPatientId = new HashMap<>();
        Map<Long, String> patientBusNoByPatientId = new HashMap<>();
        // Pre：读模型未投影检索码/证件/住址/电话，当前页患者主键一次性批量补全（禁 N+1）。
        Map<Long, BatchPatientDetailQueryModel> batchDetails =
            patientQueryService.batchLoadPatientDetails(new ArrayList<>(patientIds));
        for (Long patientId : patientIds) {
            BatchPatientDetailQueryModel batchItem = batchDetails.get(patientId);
            if (batchItem == null || batchItem.getPatientDetail() == null) {
                continue;
            }
            PatientDetailQueryModel detail = batchItem.getPatientDetail();
            if (detail.getPatient() == null) {
                continue;
            }
            PatientCoreQueryModel core = detail.getPatient();
            pinyinByPatientId.put(patientId, core.getPyStr());
            wubiByPatientId.put(patientId, core.getWbStr());
            // 大屏/队列展示完整证件号，不做脱敏。
            if (!StringUtils.isEmpty(core.getIdCard())) {
                idCardByPatientId.put(patientId, core.getIdCard().trim());
            }
            String addressText = this.resolvePatientAddressText(core);
            if (addressText != null) {
                addressByPatientId.put(patientId, addressText);
            }
            if (!StringUtils.isEmpty(core.getPhone())) {
                phoneByPatientId.put(patientId, core.getPhone().trim());
            }
            if (!StringUtils.isEmpty(core.getBusNo())) {
                patientBusNoByPatientId.put(patientId, core.getBusNo().trim());
            }
        }
        // Pre：当前页床位位置批量装载，供病房名前缀匹配（禁逐床 findOptionalById）。
        Map<Long, Location> bedById = locationQueryService.loadLocationEntityMapByIds(bedIds);
        Set<Long> encounterIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        // Pre：医保人员编号/医疗类别批量回填（平台映射落库；SPI 未启用时为空）。
        Map<Long, InsuranceEncounterPsnSnapshot> psnByEncounterId =
            insuranceRegisterBenefitQueryExtension.mapEncounterPsnByEncounterIds(encounterIds);
        // Pre：医保账户险种类别批量回填。
        Map<Long, Account> miAccountByEncounterId =
            accountQueryService.mapMedicalInsuranceAccountsByEncounterIds(encounterIds);
        // Pre：入院途径术语码 → 展示名。
        Map<String, String> inWayLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.IN_WAY_CODE);
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            NurseStationQueueDisplayEnrichment enrichment = new NurseStationQueueDisplayEnrichment();
            Location bed = null;
            if (row.getBedId() != null) {
                bed = bedById.get(row.getBedId());
            }
            enrichment.setHouseName(this.resolveHouseName(bed, houseNameByBusNo));
            if (row.getPatientId() != null) {
                enrichment.setPinyinCode(pinyinByPatientId.get(row.getPatientId()));
                enrichment.setWubiCode(wubiByPatientId.get(row.getPatientId()));
                enrichment.setIdCardNo(idCardByPatientId.get(row.getPatientId()));
                enrichment.setAddress(addressByPatientId.get(row.getPatientId()));
                enrichment.setPatientPhone(phoneByPatientId.get(row.getPatientId()));
                enrichment.setPatientBusNo(patientBusNoByPatientId.get(row.getPatientId()));
            }
            if (!StringUtils.isEmpty(row.getInWayCode())) {
                String inWayCode = row.getInWayCode().trim();
                enrichment.setInWayCode(inWayCode);
                enrichment.setInWayCodeText(inWayLabelByCode.get(inWayCode));
            }
            Account miAccount = miAccountByEncounterId.get(row.getEncounterId());
            if (miAccount != null && !StringUtils.isEmpty(miAccount.getInsutype())) {
                enrichment.setInsutype(miAccount.getInsutype().trim());
            }
            InsuranceEncounterPsnSnapshot psnSnapshot = psnByEncounterId.get(row.getEncounterId());
            if (psnSnapshot != null) {
                enrichment.setInsuranceNo(psnSnapshot.getPsnNo());
                enrichment.setMedType(psnSnapshot.getMedType());
                enrichment.setMedTypeDisplay(psnSnapshot.getMedTypeDisplay());
            }
            result.put(row.getEncounterId(), enrichment);
        }
        return result;
    }

    /**
     * 拼装患者住址展示文案：优先全文，否则省市区街道拼接。
     *
     * @param core 患者核心读模型
     * @return 非空住址或 null
     */
    private String resolvePatientAddressText(PatientCoreQueryModel core) {
        if (core == null) {
            return null;
        }
        if (!StringUtils.isEmpty(core.getAddress())) {
            return core.getAddress().trim();
        }
        StringBuilder sb = new StringBuilder();
        this.appendAddressPart(sb, core.getAddressProvince());
        this.appendAddressPart(sb, core.getAddressCity());
        this.appendAddressPart(sb, core.getAddressDistrict());
        this.appendAddressPart(sb, core.getAddressStreet());
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 向住址缓冲追加一段非空区划文案。
     *
     * @param sb   缓冲
     * @param part 区划片段
     */
    private void appendAddressPart(StringBuilder sb, String part) {
        if (StringUtils.isEmpty(part)) {
            return;
        }
        sb.append(part.trim());
    }

    /**
     * 由已装载的床位实体 bus_no 前缀匹配病房名称（调用方须批量预取床位，禁本方法内再查库）。
     *
     * @param bed              床位位置实体；空则无法解析
     * @param houseNameByBusNo 病房 bus_no 索引
     * @return 病房名或 null
     */
    private String resolveHouseName(Location bed, Map<String, String> houseNameByBusNo) {
        if (bed == null || houseNameByBusNo == null || houseNameByBusNo.isEmpty()) {
            return null;
        }
        if (StringUtils.isEmpty(bed.getBusNo())) {
            return null;
        }
        String bedBusNo = bed.getBusNo().trim();
        String matchedName = null;
        int matchedPrefixLen = 0;
        for (Map.Entry<String, String> entry : houseNameByBusNo.entrySet()) {
            String houseBusNo = entry.getKey();
            if (StringUtils.isEmpty(houseBusNo)) {
                continue;
            }
            String prefix = houseBusNo.trim();
            // 取最长前缀命中，避免短 bus_no 误匹配其它病房
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
     * <p><b>校 pendingAuditCount</b>：
     * （1）药品+诊疗中 {@code status=ACTIVE} 且来源为医生开立的行数；
     * （2）另计 {@code status=STOPPED}、长期、医生开立且 {@code stopPerformerCheckId == null} 的停嘱待校行；
     * 不计护士划价及其它非医生开立来源；与校对列表一致<strong>禁止用 based_on 排除</strong>
     *（BR-IP-AUD-002）。住院退药不造负向 Request；{@code refund_*_id}/负数量仅为门诊同源防御。</p>
     * <p><b>执 pendingExecuteCount</b>（仅 {@code includePendingExecute=true}）：药品+诊疗中 {@code status=VERIFIED}、非停嘱、医生开立，且
     * <b>开嘱日起至统计日截止前仍有未终态计划槽</b>的医嘱行数（与待执行 Tab 对齐：含跨日未完成临时/出院带药与昨日未完成长期槽；
     * <strong>可拆</strong>长期仍按频次预计点判定（与改一盒一点前一致）；
     * <strong>仅全局不可拆且资格命中</strong>的长期药品按<strong>一盒一点盒首锚点</strong>判定（IF-IP-EXE-01 / **BR-IP-EXE-035**）；
     * 已执行与不执行均为终态，不算待执行；按行 +1 不按槽累加）；
     * 不计护士划价、床位滚费、转科/出院/护理级别；
     * <strong>皮试检查 companion SR 与皮试药均计入</strong>（执行/计费走 AS-IP-NUR-004，结果登记见 AS-IP-NUR-011）。
     * 列表默认「全部任务」传 {@code false}，避免对全员展槽；待执行明细由右侧页签按选中患者拉取。</p>
     *
     * @param encounterIds           就诊主键集合
     * @param executionDate          待执行统计自然日（仅 includePendingExecute 时使用）
     * @param includePendingExecute  是否计算待执行计数（展槽极重；默认列表应 false）
     * @return encounterId → 计数包
     */
    private Map<Long, NurseStationTaskCountBundle> buildTaskCountsForEncounters(
        Collection<Long> encounterIds,
        LocalDate executionDate,
        boolean includePendingExecute) {
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
        String longTermCode = TherapyType.LONG_TERM.getCode();
        String doctorPrescriptionSourceCode = GenerateSource.DOCTOR_PRESCRIPTION.getCode();
        List<MedicationRequest> activeMeds =
            medicationRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, activeCode);
        List<ServiceRequest> activeServices =
            serviceRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, activeCode);
        for (MedicationRequest row : activeMeds) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 校：仅医生开立 ACTIVE；护士划价/滚费等非医生开立一律不计（BR-IP-NUR-004）。
            if (!doctorPrescriptionSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            // 校：退药/派生关联行不进校对队列，计数同步排除（BR-IP-AUD-002；否则会出现「校 N、西药/中药均空」）。
            if (this.isExcludedFromPendingAuditCountMedication(row)) {
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
            // 校：诊疗同口径，仅医生开立 ACTIVE。
            if (!doctorPrescriptionSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            // 校：退诊疗/负向派生行不进校对队列，计数同步排除（BR-IP-AUD-002）。
            if (this.isExcludedFromPendingAuditCountService(row)) {
                continue;
            }
            NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
            if (bundle != null) {
                bundle.setPendingAuditCount(bundle.getPendingAuditCount() + 1);
            }
        }
        // 校：另扫 STOPPED 医生开立长期且尚未停嘱校对的行，计入 pendingAuditCount（与 pending Tab 停嘱待校对齐）
        List<MedicationRequest> stoppedMeds =
            medicationRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, stoppedCode);
        List<ServiceRequest> stoppedServices =
            serviceRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, stoppedCode);
        for (MedicationRequest row : stoppedMeds) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            if (!doctorPrescriptionSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            if (!longTermCode.equals(row.getTherapyEnum())) {
                continue;
            }
            if (row.getStopPerformerCheckId() != null) {
                continue;
            }
            if (this.isExcludedFromPendingAuditCountMedication(row)) {
                continue;
            }
            NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
            if (bundle != null) {
                bundle.setPendingAuditCount(bundle.getPendingAuditCount() + 1);
            }
        }
        for (ServiceRequest row : stoppedServices) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            if (!doctorPrescriptionSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            if (!longTermCode.equals(row.getTherapyEnum())) {
                continue;
            }
            if (row.getStopPerformerCheckId() != null) {
                continue;
            }
            if (this.isExcludedFromPendingAuditCountService(row)) {
                continue;
            }
            NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
            if (bundle != null) {
                bundle.setPendingAuditCount(bundle.getPendingAuditCount() + 1);
            }
        }
        // 默认列表 / 仅待校对筛选：不算待执行展槽，pendingExecute 保持 0。
        if (!includePendingExecute) {
            return result;
        }
        if (executionDate == null) {
            return result;
        }
        List<MedicationRequest> verifiedMeds =
            medicationRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, verifiedCode);
        List<ServiceRequest> verifiedServices =
            serviceRequestQueryService.listByEncounterIdsAndStatusEnum(idSet, verifiedCode);
        List<Procedure> procedures = procedureQueryService.listByEncounterIds(idSet);
        LocalDateTime dayEnd = executionDate.plusDays(1).atStartOfDay();
        // Pre：按请求锚点索引 Procedure，供跨日计划槽终态判定（与 IF-IP-EXE-01 待执行 Tab 对齐）。
        Map<String, List<Procedure>> proceduresByAnchor = this.indexProceduresByRequestAnchor(procedures);
        // Pre：目录/用法术语，药嘱待执与执行列表一盒一点/拆零下限切批同源。
        boolean applyStandingOrderPackageBatch = !standingOrderSplittableSettingResolver.isSplittable();
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey =
            this.loadMedicationProductsForPendingExecute(verifiedMeds);
        Map<String, String> methodLabelByCode =
            terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.METHOD_CODE);
        this.addPendingExecuteForMedicationRequests(
            verifiedMeds,
            stoppedCode,
            doctorPrescriptionSourceCode,
            dayEnd,
            proceduresByAnchor,
            applyStandingOrderPackageBatch,
            medicationProductByKey,
            methodLabelByCode,
            result);
        this.addPendingExecuteForServiceRequests(
            verifiedServices,
            stoppedCode,
            doctorPrescriptionSourceCode,
            dayEnd,
            proceduresByAnchor,
            result);
        return result;
    }

    /**
     * 是否需要在列表路径计算待执行计数（展槽极重）。
     *
     * <p>空筛选（全部任务）与 pending-audit 不算执；pending-execute / processed 才算。</p>
     *
     * @param taskFilter 任务筛选 code；空表示全部任务
     * @return true 表示须展开待执行槽并写入 pendingExecuteCount
     */
    private boolean shouldIncludePendingExecuteCounts(String taskFilter) {
        if (StringUtils.isEmpty(taskFilter)) {
            return false;
        }
        if (InpatientNurseStationConstants.TASK_FILTER_PENDING_EXECUTE.equals(taskFilter)) {
            return true;
        }
        if (InpatientNurseStationConstants.TASK_FILTER_PROCESSED.equals(taskFilter)) {
            return true;
        }
        return false;
    }

    /**
     * 药品行是否应排除出待校对计数（与校对列表 BR-IP-AUD-002 同源）。
     *
     * <p>住院退药不造负向 Request；{@code refund_medicine_id} 排除仅为门诊同源防御。
     * 禁止用 based_on 顶替。</p>
     *
     * @param row 药品申请
     * @return true 表示负向药嘱行（住院正常数据通常不命中），不计校
     */
    private boolean isExcludedFromPendingAuditCountMedication(MedicationRequest row) {
        if (row == null) {
            return true;
        }
        return row.getRefundMedicineId() != null;
    }

    /**
     * 诊疗行是否应排除出待校对计数（与校对列表 BR-IP-AUD-002 同源）。
     *
     * <p>住院取消执行回退账单，不造负向诊嘱 Request；{@code refund_service_id}/负数量仅为防御。
     * 禁止因 based_on 排除（皮试检查等可挂 based_on）。</p>
     *
     * @param row 诊疗申请
     * @return true 表示负向诊嘱行（住院正常数据通常不命中），不计校
     */
    private boolean isExcludedFromPendingAuditCountService(ServiceRequest row) {
        if (row == null) {
            return true;
        }
        if (row.getRefundServiceId() != null) {
            return true;
        }
        if (row.getQuantity() != null && row.getQuantity().signum() < 0) {
            return true;
        }
        return false;
    }

    /**
     * 按 request_table + request_id 锚点索引执行记录。
     *
     * @param procedures 就诊下执行记录
     * @return 锚点 → Procedure 列表
     */
    private Map<String, List<Procedure>> indexProceduresByRequestAnchor(List<Procedure> procedures) {
        Map<String, List<Procedure>> byAnchor = new HashMap<>();
        if (procedures == null || procedures.isEmpty()) {
            return byAnchor;
        }
        char separator = GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR;
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getRequestId() == null
                || StringUtils.isEmpty(procedure.getRequestTable())) {
                continue;
            }
            String anchor = procedure.getRequestTable() + separator + procedure.getRequestId();
            List<Procedure> bucket = byAnchor.get(anchor);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byAnchor.put(anchor, bucket);
            }
            bucket.add(procedure);
        }
        return byAnchor;
    }

    /**
     * 将 VERIFIED 药品请求中「医生开立、非停嘱、截止日前仍有未终态计划槽」的行计入待执行（按医嘱行 +1）。
     *
     * <p>窗口 = 开嘱日起～统计日结束（含跨日未完成槽、临时/出院带药不限开立日）；
     * 长期在全局不可拆且资格命中时按盒首锚点判定（**BR-IP-EXE-035**），与待执行 Tab 行口径对齐；
     * 已执行与不执行均为终态。</p>
     *
     * @param requests                         药品申请列表
     * @param stoppedCode                      停嘱状态 code
     * @param doctorPrescriptionSourceCode     医生开立来源 code
     * @param dayEnd                           统计日终点（不含）
     * @param proceduresByAnchor               请求锚点 → Procedure
     * @param applyStandingOrderPackageBatch   全局不可拆时为 true
     * @param medicationProductByKey           药品复合键 → 目录
     * @param methodLabelByCode                用法 code → 展示名
     * @param result                           输出计数包
     */
    private void addPendingExecuteForMedicationRequests(
        List<MedicationRequest> requests,
        String stoppedCode,
        String doctorPrescriptionSourceCode,
        LocalDateTime dayEnd,
        Map<String, List<Procedure>> proceduresByAnchor,
        boolean applyStandingOrderPackageBatch,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey,
        Map<String, String> methodLabelByCode,
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
            // 执：已停嘱不计入。
            if (stoppedCode.equals(row.getStatusEnum())) {
                continue;
            }
            // 执：仅医生开立。
            if (!doctorPrescriptionSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            // 待执行槽展开左界：优先开立时刻，回落签发，再回落创建审计时间。
            LocalDateTime openedTime = row.getReqOpenedTime();
            if (openedTime == null) {
                openedTime = row.getReqAuthoredTime();
            }
            if (openedTime == null) {
                openedTime = row.getCreatedAt();
            }
            String anchor = requestTable + separator + row.getId();
            List<Procedure> requestProcedures = proceduresByAnchor.get(anchor);
            // 截止日前仍有 pending/hedged pending 槽 → 整行计 1（药嘱长期走一盒一点同源展槽）。
            if (this.hasUnfinishedMedicationExecuteSlots(
                row,
                openedTime,
                dayEnd,
                requestProcedures,
                applyStandingOrderPackageBatch,
                medicationProductByKey,
                methodLabelByCode)) {
                NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
                if (bundle != null) {
                    bundle.setPendingExecuteCount(bundle.getPendingExecuteCount() + 1);
                }
            }
        }
    }

    /**
     * 药品行截止日前是否仍有未终态计划槽（临时整单 / 长期可拆日点或一盒一点盒首）。
     *
     * <p><b>可拆</b>（{@code applyStandingOrderPackageBatch=false}）：完全回落历史
     * {@link #hasUnfinishedExecuteSlots}（频次展开至 {@code dayEnd}），保证与改一盒一点前统计一致。
     * <b>不可拆</b>：长期药经 {@link StandingOrderPackageBatchPlanTimeResolver} 取盒首锚点。</p>
     *
     * @param row                            药品申请
     * @param openedTime                     开立锚点
     * @param dayEnd                         统计截止（不含）
     * @param requestProcedures              该请求 Procedure
     * @param applyStandingOrderPackageBatch 是否尝试一盒一点
     * @param medicationProductByKey         目录 Map
     * @param methodLabelByCode              用法展示名
     * @return true 表示应计入待执行行
     */
    private boolean hasUnfinishedMedicationExecuteSlots(
        MedicationRequest row,
        LocalDateTime openedTime,
        LocalDateTime dayEnd,
        List<Procedure> requestProcedures,
        boolean applyStandingOrderPackageBatch,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey,
        Map<String, String> methodLabelByCode) {
        if (row == null || openedTime == null || dayEnd == null) {
            return false;
        }
        // 开立晚于截止：尚无计划。
        if (!openedTime.isBefore(dayEnd)) {
            return false;
        }
        List<Procedure> procedures = requestProcedures;
        if (procedures == null) {
            procedures = Collections.emptyList();
        }
        // 临时 / 出院带药：整单单槽 = 开立时刻（不可拆开关不影响临嘱）。
        if (!TherapyType.LONG_TERM.getCode().equals(row.getTherapyEnum())) {
            return this.isTemporarySlotStillPending(procedures);
        }
        if (StringUtils.isEmpty(row.getRateCode())) {
            // 长期无频次无法展开，与执行列表剔除一致。
            return false;
        }
        Set<LocalDateTime> planned;
        ProductDefinitionDisplayDto product = null;
        boolean packageBatchApplied = false;
        boolean minSplitUnitBatchApplied = false;
        try {
            StandingOrderPackageBatchPlanTimeResolver.MeasureInput measure =
                new StandingOrderPackageBatchPlanTimeResolver.MeasureInput();
            measure.setTherapyEnum(row.getTherapyEnum());
            measure.setQuantity(row.getQuantity());
            measure.setUnitCode(row.getUnitCode());
            measure.setFirstDose(row.getFirstDose());
            measure.setFirstDoseUnitCode(row.getFirstDoseUnitCode());
            measure.setMethodCode(row.getMethodCode());
            measure.setQuantitySchedule(row.getQuantitySchedule());
            measure.setRateCode(row.getRateCode());
            product = this.resolveMedicationProductForPendingExecute(
                row.getMedicationId(), medicationProductByKey);
            // 统计日末作 selectionDeadline；含院点一盒一点与产品拆零下限切批（BR-IP-EXE-035/036）
            LocalDateTime selectionDeadline = dayEnd.minusSeconds(1);
            StandingOrderPackageBatchPlanTimeResolver.ExpandResult expand =
                standingOrderPackageBatchPlanTimeResolver.resolveLongTermPlanTimes(
                    row.getRateCode(),
                    openedTime,
                    selectionDeadline,
                    null,
                    applyStandingOrderPackageBatch,
                    measure,
                    product,
                    methodLabelByCode);
            planned = expand.getPlanTimes();
            packageBatchApplied = expand.isPackageBatchApplied();
            minSplitUnitBatchApplied = expand.isMinSplitUnitBatchApplied();
        } catch (BusinessException ex) {
            // 频次不可用：不计待执行（与历史 resolveLongTermPlannedTimesThrough 空集口径一致）
            return false;
        }
        if (planned == null || planned.isEmpty()) {
            // 无可展计划槽（含不可拆硬顶未凑满盒 → 空）→ 不计待执行
            return false;
        }
        // 单位领用剩余按天抑制：与 IF-IP-EXE-01 / 医生站 enrich 同源（BR-IP-EXE-038；schedule 只改正算量）
        if ((packageBatchApplied
                || minSplitUnitBatchApplied
                || applyStandingOrderPackageBatch)
            && product != null) {
            StandingOrderOralPackageInventorySupport.UnitClaimMode preferredMode =
                StandingOrderOralPackageInventorySupport.UnitClaimMode.PACKAGE;
            if (minSplitUnitBatchApplied && !packageBatchApplied) {
                preferredMode = StandingOrderOralPackageInventorySupport.UnitClaimMode.MIN_SPLIT;
            }
            Map<Long, List<ProcedureRecordQueryModel>> proceduresByRequestId = new HashMap<>();
            List<MedicationRequest> chain =
                standingOrderOralPackageInventorySupport.loadDoseChangeChain(row.getId());
            for (MedicationRequest mr : chain) {
                if (mr == null || mr.getId() == null) {
                    continue;
                }
                proceduresByRequestId.put(
                    mr.getId(),
                    procedureQueryService.getProcedureRecords(
                        mr.getId(), TableNameConstants.MED_MEDICATION_REQUEST));
            }
            // dayEnd 为统计截止（不含）：asOf 取截止前一自然日末所在日
            LocalDate asOf = dayEnd.minusSeconds(1).toLocalDate();
            StandingOrderOralPackageInventorySupport.OralPackageInventorySnapshot snap =
                standingOrderOralPackageInventorySupport.compute(
                    row,
                    proceduresByRequestId,
                    product,
                    methodLabelByCode,
                    asOf,
                    preferredMode,
                    applyStandingOrderPackageBatch);
            if (!snap.isApplied()
                && StandingOrderOralPackageInventorySupport.UnitClaimMode.PACKAGE.equals(preferredMode)) {
                snap = standingOrderOralPackageInventorySupport.compute(
                    row,
                    proceduresByRequestId,
                    product,
                    methodLabelByCode,
                    asOf,
                    StandingOrderOralPackageInventorySupport.UnitClaimMode.MIN_SPLIT,
                    applyStandingOrderPackageBatch);
            }
            Set<LocalDateTime> filtered = new LinkedHashSet<>();
            for (LocalDateTime planTime : planned) {
                if (planTime == null) {
                    continue;
                }
                if (standingOrderOralPackageInventorySupport.shouldSuppressPending(
                    snap, planTime.toLocalDate())) {
                    continue;
                }
                filtered.add(planTime);
            }
            // 与 IF-IP-EXE-01 同源：038⑥ 领用日仅锚首频次一点（日点残留时收成单点）
            LocalDate nextClaim = snap.getNextClaimDate();
            if (snap.getClaimedSplitQuantity() != null
                && snap.getClaimedSplitQuantity().compareTo(BigDecimal.ZERO) > 0
                && nextClaim != null
                && !nextClaim.isAfter(asOf)) {
                int totalKept = 0;
                int onNextClaim = 0;
                for (LocalDateTime planTime : filtered) {
                    if (planTime == null) {
                        continue;
                    }
                    totalKept++;
                    if (nextClaim.equals(planTime.toLocalDate())) {
                        onNextClaim++;
                    }
                }
                if (!(totalKept == 1 && onNextClaim == 1)) {
                    LocalDateTime anchor =
                        standingOrderOralPackageInventorySupport.resolveNextClaimPendingPlanTime(
                            row, nextClaim);
                    if (anchor != null) {
                        LocalDateTime normalized = anchor.withNano(0);
                        List<ProcedureRecordQueryModel> claimProcs =
                            proceduresByRequestId.get(row.getId());
                        // Gate：不执行/净已执行等同 occurrence 终态，禁止补回假 pending
                        if (standingOrderOralPackageInventorySupport.allowsNextClaimPendingReinjection(
                            claimProcs, normalized)) {
                            filtered.clear();
                            filtered.add(normalized);
                        }
                    }
                }
            }
            planned = filtered;
            if (planned.isEmpty()) {
                return false;
            }
        }
        for (LocalDateTime planTime : planned) {
            if (planTime == null) {
                continue;
            }
            // 任一盒首/单位首仍 pending / hedged pending → 整行待执行（行 +1，不按槽累加）。
            if (this.isLongTermOccurrenceStillPending(procedures, planTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量加载长期药品目录，供待执计数一盒一点取包装拆零比。
     *
     * @param requests 已校对药品申请
     * @return 复合键 → 目录
     */
    private Map<String, ProductDefinitionDisplayDto> loadMedicationProductsForPendingExecute(
        List<MedicationRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (MedicationRequest row : requests) {
            if (row == null || row.getMedicationId() == null) {
                continue;
            }
            if (!TherapyType.LONG_TERM.getCode().equals(row.getTherapyEnum())) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            if (key == null || seenKeys.contains(key)) {
                continue;
            }
            seenKeys.add(key);
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(row.getMedicationId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Collections.emptyMap();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 从预热 Map 解析药品目录。
     *
     * @param medicationId           药品定义主键
     * @param medicationProductByKey 预热 Map
     * @return 目录；缺失时 null
     */
    private ProductDefinitionDisplayDto resolveMedicationProductForPendingExecute(
        Long medicationId,
        Map<String, ProductDefinitionDisplayDto> medicationProductByKey) {
        if (medicationId == null || medicationProductByKey == null || medicationProductByKey.isEmpty()) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        if (key == null) {
            return null;
        }
        return medicationProductByKey.get(key);
    }

    /**
     * 将 VERIFIED 诊疗请求中「医生开立、非停嘱、截止日前仍有未终态计划槽」的行计入待执行。
     *
     * @param requests                     诊疗申请列表
     * @param stoppedCode                  停嘱状态 code
     * @param doctorPrescriptionSourceCode 医生开立来源 code
     * @param dayEnd                       统计日终点（不含）
     * @param proceduresByAnchor           请求锚点 → Procedure
     * @param result                       输出计数包
     */
    private void addPendingExecuteForServiceRequests(
        List<ServiceRequest> requests,
        String stoppedCode,
        String doctorPrescriptionSourceCode,
        LocalDateTime dayEnd,
        Map<String, List<Procedure>> proceduresByAnchor,
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
            if (!doctorPrescriptionSourceCode.equals(row.getGenerateSourceEnum())) {
                continue;
            }
            if (this.shouldExcludeServiceRequestFromPendingExecuteCount(row)) {
                continue;
            }
            // 待执行槽展开左界：优先开立时刻，回落签发，再回落创建审计时间。
            LocalDateTime openedTime = row.getReqOpenedTime();
            if (openedTime == null) {
                openedTime = row.getReqAuthoredTime();
            }
            if (openedTime == null) {
                openedTime = row.getCreatedAt();
            }
            String anchor = requestTable + separator + row.getId();
            List<Procedure> requestProcedures = proceduresByAnchor.get(anchor);
            if (this.hasUnfinishedExecuteSlots(
                row.getTherapyEnum(),
                row.getRateCode(),
                openedTime,
                dayEnd,
                requestProcedures)) {
                NurseStationTaskCountBundle bundle = result.get(row.getEncounterId());
                if (bundle != null) {
                    bundle.setPendingExecuteCount(bundle.getPendingExecuteCount() + 1);
                }
            }
        }
    }

    /**
     * 待执行计数是否排除该诊疗申请（与执行列表 {@code shouldExcludeExecuteRow} 同源；皮试检查计入待执行）。
     *
     * @param row 诊疗申请
     * @return true 表示不计入 pendingExecuteCount
     */
    private boolean shouldExcludeServiceRequestFromPendingExecuteCount(ServiceRequest row) {
        if (row == null) {
            return true;
        }
        // 护士划价不进执行 Tab。
        if (GenerateSource.NURSE_PRICING.getCode().equals(row.getGenerateSourceEnum())) {
            return true;
        }
        // 床位滚费自动行不进手动执行。
        if (GenerateSource.AUTO_ROLL_FEES.getCode().equals(row.getGenerateSourceEnum())) {
            return true;
        }
        // 转科/出院/护理级别等特殊诊疗类别。
        if (nurseStationOrderViewListAssembler.isExcludedFromNurseStationExecuteList(row.getCategoryEnum())) {
            return true;
        }
        return false;
    }

    /**
     * 截止日前是否仍有未终态计划槽（pending / hedged pending）。
     *
     * <p>临时/出院带药：整单单槽，不限开立是否为统计日（跨日未完成仍计）。
     * 长期：频次展开至 {@code dayEnd}，任一计划点未终态（已执行/不执行）即计。</p>
     *
     * @param therapyEnum        长临 code
     * @param rateCode           频次；长期必填
     * @param openedTime         开立时刻（{@code req_opened_time}；空回落后的锚点）
     * @param dayEnd             统计截止（不含）
     * @param requestProcedures  该请求下 Procedure；可空
     * @return true 表示应计入待执行行
     */
    private boolean hasUnfinishedExecuteSlots(
        String therapyEnum,
        String rateCode,
        LocalDateTime openedTime,
        LocalDateTime dayEnd,
        List<Procedure> requestProcedures) {
        if (openedTime == null || dayEnd == null) {
            return false;
        }
        // 开立晚于截止：尚无计划。
        if (!openedTime.isBefore(dayEnd)) {
            return false;
        }
        List<Procedure> procedures = requestProcedures;
        if (procedures == null) {
            procedures = Collections.emptyList();
        }
        // 临时 / 出院带药：整单单槽 = 开立时刻；已执行或不执行终态则不计。
        if (!TherapyType.LONG_TERM.getCode().equals(therapyEnum)) {
            return this.isTemporarySlotStillPending(procedures);
        }
        if (StringUtils.isEmpty(rateCode)) {
            // 长期无频次无法展开，与执行列表剔除一致。
            return false;
        }
        Set<LocalDateTime> planned = this.resolveLongTermPlannedTimesThrough(rateCode, openedTime, dayEnd);
        if (planned.isEmpty()) {
            // 必要时等无时钟点：无计划槽，不计待执行。
            return false;
        }
        for (LocalDateTime planTime : planned) {
            if (planTime == null) {
                continue;
            }
            // 任一计划点仍 pending / hedged pending → 整行待执行。
            if (this.isLongTermOccurrenceStillPending(procedures, planTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 临时/出院带药整单是否仍待执行（净 COMPLETED 或 不执行终态则否；对冲后仍待执行）。
     *
     * @param procedures 该请求 Procedure
     * @return true 表示仍待执行
     */
    private boolean isTemporarySlotStillPending(List<Procedure> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return true;
        }
        // 存在未被对冲的净 COMPLETED → 已执行终态。
        if (this.findLastNetCompletedProcedure(procedures) != null) {
            return false;
        }
        Procedure finalRecord = this.pickLatestProcedure(procedures);
        if (finalRecord == null) {
            return true;
        }
        String status = finalRecord.getStatusEnum();
        // 不执行是终态，不算待执行。
        if (EventStatus.STOPPED.getCode().equals(status)) {
            return false;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            // 带 refund 的取消 = 对冲后净值仍待执行。
            if (finalRecord.getRefundId() != null) {
                return true;
            }
            // 孤立取消：列表进取消 Tab，不进待执行。
            return false;
        }
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            // 末态 COMPLETED 已被对冲 → 仍待执行。
            if (this.isProcedureOffsetByCancel(procedures, finalRecord.getId())) {
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 长期单 occurrence 是否仍待执行。
     *
     * @param procedures 该请求全部 Procedure
     * @param planTime   计划时点
     * @return true 表示该点仍 pending / hedged pending
     */
    private boolean isLongTermOccurrenceStillPending(List<Procedure> procedures, LocalDateTime planTime) {
        List<Procedure> group = this.findProceduresAtOccurrence(procedures, planTime);
        if (group.isEmpty()) {
            // 尚无 Procedure → 待执行。
            return true;
        }
        Procedure finalRecord = this.pickLatestProcedure(group);
        if (finalRecord == null) {
            return true;
        }
        String status = finalRecord.getStatusEnum();
        if (EventStatus.COMPLETED.getCode().equals(status)) {
            // 已被取消对冲 → 净值待执行。
            if (this.isProcedureOffsetByCancel(procedures, finalRecord.getId())) {
                return true;
            }
            return false;
        }
        // 不执行终态，不算待执行。
        if (EventStatus.STOPPED.getCode().equals(status)) {
            return false;
        }
        if (EventStatus.isCancelExecutionStatusCode(status)) {
            if (finalRecord.getRefundId() != null) {
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * 长期频次展开至统计截止（含开立日首次槽裁剪）。
     *
     * @param rateCode   频次
     * @param openedTime 开立时刻（{@code req_opened_time}）
     * @param dayEnd     截止（不含）
     * @return 计划时点集合
     */
    private Set<LocalDateTime> resolveLongTermPlannedTimesThrough(
        String rateCode,
        LocalDateTime openedTime,
        LocalDateTime dayEnd) {
        LocalDateTime expandStart = openedTime.toLocalDate().atStartOfDay();
        if (!dayEnd.isAfter(expandStart)) {
            return Collections.emptySet();
        }
        List<LocalDateTime> rawTimes = new ArrayList<>();
        try {
            List<FrequencyExecutionTimePointQueryModel> points =
                frequencyQueryService.computeExecutionTimePoints(rateCode, expandStart, dayEnd);
            for (FrequencyExecutionTimePointQueryModel point : points) {
                if (point != null && point.getExecutionAt() != null) {
                    rawTimes.add(point.getExecutionAt());
                }
            }
        } catch (BusinessException ex) {
            return Collections.emptySet();
        }
        return LongTermFirstExecutionSlotSupport.applyOpenedDayFirstSlotRule(rawTimes, openedTime);
    }

    /**
     * 按 occurrence 对齐查找 Procedure（秒级，忽略纳米）。
     *
     * @param procedures 请求下 Procedure
     * @param planTime   计划时点
     * @return 同 occurrence 组
     */
    private List<Procedure> findProceduresAtOccurrence(List<Procedure> procedures, LocalDateTime planTime) {
        List<Procedure> group = new ArrayList<>();
        if (procedures == null || planTime == null) {
            return group;
        }
        LocalDateTime normalizedPlan = planTime.withNano(0);
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getOccurrenceTime() == null) {
                continue;
            }
            if (procedure.getOccurrenceTime().withNano(0).equals(normalizedPlan)) {
                group.add(procedure);
            }
        }
        return group;
    }

    /**
     * 取 id 最大的 Procedure 作为组内末态。
     *
     * @param procedures Procedure 列表
     * @return 末态行；空则 null
     */
    private Procedure pickLatestProcedure(List<Procedure> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        Procedure latest = null;
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getId() == null) {
                continue;
            }
            if (latest == null || procedure.getId() > latest.getId()) {
                latest = procedure;
            }
        }
        return latest;
    }

    /**
     * 取未被 CANCEL 对冲的最后一条 COMPLETED。
     *
     * @param procedures 请求下 Procedure
     * @return 净 COMPLETED；无则 null
     */
    private Procedure findLastNetCompletedProcedure(List<Procedure> procedures) {
        if (procedures == null || procedures.isEmpty()) {
            return null;
        }
        List<Procedure> completed = new ArrayList<>();
        for (Procedure procedure : procedures) {
            if (procedure == null) {
                continue;
            }
            if (EventStatus.COMPLETED.getCode().equals(procedure.getStatusEnum())) {
                completed.add(procedure);
            }
        }
        completed.sort((left, right) -> {
            Long leftId = left.getId();
            Long rightId = right.getId();
            if (leftId == null && rightId == null) {
                return 0;
            }
            if (leftId == null) {
                return -1;
            }
            if (rightId == null) {
                return 1;
            }
            return leftId.compareTo(rightId);
        });
        for (int index = completed.size() - 1; index >= 0; index--) {
            Procedure row = completed.get(index);
            if (!this.isProcedureOffsetByCancel(procedures, row.getId())) {
                return row;
            }
        }
        return null;
    }

    /**
     * COMPLETED 是否已被 CANCEL.refund_id 对冲。
     *
     * @param procedures           同请求 Procedure
     * @param completedProcedureId COMPLETED 主键
     * @return true 表示已被对冲
     */
    private boolean isProcedureOffsetByCancel(List<Procedure> procedures, Long completedProcedureId) {
        if (procedures == null || completedProcedureId == null) {
            return false;
        }
        for (Procedure sibling : procedures) {
            if (sibling == null) {
                continue;
            }
            if (!EventStatus.isCancelExecutionStatusCode(sibling.getStatusEnum())) {
                continue;
            }
            if (completedProcedureId.equals(sibling.getRefundId())) {
                return true;
            }
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
     * IF-IP-NUR-04：维护路径写入入院体征（须已有 {@code start_time}；首次分床走 ATD-02 ASSIGN）。
     *
     * @param request    维护请求
     * @param encounter  就诊详情（写前已加载）
     * @param operatorId 操作人
     */
    private void saveAdmissionVitalSignsIfPresent(
        SavePatientInfoRequest request,
        EncounterDetailQueryModel encounter,
        Long operatorId) {
        // 业务分支：无有效测量项则跳过（BR-IP-ATD-024）
        if (request == null || !admissionVitalSignOrchestrator.hasAnySubmittedValue(request.getAdmissionVitalSigns())) {
            return;
        }
        LocalDateTime recordedAt = encounter.getStartTime();
        // 业务分支：未入科（无 start_time）时维护路径不写体征，避免时点与分床不一致
        if (recordedAt == null) {
            return;
        }
        Long bedLocationId = null;
        // 业务分支：已入科时取当前活动床作 location_id；未分床可空
        if (EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())) {
            InpatientEncounterContextQueryModel contextRow =
                EncounterContextReader.mustGetInpatientContextByEncounterId(encounter.getId());
            if (contextRow != null) {
                bedLocationId = contextRow.getBedId();
            }
        }
        AdmissionVitalSignSaveContext context = new AdmissionVitalSignSaveContext()
            .setEncounterId(encounter.getId())
            .setPatientId(encounter.getPatientId())
            .setRecordedAt(recordedAt)
            .setLocationId(bedLocationId)
            .setOperatorId(operatorId)
            .setRecorderId(currentPractitionerResolver.findPractitionerIdOrNull())
            .setSubmitDto(request.getAdmissionVitalSigns());
        admissionVitalSignOrchestrator.saveIfPresent(context);
    }

}
