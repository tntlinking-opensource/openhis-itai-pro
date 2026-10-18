/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.service;

import com.zxplat.application.inpatient.nursestation.traceassignment.assembler.PatientTraceAssignmentListAssembler;
import com.zxplat.application.inpatient.nursestation.traceassignment.assembler.PatientTraceAssignmentWriteAssembler;
import com.zxplat.application.inpatient.nursestation.traceassignment.assembler.SummaryTracePoolAssembler;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupSubmitDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentQueueRowDto;
import com.zxplat.application.inpatient.nursestation.traceassignment.mapper.NurseStationTraceAssignmentMapper;
import com.zxplat.application.inpatient.nursestation.traceassignment.request.ListPatientTraceAssignmentGroupsRequest;
import com.zxplat.application.inpatient.nursestation.traceassignment.request.PagePatientsPendingTraceAssignmentRequest;
import com.zxplat.application.inpatient.nursestation.traceassignment.request.ResolveTraceCodesForPatientRequest;
import com.zxplat.application.inpatient.nursestation.traceassignment.request.SavePatientTraceAssignmentRequest;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.ListPatientTraceAssignmentGroupsResponse;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.PagePatientsPendingTraceAssignmentResponse;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.ResolveTraceCodesForPatientResponse;
import com.zxplat.application.inpatient.nursestation.traceassignment.response.SavePatientTraceAssignmentResponse;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.custom.tracenumber.queries.model.TraceNumberIdentityQueryModel;
import com.zxplat.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.zxplat.domain.share.enums.TraceIdentityResolveStatus;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.medication.medicationdispense.command.AssignTraceNoToCompletedDispenseCommand;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.queries.model.EncounterPendingTraceAssignmentQueryModel;
import com.zxplat.domain.medication.medicationdispense.queries.model.PatientTraceAssignmentAggregateLineQueryModel;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.application.inpatient.nursestation.traceassignment.support.PharmacyTraceCodeResolver;
import com.zxplat.application.shared.tracenumber.TraceNumberBatchIdentityResolver;
import com.zxplat.application.inpatient.nursestation.traceassignment.support.ResolveTraceCodesQuery;
import com.zxplat.application.inpatient.nursestation.traceassignment.support.ResolveTraceCodesResult;
import com.zxplat.domain.workflow.supplydelivery.queries.SupplyDeliveryRequestIdListQuery;
import com.zxplat.domain.workflow.supplydelivery.queries.model.SupplyDeliveryByRequestModel;
import com.zxplat.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.zxplat.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.session.CurrentOperatorResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站追溯码分配应用服务（**AS-IP-NUR-015**，IF-IP-TRC-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>IF-IP-TRC-01</b>：待分配患者队列分页</li>
 *   <li><b>IF-IP-TRC-02</b>：患者药+批汇总行 + 码池摘要</li>
 *   <li><b>IF-IP-TRC-03</b>：批量追溯码只读匹配预览</li>
 *   <li><b>IF-IP-TRC-04</b>：仅写 {@code MedicationDispense.trace_no}</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/nurseStationTraceAssignment/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationTraceAssignmentAppService {

    /** 写路径操作人门控（IF-IP-TRC-04 审计）。 */
    private final CurrentOperatorResolver currentOperatorResolver;
    /** 护士站管辖病区收窄（BR-IP-TRC-001）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;
    /** 住院就诊上下文读侧。 */
    private final EncounterContextReader EncounterContextReader;
    /** 发放行读侧（队列/汇总组/强加载）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 发放行写侧（仅 trace_no 分配）。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 汇总 Supply 行读侧（summaryNo → supplyRequestId）。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** 汇总码池读侧（SupplyDelivery.trace_no）。 */
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    /** 药品目录批量展示。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 列表/队列装配。 */
    private final PatientTraceAssignmentListAssembler patientTraceAssignmentListAssembler;
    /** 码池装配与校验。 */
    private final SummaryTracePoolAssembler summaryTracePoolAssembler;
    /** 写路径校验与 Command 装配。 */
    private final PatientTraceAssignmentWriteAssembler patientTraceAssignmentWriteAssembler;
    /** Request/Response 映射。 */
    private final NurseStationTraceAssignmentMapper nurseStationTraceAssignmentMapper;

    /** 追溯码主表读侧（IF-TRC-21 批号 enrich）。 */
    private final TraceNumberQueryService traceNumberQueryService;

    /**
     * IF-IP-TRC-01：分页查询待追溯分配患者队列。
     *
     * @param request 分页与可选关键字
     * @return 待办就诊分页
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_TRC_101}、
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_102}
     */
    public PagePatientsPendingTraceAssignmentResponse pagePatientsPendingTraceAssignment(
        PagePatientsPendingTraceAssignmentRequest request) {
        try {
            // Gate：分页参数须合法，否则列表无法稳定翻页
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_TRC_101,
                "请先选择患者或无权访问");
            // Pre：收窄到管辖病区可见就诊，关键字由 Encounter 读侧解释
            String keyword = null;
            if (request != null) {
                keyword = request.getKeyword();
            }
            List<Long> visibleEncounterIds = this.resolveVisibleEncounterIds(keyword);
            // 可见就诊内无待办时返回空页，非业务错误
            if (visibleEncounterIds.isEmpty()) {
                return nurseStationTraceAssignmentMapper.toPendingTraceAssignmentPageResponse(
                    Page.empty(), List.of());
            }
            // Pre：域分页查询仍有待分配 trace 的就诊
            Page<EncounterPendingTraceAssignmentQueryModel> pendingPage =
                medicationDispenseQueryService.pageEncountersPendingTraceAssignment(
                    nurseStationTraceAssignmentMapper.toEncountersPendingTraceAssignmentPageQuery(
                        request, visibleEncounterIds));
            List<Long> pageEncounterIds = new ArrayList<>();
            // 收集当前页 encounterId，供上下文批量回补
            for (EncounterPendingTraceAssignmentQueryModel row : pendingPage.getContent()) {
                if (row != null && row.getEncounterId() != null) {
                    pageEncounterIds.add(row.getEncounterId());
                }
            }
            // Pre：按域分页顺序回补患者/床号展示
            List<InpatientEncounterContextQueryModel> contexts =
                EncounterContextReader.listInpatientContextsByEncounterIdsOrdered(pageEncounterIds);
            Map<Long, InpatientEncounterContextQueryModel> contextByEncounterId =
                nurseStationTraceAssignmentMapper.indexContextsByEncounterId(contexts);
            List<PatientTraceAssignmentQueueRowDto> rows = patientTraceAssignmentListAssembler.buildQueueRows(
                pendingPage.getContent(), contextByEncounterId);
            // Asm：分页响应
            return nurseStationTraceAssignmentMapper.toPendingTraceAssignmentPageResponse(pendingPage, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // 未预期异常统一映射为列表查询失败，避免栈信息透出
            log.error("pagePatientsPendingTraceAssignment failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TRC_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-TRC-02：加载患者药批汇总待分配行。
     *
     * @param request 就诊主键
     * @return 汇总组列表（含码池摘要）
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_TRC_101}
     */
    public ListPatientTraceAssignmentGroupsResponse listPatientTraceAssignmentGroups(
        ListPatientTraceAssignmentGroupsRequest request) {
        try {
            Long encounterId = null;
            if (request != null) {
                encounterId = request.getEncounterId();
            }
            // Gate：就诊主键必填，防止空患者加载
            if (encounterId == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TRC_101,
                    "请先选择患者或无权访问");
            }
            // Pre：就诊须落在护士站可见范围（BR-IP-TRC-001；对齐 SDD EncounterContextReader#assertEncounterVisible）
            this.assertEncounterVisible(encounterId);
            // Pre：药批汇总组 + 码池 + 药品展示
            List<PatientTraceAssignmentGroupDto> groups = this.loadPatientGroupDtos(encounterId);
            // Asm：汇总组列表响应
            return nurseStationTraceAssignmentMapper.toPatientTraceAssignmentGroupsResponse(groups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            Long failedEncounterId = null;
            if (request != null) {
                failedEncounterId = request.getEncounterId();
            }
            log.error("listPatientTraceAssignmentGroups failed encounterId={}", failedEncounterId, ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TRC_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-TRC-03：批量解析追溯码（只读预览，不写库）。
     *
     * @param request 就诊、码列表与组键快照
     * @return 匹配预览与失败明细；failItems 非空表示部分码未匹配（AS §4 返回失败明细）
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_TRC_101} 就诊不可见；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_202} 码格式非法；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_201} 不在码池；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_206} 跨汇总单；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_208} 码数超限
     */
    public ResolveTraceCodesForPatientResponse resolveTraceCodesForPatient(
        ResolveTraceCodesForPatientRequest request) {
        Long encounterId = null;
        if (request != null) {
            encounterId = request.getEncounterId();
        }
        // Gate：批量码非空，否则无法进入解析
        if (request == null || request.getTraceCodes() == null || request.getTraceCodes().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TRC_202,
                "追溯码须为 20 位有效编码");
        }
        // Gate：单次批量上限 50，防止性能劣化
        if (request.getTraceCodes().size() > InpatientNurseStationConstants.TRACE_CODE_BATCH_RESOLVE_MAX) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TRC_102,
                "单次最多解析50个追溯码");
        }
        // Pre：就诊可见性（BR-IP-TRC-001）
        this.assertEncounterVisible(encounterId);
        // Pre：复用 IF-02 组快照与码池（步骤 3～5）
        List<PatientTraceAssignmentGroupDto> allGroups = this.loadPatientGroupDtos(encounterId);
        List<PatientTraceAssignmentGroupDto> snapshotGroups = patientTraceAssignmentListAssembler.filterGroupsByKeys(
            allGroups, request.getGroupKeys());
        TraceAssignmentSupplyContext supplyContext = this.loadSupplyContextFromGroups(allGroups);
        // Pre：规范化追溯码（BR-IP-TRC-014）
        List<String> normalizedCodes = patientTraceAssignmentWriteAssembler.normalizeTraceCodes(request.getTraceCodes());
        Set<String> snapshotSummaryNos = new LinkedHashSet<>();
        for (PatientTraceAssignmentGroupDto group : snapshotGroups) {
            if (group != null && StringUtils.isNotEmpty(group.getSummaryNo())) {
                snapshotSummaryNos.add(group.getSummaryNo());
            }
        }
        // Pre：码须属于快照 summary 码池，禁止跨 summary 歧义（BR-IP-TRC-005/011）
        summaryTracePoolAssembler.assertCodesInSnapshotSummaries(
            normalizedCodes,
            snapshotSummaryNos,
            supplyContext.poolBySummary(),
            InpatientNurseStationConstants.EX_IP_TRC_201,
            InpatientNurseStationConstants.EX_IP_TRC_206,
            "追溯码不在本汇总单药房码池内，请核对或联系药房",
            "追溯码与汇总单不匹配");
        // Pre：药品+批号自动匹配（只读；BR-IP-TRC-007 不写库）
        ResolveTraceCodesQuery resolveCommand = new ResolveTraceCodesQuery();
        resolveCommand.setTraceCodes(normalizedCodes);
        resolveCommand.setGroups(patientTraceAssignmentListAssembler.toGroupSnapshots(snapshotGroups));
        resolveCommand.setPoolBindings(summaryTracePoolAssembler.buildPoolBindings(
            supplyContext.supplyLinesBySummary(), supplyContext.deliveriesByRequestId()));
        // Pre：IF-TRC-21 反查主表批号 enrich 码池绑定，提升同药多批匹配精度
        this.enrichPoolBindingsWithMasterLotNumbers(resolveCommand.getPoolBindings(), normalizedCodes);
        ResolveTraceCodesResult resolveResult = PharmacyTraceCodeResolver.resolveTraceCodes(resolveCommand);
        // Pre：码数不超过各组发药量（仅对已成功匹配的组校验，BR-IP-TRC-008）
        Map<String, String> traceNoByGroupKey = this.buildMatchedTraceNoByGroupKey(resolveResult);
        patientTraceAssignmentWriteAssembler.assertTraceCountWithinLimit(snapshotGroups, traceNoByGroupKey);
        // Asm：返回匹配预览与失败明细（部分未匹配时由 failItems 承载，AS §4 返回失败明细）
        return nurseStationTraceAssignmentMapper.toResolveTraceCodesResponse(resolveResult);
    }

    /**
     * IF-IP-TRC-04：保存患者追溯码分配（仅 UPDATE trace_no）。
     *
     * @param request 就诊与各汇总组追溯码
     * @param request 就诊与各汇总组追溯码
     * @return 成功保存组数
     * @throws BusinessException {@link InpatientNurseStationConstants#EX_IP_TRC_101} 无权；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_201} 不在码池；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_203} 批号缺失；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_204} 码池为空；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_207} 状态非 COMPLETED；
     *                           {@link InpatientNurseStationConstants#EX_IP_TRC_208} 码数超限
     */
    public SavePatientTraceAssignmentResponse savePatientTraceAssignment(
        SavePatientTraceAssignmentRequest request) {
        // Gate：待保存组非空
        if (request == null || request.getGroups() == null || request.getGroups().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TRC_101,
                "请先选择患者或无权访问");
        }
        Long encounterId = request.getEncounterId();
        // Pre：就诊可见（BR-IP-TRC-001）
        this.assertEncounterVisible(encounterId);
        // Gate：写路径操作人
        Long operatorId = currentOperatorResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_TRC_101,
            "请先选择患者或无权访问");
        // Pre：强加载全部底层发放行
        List<Long> allDispenseIds = this.collectDispenseIds(request.getGroups());
        List<MedicationDispense> dispenses = medicationDispenseQueryService.listMedicationDispensesByIds(allDispenseIds);
        // Pre：须为汇总路径已核发态（BR-IP-TRC-002）
        patientTraceAssignmentWriteAssembler.assertAllCompletedWithSummary(dispenses);
        // Pre：批号非空（EX-IP-TRC-203）
        patientTraceAssignmentWriteAssembler.assertLotNumberPresent(request.getGroups());
        List<PatientTraceAssignmentGroupDto> allGroups = this.loadPatientGroupDtos(encounterId);
        Map<String, PatientTraceAssignmentGroupDto> groupKeyIndex = this.indexGroupsByKey(allGroups);
        TraceAssignmentSupplyContext supplyContext = this.loadSupplyContextFromGroups(allGroups);
        Map<String, List<String>> traceCodesBySummary = patientTraceAssignmentWriteAssembler.buildTraceCodesBySummary(
            request.getGroups(), groupKeyIndex);
        // Pre：码池校验 + 空池拒绝（BR-IP-TRC-005/013）
        patientTraceAssignmentWriteAssembler.assertPoolPresentWhenCodesSubmitted(
            traceCodesBySummary, supplyContext.poolBySummary());
        summaryTracePoolAssembler.assertCodesInPools(
            traceCodesBySummary,
            supplyContext.poolBySummary(),
            InpatientNurseStationConstants.EX_IP_TRC_201,
            "追溯码不在本汇总单药房码池内，请核对或联系药房");
        Map<String, String> traceNoByGroupKey = this.buildTraceNoByGroupKeyFromSubmit(request.getGroups());
        List<PatientTraceAssignmentGroupDto> groupsToValidate =
            this.collectGroupsForTraceCountValidation(groupKeyIndex, traceNoByGroupKey);
        // Pre：码数不超过发药量（BR-IP-TRC-008）
        patientTraceAssignmentWriteAssembler.assertTraceCountWithinLimit(groupsToValidate, traceNoByGroupKey);
        // Cmd：按组写入相同 trace_no 到全部底层行（BR-IP-TRC-010）；同一事务由框架切面覆盖
        int savedGroupCount = 0;
        for (PatientTraceAssignmentGroupSubmitDto submitGroup : request.getGroups()) {
            if (submitGroup == null || StringUtils.isEmpty(submitGroup.getTraceNo())) {
                // 空 trace 组表示跳过，允许部分组仅预览不保存
                continue;
            }
            AssignTraceNoToCompletedDispenseCommand command =
                patientTraceAssignmentWriteAssembler.toAssignTraceNoCommand(submitGroup, encounterId, operatorId);
            if (command.getItems() == null || command.getItems().isEmpty()) {
                continue;
            }
            medicationDispenseDomainService.assignTraceNoToCompletedDispense(command);
            savedGroupCount = savedGroupCount + 1;
        }
        // Asm：保存结果
        return nurseStationTraceAssignmentMapper.toSavePatientTraceAssignmentResponse(savedGroupCount);
    }

    /**
     * 委托 {@link EncounterContextReader#assertEncounterVisible} 校验护士站就诊可见性。
     *
     * @param encounterId 就诊主键
     */
    private void assertEncounterVisible(Long encounterId) {
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        EncounterContextReader.assertEncounterVisible(
            encounterId,
            managedWardIds,
            InpatientNurseStationConstants.EX_IP_TRC_101,
            "请先选择患者或无权访问");
    }

    /**
     * 解析管辖病区下可见住院就诊主键（可选关键字收窄）。
     *
     * @param keyword 姓名/床号/就诊号
     * @return 可见就诊 id 列表；无管辖病区时抛 EX-IP-TRC-101
     */
    private List<Long> resolveVisibleEncounterIds(String keyword) {
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        // 无管辖病区时无权查看任何待办队列
        if (managedWardIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TRC_101,
                "请先选择患者或无权访问");
        }
        InpatientEncounterPageQuery query =
            nurseStationTraceAssignmentMapper.toInpatientEncounterVisibilityQuery(managedWardIds, keyword);
        List<InpatientEncounterContextQueryModel> rows = EncounterContextReader.listInpatientEncounterContexts(query);
        List<Long> encounterIds = new ArrayList<>();
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        return encounterIds;
    }

    /**
     * 加载患者药批汇总组 DTO（域聚合 + 码池 + 药品展示）。
     *
     * @param encounterId 就诊主键
     * @return 汇总组 DTO 列表
     */
    private List<PatientTraceAssignmentGroupDto> loadPatientGroupDtos(Long encounterId) {
        List<PatientTraceAssignmentAggregateLineQueryModel> aggregateLines =
            medicationDispenseQueryService.listPatientTraceAssignmentAggregateLines(encounterId);
        TraceAssignmentSupplyContext supplyContext = this.loadSupplyContextFromAggregateLines(aggregateLines);
        Map<String, ProductDefinitionDisplayDto> productByMedKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectMedicationLookups(aggregateLines));
        return patientTraceAssignmentListAssembler.buildGroupDtos(
            aggregateLines,
            productByMedKey,
            supplyContext.poolBySummary(),
            summaryTracePoolAssembler);
    }

    /**
     * 从已装配汇总组 DTO 加载 Supply/码池上下文。
     */
    private TraceAssignmentSupplyContext loadSupplyContextFromGroups(List<PatientTraceAssignmentGroupDto> groups) {
        Set<String> summaryNos = new LinkedHashSet<>();
        if (groups != null) {
            for (PatientTraceAssignmentGroupDto group : groups) {
                if (group != null && StringUtils.isNotEmpty(group.getSummaryNo())) {
                    summaryNos.add(group.getSummaryNo());
                }
            }
        }
        return this.loadSupplyContextBySummaryNos(new ArrayList<>(summaryNos));
    }

    /**
     * 从域汇总行提取 summaryNo 并加载码池上下文。
     */
    private TraceAssignmentSupplyContext loadSupplyContextFromAggregateLines(
        List<PatientTraceAssignmentAggregateLineQueryModel> aggregateLines) {
        Set<String> summaryNos = new LinkedHashSet<>();
        if (aggregateLines != null) {
            for (PatientTraceAssignmentAggregateLineQueryModel line : aggregateLines) {
                if (line != null && StringUtils.isNotEmpty(line.getSummaryNo())) {
                    summaryNos.add(line.getSummaryNo());
                }
            }
        }
        return this.loadSupplyContextBySummaryNos(new ArrayList<>(summaryNos));
    }

    /**
     * 按汇总单号批量加载 Supply 行、Delivery 与码池。
     *
     * @param summaryNos 汇总单号列表
     * @return 码池上下文
     */
    private TraceAssignmentSupplyContext loadSupplyContextBySummaryNos(List<String> summaryNos) {
        Map<String, List<MedicationSummarySlipLineQueryModel>> supplyLinesBySummary = new HashMap<>();
        List<Long> supplyRequestIds = new ArrayList<>();
        if (summaryNos != null) {
            for (String summaryNo : summaryNos) {
                if (StringUtils.isEmpty(summaryNo)) {
                    continue;
                }
                List<MedicationSummarySlipLineQueryModel> lines =
                    supplyRequestQueryService.listMedicationSummarySlipLinesByBusNo(summaryNo);
                supplyLinesBySummary.put(summaryNo, lines);
                if (lines != null) {
                    for (MedicationSummarySlipLineQueryModel line : lines) {
                        if (line != null && line.getSupplyRequestId() != null) {
                            supplyRequestIds.add(line.getSupplyRequestId());
                        }
                    }
                }
            }
        }
        Map<Long, List<SupplyDeliveryByRequestModel>> deliveriesByRequestId = new HashMap<>();
        if (!supplyRequestIds.isEmpty()) {
            SupplyDeliveryRequestIdListQuery deliveryQuery = new SupplyDeliveryRequestIdListQuery();
            deliveryQuery.setRequestIdList(supplyRequestIds);
            List<SupplyDeliveryByRequestModel> deliveries =
                supplyDeliveryQueryService.selectByRequestIdList(deliveryQuery);
            if (deliveries != null) {
                for (SupplyDeliveryByRequestModel delivery : deliveries) {
                    if (delivery == null || delivery.getRequestId() == null) {
                        continue;
                    }
                    List<SupplyDeliveryByRequestModel> bucket =
                        deliveriesByRequestId.computeIfAbsent(delivery.getRequestId(), key -> new ArrayList<>());
                    bucket.add(delivery);
                }
            }
        }
        Map<String, Set<String>> poolBySummary = summaryTracePoolAssembler.buildPoolBySummaryNo(
            summaryNos, supplyLinesBySummary, deliveriesByRequestId);
        return new TraceAssignmentSupplyContext(supplyLinesBySummary, deliveriesByRequestId, poolBySummary);
    }

    /**
     * 收集汇总行涉及药品的批量加载键。
     */
    private List<ProductDefinitionBatchLookup> collectMedicationLookups(
        List<PatientTraceAssignmentAggregateLineQueryModel> aggregateLines) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<Long> seenMedIds = new LinkedHashSet<>();
        if (aggregateLines == null) {
            return lookups;
        }
        for (PatientTraceAssignmentAggregateLineQueryModel line : aggregateLines) {
            if (line == null || line.getMedicationId() == null || seenMedIds.contains(line.getMedicationId())) {
                continue;
            }
            seenMedIds.add(line.getMedicationId());
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(line.getMedicationId());
            lookups.add(lookup);
        }
        return lookups;
    }

    /**
     * 收集保存请求中全部底层发放行 id。
     */
    private List<Long> collectDispenseIds(List<PatientTraceAssignmentGroupSubmitDto> groups) {
        List<Long> dispenseIds = new ArrayList<>();
        if (groups == null) {
            return dispenseIds;
        }
        for (PatientTraceAssignmentGroupSubmitDto group : groups) {
            if (group == null || group.getDispenseIds() == null) {
                continue;
            }
            for (Long dispenseId : group.getDispenseIds()) {
                if (dispenseId != null) {
                    dispenseIds.add(dispenseId);
                }
            }
        }
        return dispenseIds;
    }

    /**
     * 汇总组 DTO 按 groupKey 索引。
     */
    private Map<String, PatientTraceAssignmentGroupDto> indexGroupsByKey(List<PatientTraceAssignmentGroupDto> groups) {
        Map<String, PatientTraceAssignmentGroupDto> index = new HashMap<>();
        if (groups == null) {
            return index;
        }
        for (PatientTraceAssignmentGroupDto group : groups) {
            if (group != null && StringUtils.isNotEmpty(group.getGroupKey())) {
                index.put(group.getGroupKey(), group);
            }
        }
        return index;
    }

    /**
     * 保存请求组 → groupKey → traceNo 映射。
     */
    private Map<String, String> buildTraceNoByGroupKeyFromSubmit(List<PatientTraceAssignmentGroupSubmitDto> submitGroups) {
        Map<String, String> traceNoByGroupKey = new HashMap<>();
        if (submitGroups == null) {
            return traceNoByGroupKey;
        }
        for (PatientTraceAssignmentGroupSubmitDto group : submitGroups) {
            if (group == null || StringUtils.isEmpty(group.getGroupKey())) {
                continue;
            }
            traceNoByGroupKey.put(group.getGroupKey(), group.getTraceNo());
        }
        return traceNoByGroupKey;
    }

    /**
     * 收集待校验码数的汇总组 DTO（仅含提交了非空 trace 的组）。
     *
     * @param groupKeyIndex      groupKey → 汇总组 DTO
     * @param traceNoByGroupKey  groupKey → 追溯码串
     * @return 待校验组列表
     */
    private List<PatientTraceAssignmentGroupDto> collectGroupsForTraceCountValidation(
        Map<String, PatientTraceAssignmentGroupDto> groupKeyIndex,
        Map<String, String> traceNoByGroupKey) {
        List<PatientTraceAssignmentGroupDto> groups = new ArrayList<>();
        if (groupKeyIndex == null || traceNoByGroupKey == null) {
            return groups;
        }
        for (Map.Entry<String, String> entry : traceNoByGroupKey.entrySet()) {
            if (StringUtils.isEmpty(entry.getValue())) {
                continue;
            }
            PatientTraceAssignmentGroupDto groupDto = groupKeyIndex.get(entry.getKey());
            if (groupDto != null) {
                groups.add(groupDto);
            }
        }
        return groups;
    }

    /**
     * 解析预览：将匹配结果转为 groupKey → 逗号分隔 traceNo。
     *
     * @param resolveResult 批量解析结果
     * @return groupKey → traceNo
     */
    private Map<String, String> buildMatchedTraceNoByGroupKey(ResolveTraceCodesResult resolveResult) {
        Map<String, String> traceNoByGroupKey = new HashMap<>();
        if (resolveResult == null || resolveResult.getMatches() == null) {
            return traceNoByGroupKey;
        }
        for (ResolveTraceCodesResult.GroupMatch match : resolveResult.getMatches()) {
            if (match == null || StringUtils.isEmpty(match.getGroupKey())) {
                continue;
            }
            if (match.getMatchedTraceCodes() == null || match.getMatchedTraceCodes().isEmpty()) {
                continue;
            }
            traceNoByGroupKey.put(match.getGroupKey(), String.join(",", match.getMatchedTraceCodes()));
        }
        return traceNoByGroupKey;
    }

    /**
     * IF-TRC-21：用主表批号 enrich 码池绑定，供 IF-IP-TRC-03 同药多批唯一定位。
     *
     * @param poolBindings    码池绑定列表
     * @param normalizedCodes 规范化追溯码
     */
    private void enrichPoolBindingsWithMasterLotNumbers(
        List<ResolveTraceCodesQuery.TraceCodePoolBinding> poolBindings,
        List<String> normalizedCodes
    ) {
        if (poolBindings == null || poolBindings.isEmpty() || normalizedCodes == null || normalizedCodes.isEmpty()) {
            return;
        }
        TraceNumberBatchIdentityResolver.assertBatchSizeWithinLimit(normalizedCodes);
        List<TraceNumberIdentityQueryModel> identities =
            traceNumberQueryService.batchResolveTraceIdentityByTraceNos(normalizedCodes);
        Map<String, TraceNumberIdentityQueryModel> identityByCode = new HashMap<>();
        if (identities != null) {
            for (TraceNumberIdentityQueryModel identity : identities) {
                if (identity == null || StringUtils.isEmpty(identity.getTraceNo())) {
                    continue;
                }
                identityByCode.put(identity.getTraceNo().trim(), identity);
            }
        }
        for (ResolveTraceCodesQuery.TraceCodePoolBinding binding : poolBindings) {
            if (binding == null || StringUtils.isEmpty(binding.getTraceCode())) {
                continue;
            }
            TraceNumberIdentityQueryModel identity = identityByCode.get(binding.getTraceCode().trim());
            if (identity == null) {
                continue;
            }
            if (!TraceIdentityResolveStatus.OK.getCode().equals(identity.getResolveStatusEnum())) {
                continue;
            }
            if (StringUtils.isNotEmpty(identity.getLotNumber())) {
                binding.setLotNumber(identity.getLotNumber().trim());
            }
        }
    }

    /**
     * Supply/码池读侧上下文（IF-02/03/04 复用）。
     */
    private record TraceAssignmentSupplyContext(
        Map<String, List<MedicationSummarySlipLineQueryModel>> supplyLinesBySummary,
        Map<Long, List<SupplyDeliveryByRequestModel>> deliveriesByRequestId,
        Map<String, Set<String>> poolBySummary) {
    }
}
