/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.application.shared.validator.CurrentManagedWardLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryListAssembler;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler.NurseStationDispenseSummaryWriteAssembler.SummaryMergeGroup;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.CreateMedicationSummaryDispenseItemDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummaryPatientGroupDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipHeaderDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.dto.NurseStationDispenseSummarySlipLineDto;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.mapper.NurseStationDispenseSummaryMapper;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.CancelMedicationSummaryRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.CreateMedicationSummaryRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.GetMedicationSummarySlipDetailRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.PageMedicationSummarySlipHeadersRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.PagePendingDispenseSummaryLinesRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.request.SubmitMedicationSummaryRequest;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.CancelMedicationSummaryResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.CreateMedicationSummaryResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.GetMedicationSummarySlipDetailResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.PageMedicationSummarySlipHeadersResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.PagePendingDispenseSummaryLinesResponse;
import com.openhis.health.application.inpatient.nursestation.dispensesummary.response.SubmitMedicationSummaryResponse;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.inpatient.pharmacy.dispense.support.InpatientMedicationDispenseChargePricingLoader;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.support.MedicationSummarySlipLoader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.SummarizeMedicationDispensesCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispensePickupPendingQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.MedicationSummarySlipQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站汇总领药应用服务（AS-IP-NUR-010，IF-IP-SUM-01～05、08）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>IF-IP-SUM-01</b>：待汇总明细分页 + 就诊/医嘱/时间点分组展示</li>
 *   <li><b>IF-IP-SUM-02/03</b>：已生成汇总单头/合并明细查询</li>
 *   <li><b>IF-IP-SUM-04</b>：按药房拆单、itemId+单价合并、拆零写 SupplyRequest（PENDING_APPROVAL）+ 发放行 SUMMARIZED</li>
 *   <li><b>IF-IP-SUM-05</b>：汇总 Tab 发送 → submitApproval（APPROVAL）</li>
 *   <li><b>IF-IP-SUM-08</b>：待发送汇总单撤销 → 释放发放行 + 逻辑删除 Supply 汇总单</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/nurseStationDispenseSummary/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationDispenseSummaryAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 写路径医务人员/applicantId 解析。 */
    /** 护士站管辖病区收窄（BR-IP-SUM-001）。 */
    private final CurrentManagedWardLocationIdsResolver managedWardLocationIdsResolver;
    /** 分页窗口 Gate（pageNo/pageSize 必填 IF）。 */
    private final PageWindowGate pageWindowGate;
    /** 住院就诊上下文批量加载。 */
    private final EncounterContextReader EncounterContextReader;
    /** 药品申请执行列表（medReq 预解析）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 发放读侧（待汇总分页、写前强加载）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 发放写侧（summarizeMedicationDispenses）。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 发放 ↔ 待结算账单只读协作（B.6.1.3）。 */
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    /** 汇总单头 SupplyRequest + SupplyDelivery 读编排（B.6.1.3）。 */
    private final MedicationSummarySlipLoader medicationSummarySlipLoader;
    /** Supply 读侧（汇总单头/明细、busNo 回读）。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** Supply 写侧（createSummarySupplyRequest / submitApproval / removeByBusNo）。 */
    private final SupplyRequestDomainService supplyRequestDomainService;
    /** 位置名称展示。 */
    private final LocationQueryService locationQueryService;
    /** 药品目录批量读侧（展示 + unitBands）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** Request/Query/Command/Response 映射。 */
    private final NurseStationDispenseSummaryMapper nurseStationDispenseSummaryMapper;
    /** 待汇总/汇总 Tab 列表分组装配。 */
    private final NurseStationDispenseSummaryListAssembler nurseStationDispenseSummaryListAssembler;
    /** 写路径分组合并与拆零数量换算。 */
    private final NurseStationDispenseSummaryWriteAssembler nurseStationDispenseSummaryWriteAssembler;

    /**
     * IF-IP-SUM-01：分页查询待汇总明细。
     *
     * @param request 多选就诊、中西药 Tab、长临、截止时间
     * @return 分页壳 + 按就诊分组明细
     */
    public PagePendingDispenseSummaryLinesResponse pagePendingDispenseSummaryLines(
        PagePendingDispenseSummaryLinesRequest request) {
        try {
            // Gate ①：encounterIds、medicineCategoryTab、therapyFilter、pageNo/pageSize
            this.assertPendingLinesRequestGate(request);
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
            // Gate ②：管理病区
            List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
            List<Long> requestedEncounterIds = this.distinctIds(request.getEncounterIds());
            // Gate ③：可见 encounter 收窄（BR-IP-SUM-001）
            InpatientEncounterPageQuery visibilityQuery = nurseStationDispenseSummaryMapper.toInpatientEncounterPageQuery(
                requestedEncounterIds, managedWardIds);
            List<InpatientEncounterContextQueryModel> visibleRows =
                EncounterContextReader.listInpatientEncounterContexts(visibilityQuery);
            List<Long> visibleEncounterIds = this.retainRequestedVisibleEncounterIds(requestedEncounterIds, visibleRows);
            if (visibleEncounterIds.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_101,
                    "请先选择患者或无权访问");
            }
            Map<Long, InpatientEncounterContextQueryModel> encounterById =
                this.indexEncounters(visibleRows, visibleEncounterIds);
            // Pre ④：护士站执行药嘱列表（VERIFIED + STOPPED）
            String therapyEnum = nurseStationDispenseSummaryMapper.resolveTherapyEnum(request.getTherapyFilter());
            List<MedicationRequestListQueryModel> executeOrders =
                medicationRequestQueryService.listInpatientNurseStationExecuteOrders(visibleEncounterIds, therapyEnum);
            // Pre ⑤：按 tcmFlag 收窄 medReqIds
            List<Long> resolvedMedReqIds = nurseStationDispenseSummaryListAssembler.resolveMedReqIds(
                executeOrders, request.getMedicineCategoryTab());
            Map<Long, MedicationRequestListQueryModel> medReqById = this.indexMedReqById(executeOrders);
            // Pre ⑥⑦：域分页待汇总发放行
            Page<MedicationDispensePickupPendingQueryModel> page =
                medicationDispenseQueryService.pageMedicationDispensePickupPending(
                    nurseStationDispenseSummaryMapper.toPickupPendingPageQuery(
                        request, visibleEncounterIds, resolvedMedReqIds));
            // Pre ⑦b：跨聚合只读回填账单单价（PENDING_SETTLEMENT）
            inpatientMedicationDispenseChargePricingLoader.applyPendingSettlementUnitPricesToPickupPendingRows(
                page.getContent());
            // Pre ⑧：药品展示批量加载
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectProductLookupsFromPendingPage(page));
            // Pre ⑨：药房展示批量加载
            Set<Long> locationIds = this.collectLocationIdsFromPendingPage(page);
            Map<Long, Location> locationById = nurseStationDispenseSummaryListAssembler.indexLocations(
                this.loadLocations(locationIds));
            // Asm ⑩：就诊→医嘱→时间点分组
            List<NurseStationDispenseSummaryPatientGroupDto> groups =
                nurseStationDispenseSummaryListAssembler.buildPatientGroups(
                    page, medReqById, productByKey, encounterById, locationById);
            // Asm ⑪：分页响应
            return nurseStationDispenseSummaryMapper.toPagePendingDispenseSummaryLinesResponse(page, groups);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("pagePendingDispenseSummaryLines failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-02：分页查询汇总单头。
     *
     * @param request 药房、时段、关键字、分页
     * @return 汇总单头分页
     */
    public PageMedicationSummarySlipHeadersResponse pageMedicationSummarySlipHeaders(
        PageMedicationSummarySlipHeadersRequest request) {
        try {
            // Gate ①：分页参数
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
            // Pre ②：仅当前端显式指定药房时收窄 purposeLocationIds
            List<Long> purposeLocationIds = this.resolveSummaryPurposeLocationIds(request);
            // Pre ③：Supply 列表 Query
            SupplyRequestDocumentListQuery listQuery = nurseStationDispenseSummaryMapper.toSupplyRequestDocumentListQuery(
                request, purposeLocationIds);
            // Pre ④：域分页汇总单头
            Page<MedicationSummarySlipQueryModel> page =
                medicationSummarySlipLoader.pageMedicationSummarySlips(listQuery);
            // Asm ⑤：单头 DTO + 展示 enrichment
            List<NurseStationDispenseSummarySlipHeaderDto> rows =
                nurseStationDispenseSummaryMapper.toSlipHeaderDtos(page);
            // Asm ⑥：分页响应
            return nurseStationDispenseSummaryMapper.toPageMedicationSummarySlipHeadersResponse(page, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("pageMedicationSummarySlipHeaders failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-03：查询汇总单合并明细。
     *
     * @param request summaryNo
     * @return 单头 + 合并明细（无批号）
     */
    public GetMedicationSummarySlipDetailResponse getMedicationSummarySlipDetail(
        GetMedicationSummarySlipDetailRequest request) {
        try {
            // Gate ①：summaryNo 非空
            if (request == null || StringUtils.isEmpty(request.getSummaryNo())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_103,
                    "汇总单不存在或已失效，请刷新");
            }
            String summaryNo = request.getSummaryNo().trim();
            // Pre ②：合并明细行
            List<MedicationSummarySlipLineQueryModel> domainLines =
                supplyRequestQueryService.listMedicationSummarySlipLinesByBusNo(summaryNo);
            if (domainLines.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_103,
                    "汇总单不存在或已失效，请刷新");
            }
            // Pre ②b：跨聚合只读累加单价/总价（BR-IP-SUM-009）
            inpatientMedicationDispenseChargePricingLoader.enrichSummarySlipLineChargePricing(summaryNo, domainLines);
            // Pre ③：单头（复用 IF-02 装配）
            NurseStationDispenseSummarySlipHeaderDto header = this.loadSlipHeaderBySummaryNo(summaryNo);
            // Pre ④：药品展示批量加载
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectProductLookupsFromSlipLines(domainLines));
            // Asm ⑤：合并行 DTO
            List<NurseStationDispenseSummarySlipLineDto> lines =
                nurseStationDispenseSummaryListAssembler.buildSlipLineDtos(domainLines, productByKey);
            // Asm ⑥：详情响应
            return nurseStationDispenseSummaryMapper.toGetMedicationSummarySlipDetailResponse(summaryNo, header, lines);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("getMedicationSummarySlipDetail failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IP-SUM-04：汇总保存（唯一写路径之一；同事务整批 BR-IP-SUM-012）。
     *
     * @param request 勾选发放行
     * @return 汇总单号列表
     */
    public CreateMedicationSummaryResponse createMedicationSummary(CreateMedicationSummaryRequest request) {
        // Gate ①：dispenseItems 1～500
        this.assertCreateSummaryRequestGate(request);
        // Gate ②：各 encounterId 可见性
        List<Long> encounterIds = this.collectEncounterIdsFromWriteRequest(request);
        this.requireVisibleEncounters(encounterIds);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_SUM_204,
            "未登录或登录已失效，无法汇总领药");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            InpatientNurseStationConstants.EX_IP_SUM_204,
            "未解析到当前医务人员，无法汇总领药");
        List<Long> dispenseIds = this.collectDispenseIdsFromWriteRequest(request);
        try {
            // Pre ③：强加载发放实体
            List<MedicationDispense> dispenses = medicationDispenseQueryService.listMedicationDispensesByIds(dispenseIds);
            if (dispenses.size() != dispenseIds.size()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            // Pre ④：PREPARATION 且无 summaryNo
            this.assertAllDispensesSummarizable(dispenses);
            // Pre ⑤：ChargeItem 只读定价
            Map<Long, ChargeItem> chargeByDispenseId = this.loadChargeByDispenseId(request, dispenses);
            // Pre ⑥：药品 unitBands 源
            Map<String, ProductDefinitionDisplayDto> productByMedId =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectProductLookupsFromDispenses(dispenses));
            // Pre ⑦：按药房+单价分组合并
            Map<Long, List<SummaryMergeGroup>> groupsByLocation =
                nurseStationDispenseSummaryWriteAssembler.buildMergeGroupsByLocationAndPrice(
                    dispenses, chargeByDispenseId, productByMedId);
            // Pre ⑧：unitBands 校验
            for (List<SummaryMergeGroup> groups : groupsByLocation.values()) {
                for (SummaryMergeGroup group : groups) {
                    String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, group.getMedicationId());
                    ProductDefinitionDisplayDto product = productByMedId.get(productKey);
                    nurseStationDispenseSummaryWriteAssembler.validateMergeGroupUnitBands(group, product);
                }
            }

            List<String> summaryNos = new ArrayList<>();
            // Cmd ⑨：循环每个药房组（9a～9b）
            for (Map.Entry<Long, List<SummaryMergeGroup>> entry : groupsByLocation.entrySet()) {
                Long locationId = entry.getKey();
                List<SummaryMergeGroup> locationGroups = entry.getValue();
                List<Long> locationDispenseIds = this.collectDispenseIdsFromGroups(locationGroups);
                // Cmd 9a～9b：预分配 MSO 单号 → 发放 summary_no 与 Supply bus_no 同源（AS-IP-NUR-010 §2.1）
                String summaryNo = supplyRequestDomainService.allocateBusNoForType(
                    SupplyType.MEDICATION_SUMMARY_ORDER.getCode());
                this.summarizeMedicationDispensesOrThrow201(
                    nurseStationDispenseSummaryMapper.toSummarizeMedicationDispensesCommand(
                        locationId, locationDispenseIds, operatorId, summaryNo));
                // Cmd 9b：SupplyRequest type 27（初态 PENDING_APPROVAL；不创建 SupplyDelivery）
                supplyRequestDomainService.createSummarySupplyRequest(
                    nurseStationDispenseSummaryMapper.toCreateSummarySupplyRequestCommand(
                        summaryNo, locationGroups, applicantId, operatorId));
                summaryNos.add(summaryNo);
            }
            // Asm ⑩：写响应
            return nurseStationDispenseSummaryMapper.toCreateMedicationSummaryResponse(summaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("createMedicationSummary failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_204,
                "汇总保存失败，请检查所选明细状态");
        }
    }

    /**
     * IF-IP-SUM-05：发送汇总领药单（submitApproval → APPROVAL）。
     *
     * @param request 汇总单号列表
     * @return 已发送汇总单号
     */
    public SubmitMedicationSummaryResponse submitMedicationSummary(SubmitMedicationSummaryRequest request) {
        this.assertSubmitSummaryRequestGate(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_SUM_205,
            "未登录或登录已失效，无法发送汇总单");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            InpatientNurseStationConstants.EX_IP_SUM_205,
            "未解析到当前医务人员，无法发送汇总单");
        List<String> summaryNos = this.distinctSummaryNos(request.getSummaryNos());
        try {
            for (String summaryNo : summaryNos) {
                this.assertMedicationSummarySlipSubmittable(summaryNo);
                supplyRequestDomainService.submitApproval(
                    nurseStationDispenseSummaryMapper.toSubmitApprovalCommand(summaryNo, applicantId, operatorId));
            }
            return nurseStationDispenseSummaryMapper.toSubmitMedicationSummaryResponse(summaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("submitMedicationSummary failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_205,
                "发送失败，请检查汇总单状态");
        }
    }

    /**
     * IF-IP-SUM-08：撤销待发送汇总领药单（释放发放行 + 逻辑删除 Supply 汇总单）。
     *
     * <p>仅 {@link SupplyStatus#PENDING_APPROVAL} 可撤销；已发送/已驳回/已核发单拒绝。</p>
     *
     * @param request 汇总单号列表
     * @return 已撤销汇总单号
     */
    public CancelMedicationSummaryResponse cancelMedicationSummary(CancelMedicationSummaryRequest request) {
        this.assertCancelSummaryRequestGate(request);
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_SUM_206,
            "未登录或登录已失效，无法撤销汇总单");
        List<String> summaryNos = this.distinctSummaryNos(request.getSummaryNos());
        try {
            for (String summaryNo : summaryNos) {
                // Pre：仅待发送（PENDING_APPROVAL）type 27 汇总单允许撤销
                this.assertMedicationSummarySlipCancellable(summaryNo);
                // Cmd ①：发放行 SUMMARIZED → PREPARATION，清 summary_no
                medicationDispenseDomainService.cancelMedicationDispenseSummary(
                    nurseStationDispenseSummaryMapper.toCancelMedicationDispenseSummaryCommand(summaryNo, operatorId));
                // Cmd ②：同 bus_no 汇总 Supply 行逻辑删除
                supplyRequestDomainService.removeByBusNo(
                    nurseStationDispenseSummaryMapper.toRemoveByBusNoCommand(summaryNo, operatorId));
            }
            return nurseStationDispenseSummaryMapper.toCancelMedicationSummaryResponse(summaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("cancelMedicationSummary failed", ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_206,
                "撤销失败，仅待发送汇总单可撤销");
        }
    }

    /**
     * 待发药汇总明细分页请求门禁：须选就诊、合法药品分类 Tab 与长临筛选项（EX-IP-SUM-101）。
     *
     * @param request 分页请求
     * @throws BusinessException EX-IP-SUM-101 参数无效或无权
     */
    private void assertPendingLinesRequestGate(PagePendingDispenseSummaryLinesRequest request) {
        if (request == null
            || request.getEncounterIds() == null
            || request.getEncounterIds().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
        String tab = trimToEmpty(request.getMedicineCategoryTab());
        if (!InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)
            && !InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
        String therapy = trimToEmpty(request.getTherapyFilter());
        if (!this.isAllowedTherapyFilter(therapy)) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
    }

    /**
     * 创建汇总单请求门禁：明细非空、批量上限与 dispenseId/encounterId 成对（EX-IP-SUM-204）。
     *
     * @param request 创建请求
     * @throws BusinessException EX-IP-SUM-204 明细无效或超批量
     */
    private void assertCreateSummaryRequestGate(CreateMedicationSummaryRequest request) {
        if (request == null
            || request.getDispenseItems() == null
            || request.getDispenseItems().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_204,
                "汇总申请失败，请检查所选明细状态");
        }
        if (request.getDispenseItems().size() > InpatientNurseStationConstants.SUMMARY_WRITE_BATCH_MAX) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_204,
                "汇总保存失败，请检查所选明细状态");
        }
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item == null || item.getDispenseId() == null || item.getEncounterId() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_204,
                    "汇总保存失败，请检查所选明细状态");
            }
        }
    }

    /**
     * 发送汇总单请求门禁：汇总单号非空且单次不超过 50 张（EX-IP-SUM-205）。
     *
     * @param request 发送请求
     * @throws BusinessException EX-IP-SUM-205 参数无效或超批量
     */
    private void assertSubmitSummaryRequestGate(SubmitMedicationSummaryRequest request) {
        if (request == null
            || request.getSummaryNos() == null
            || request.getSummaryNos().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_205,
                "发送失败，请至少选择一张汇总单");
        }
        if (request.getSummaryNos().size() > 50) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_205,
                "发送失败，单次最多发送 50 张汇总单");
        }
        for (String summaryNo : request.getSummaryNos()) {
            if (StringUtils.isEmpty(summaryNo)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_205,
                    "发送失败，汇总单号不能为空");
            }
        }
    }

    /**
     * 撤销汇总单请求门禁：汇总单号非空且单次不超过 50 张（EX-IP-SUM-206）。
     *
     * @param request 撤销请求
     * @throws BusinessException EX-IP-SUM-206 参数无效或超批量
     */
    private void assertCancelSummaryRequestGate(CancelMedicationSummaryRequest request) {
        if (request == null
            || request.getSummaryNos() == null
            || request.getSummaryNos().isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_206,
                "撤销失败，请至少选择一张汇总单");
        }
        if (request.getSummaryNos().size() > 50) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_206,
                "撤销失败，单次最多撤销 50 张汇总单");
        }
        for (String summaryNo : request.getSummaryNos()) {
            if (StringUtils.isEmpty(summaryNo)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_206,
                    "撤销失败，汇总单号不能为空");
            }
        }
    }

    /**
     * 汇总单须为 type=27 且 Supply 状态为待发送方可撤销（EX-IP-SUM-103/206）。
     *
     * @param summaryNo 汇总单 bus_no
     * @throws BusinessException 单不存在、非汇总类型或状态不可撤销
     */
    private void assertMedicationSummarySlipCancellable(String summaryNo) {
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(summaryNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(query);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
        boolean hasType27 = false;
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.MEDICATION_SUMMARY_ORDER.getCode().equals(line.getTypeEnum())) {
                continue;
            }
            hasType27 = true;
            if (!SupplyStatus.PENDING_APPROVAL.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_206,
                    "撤销失败，仅待发送汇总单可撤销");
            }
        }
        if (!hasType27) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
    }

    /**
     * 汇总单须存在且含 type=27 汇总 Supply 行方可发送（EX-IP-SUM-103）。
     *
     * @param summaryNo 汇总单 bus_no
     * @throws BusinessException 单不存在或非汇总类型
     */
    private void assertMedicationSummarySlipSubmittable(String summaryNo) {
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(summaryNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(query);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
        boolean hasType27 = false;
        for (SupplyRequestModel line : lines) {
            if (line != null && SupplyType.MEDICATION_SUMMARY_ORDER.getCode().equals(line.getTypeEnum())) {
                hasType27 = true;
                break;
            }
        }
        if (!hasType27) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
    }

    /**
     * 待发药行须全部为 PREPARATION 且未绑定 summaryNo（EX-IP-SUM-201）。
     *
     * @param dispenses 待汇总发放行
     * @throws BusinessException 已汇总或状态不可汇总
     */
    private void assertAllDispensesSummarizable(List<MedicationDispense> dispenses) {
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            if (!DispenseStatus.PREPARATION.equals(dispense.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            if (StringUtils.isNotEmpty(dispense.getSummaryNo())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
        }
    }

    /**
     * 调用领域汇总发放并映射无效状态为 EX-IP-SUM-201。
     *
     * @param command 领域汇总命令
     * @return 新生成 summaryNo
     * @throws BusinessException EX-IP-SUM-201 重复汇总；其他领域码透传
     */
    private String summarizeMedicationDispensesOrThrow201(SummarizeMedicationDispensesCommand command) {
        try {
            return medicationDispenseDomainService.summarizeMedicationDispenses(command);
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_STATUS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_201,
                    "所选药品已汇总，请勿重复操作");
            }
            throw ex;
        }
    }

    /**
     * 按就诊分组加载待发药对应待结算 ChargeItem（缺账单抛 EX-IP-SUM-203）。
     *
     * @param request   创建请求（含 encounter-dispense 对）
     * @param dispenses 已加载发放行
     * @return dispenseId → ChargeItem
     * @throws BusinessException EX-IP-SUM-203 缺执行账单
     */
    private Map<Long, ChargeItem> loadChargeByDispenseId(
        CreateMedicationSummaryRequest request,
        List<MedicationDispense> dispenses) {
        Map<Long, List<Long>> dispenseIdsByEncounter = new HashMap<>();
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item == null || item.getEncounterId() == null || item.getDispenseId() == null) {
                continue;
            }
            List<Long> bucket = dispenseIdsByEncounter.get(item.getEncounterId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                dispenseIdsByEncounter.put(item.getEncounterId(), bucket);
            }
            bucket.add(item.getDispenseId());
        }
        Map<Long, ChargeItem> chargeByDispenseId =
            inpatientMedicationDispenseChargePricingLoader.mapPendingSettlementChargeByDispenseIds(
                dispenseIdsByEncounter);
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            if (!chargeByDispenseId.containsKey(dispense.getId())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_203,
                    "缺少执行账单，无法汇总，请确认医嘱已执行");
            }
        }
        return chargeByDispenseId;
    }

    /**
     * 汇总单号去重并保持请求顺序。
     *
     * @param summaryNos 原始单号列表
     * @return 去重后的单号
     */
    private List<String> distinctSummaryNos(List<String> summaryNos) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (summaryNos != null) {
            for (String summaryNo : summaryNos) {
                if (StringUtils.isNotEmpty(summaryNo)) {
                    set.add(summaryNo.trim());
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 按 bus_no 精确加载汇总单头 DTO（EX-IP-SUM-103 不存在时抛错）。
     *
     * @param summaryNo 汇总单号
     * @return 匹配的汇总单头
     * @throws BusinessException 单不存在或已失效
     */
    private NurseStationDispenseSummarySlipHeaderDto loadSlipHeaderBySummaryNo(String summaryNo) {
        PageMedicationSummarySlipHeadersRequest pageRequest = new PageMedicationSummarySlipHeadersRequest();
        pageRequest.setPageNo(1);
        pageRequest.setPageSize(1);
        pageRequest.setKeyword(summaryNo);
        SupplyRequestDocumentListQuery listQuery = nurseStationDispenseSummaryMapper.toSupplyRequestDocumentListQuery(
            pageRequest, null);
        Page<MedicationSummarySlipQueryModel> page = medicationSummarySlipLoader.pageMedicationSummarySlips(listQuery);
        List<NurseStationDispenseSummarySlipHeaderDto> headers =
            nurseStationDispenseSummaryMapper.toSlipHeaderDtos(page);
        if (headers.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_103,
                "汇总单不存在或已失效，请刷新");
        }
        for (NurseStationDispenseSummarySlipHeaderDto header : headers) {
            if (header != null && summaryNo.equals(header.getBusNo())) {
                return header;
            }
        }
        return headers.get(0);
    }

    /**
     * 解析汇总单列表查询的目的地 location 过滤（单选非 0 时返回单元素列表）。
     *
     * @param request 列表请求
     * @return purposeLocationId 列表；未指定时 null 表示不过滤
     */
    private List<Long> resolveSummaryPurposeLocationIds(PageMedicationSummarySlipHeadersRequest request) {
        Long purposeLocationId = null;
        if (request != null) {
            purposeLocationId = request.getPurposeLocationId();
        }
        if (purposeLocationId != null && purposeLocationId.longValue() != 0L) {
            List<Long> single = new ArrayList<>();
            single.add(purposeLocationId);
            return single;
        }
        return null;
    }

    /**
     * 断言请求 encounter 均在护士站可见病区范围内（EX-IP-SUM-101）。
     *
     * @param encounterIds 待校验就诊 id
     * @throws BusinessException 空列表或含不可见就诊
     */
    private void requireVisibleEncounters(List<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
        List<Long> managedWardIds = managedWardLocationIdsResolver.listManagedWardLocationIdsOrEmpty();
        InpatientEncounterPageQuery query = nurseStationDispenseSummaryMapper.toInpatientEncounterPageQuery(
            encounterIds, managedWardIds);
        List<InpatientEncounterContextQueryModel> visibleRows =
            EncounterContextReader.listInpatientEncounterContexts(query);
        List<Long> visibleEncounterIds = this.retainRequestedVisibleEncounterIds(encounterIds, visibleRows);
        if (visibleEncounterIds.size() != encounterIds.size()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_101,
                "请先选择患者或无权访问");
        }
    }

    /**
     * 从待发药分页结果收集药品定义批量查询键（去重）。
     *
     * @param page 待发药分页
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromPendingPage(
        Page<MedicationDispensePickupPendingQueryModel> page) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        if (page == null || page.getContent() == null) {
            return lookups;
        }
        Set<String> dedupe = new HashSet<>();
        for (MedicationDispensePickupPendingQueryModel row : page.getContent()) {
            if (row == null || row.getMedicationId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getMedicationId());
            if (dedupe.add(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(row.getMedicationId());
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 从汇总单明细行收集药品定义批量查询键（去重）。
     *
     * @param domainLines 汇总单明细读模型
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromSlipLines(
        List<MedicationSummarySlipLineQueryModel> domainLines) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        if (domainLines == null) {
            return lookups;
        }
        for (MedicationSummarySlipLineQueryModel row : domainLines) {
            if (row == null || row.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, row.getItemId());
            if (dedupe.add(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(row.getItemId());
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 从发放实体列表收集药品定义批量查询键（去重）。
     *
     * @param dispenses 发放行
     * @return ProductDefinition 批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromDispenses(List<MedicationDispense> dispenses) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<String> dedupe = new HashSet<>();
        if (dispenses == null) {
            return lookups;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getMedicationId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
            if (dedupe.add(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(dispense.getMedicationId());
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 从待发药分页收集 locationId 集合（展示用）。
     *
     * @param page 待发药分页
     * @return locationId 集合
     */
    private Set<Long> collectLocationIdsFromPendingPage(Page<MedicationDispensePickupPendingQueryModel> page) {
        Set<Long> ids = new HashSet<>();
        if (page == null || page.getContent() == null) {
            return ids;
        }
        for (MedicationDispensePickupPendingQueryModel row : page.getContent()) {
            if (row != null && row.getLocationId() != null) {
                ids.add(row.getLocationId());
            }
        }
        return ids;
    }

    /**
     * 按 id 逐条加载 Location 实体（可选存在）。
     *
     * @param locationIds 位置 id 集合
     * @return 已存在的 Location 列表
     */
    private List<Location> loadLocations(Set<Long> locationIds) {
        List<Location> locations = new ArrayList<>();
        if (locationIds == null) {
            return locations;
        }
        for (Long locationId : locationIds) {
            if (locationId == null) {
                continue;
            }
            locationQueryService.findOptionalById(locationId).ifPresent(locations::add);
        }
        return locations;
    }

    /**
     * 从创建汇总请求收集 dispenseId（去重保序）。
     *
     * @param request 创建请求
     * @return dispenseId 列表
     */
    private List<Long> collectDispenseIdsFromWriteRequest(CreateMedicationSummaryRequest request) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item != null && item.getDispenseId() != null) {
                ids.add(item.getDispenseId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从创建汇总请求收集 encounterId（去重保序）。
     *
     * @param request 创建请求
     * @return encounterId 列表
     */
    private List<Long> collectEncounterIdsFromWriteRequest(CreateMedicationSummaryRequest request) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CreateMedicationSummaryDispenseItemDto item : request.getDispenseItems()) {
            if (item != null && item.getEncounterId() != null) {
                ids.add(item.getEncounterId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 从合并分组收集全部 dispenseId（去重保序）。
     *
     * @param groups 汇总合并分组
     * @return dispenseId 列表
     */
    private List<Long> collectDispenseIdsFromGroups(List<SummaryMergeGroup> groups) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (groups == null) {
            return new ArrayList<>();
        }
        for (SummaryMergeGroup group : groups) {
            if (group == null || group.getDispenseIds() == null) {
                continue;
            }
            for (Long dispenseId : group.getDispenseIds()) {
                if (dispenseId != null) {
                    ids.add(dispenseId);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 医嘱执行列表 → id 索引 Map。
     *
     * @param executeOrders 医嘱读模型列表
     * @return medReqId → 读模型
     */
    private Map<Long, MedicationRequestListQueryModel> indexMedReqById(
        List<MedicationRequestListQueryModel> executeOrders) {
        Map<Long, MedicationRequestListQueryModel> map = new HashMap<>();
        if (executeOrders == null) {
            return map;
        }
        for (MedicationRequestListQueryModel row : executeOrders) {
            if (row != null && row.getId() != null) {
                map.put(row.getId(), row);
            }
        }
        return map;
    }

    /**
     * 可见就诊上下文 → encounterId 索引（限 visibleEncounterIds 子集）。
     *
     * @param visibleRows          可见就诊行
     * @param visibleEncounterIds  允许的 encounterId
     * @return encounterId → 上下文
     */
    private Map<Long, InpatientEncounterContextQueryModel> indexEncounters(
        List<InpatientEncounterContextQueryModel> visibleRows,
        List<Long> visibleEncounterIds) {
        Map<Long, InpatientEncounterContextQueryModel> map = new HashMap<>();
        Set<Long> idSet = new HashSet<>(visibleEncounterIds);
        if (visibleRows == null) {
            return map;
        }
        for (InpatientEncounterContextQueryModel row : visibleRows) {
            if (row != null && row.getEncounterId() != null && idSet.contains(row.getEncounterId())) {
                map.put(row.getEncounterId(), row);
            }
        }
        return map;
    }

    /**
     * Long id 列表去重并保持顺序。
     *
     * @param ids 原始 id 列表
     * @return 去重后的 id
     */
    private List<Long> distinctIds(List<Long> ids) {
        LinkedHashSet<Long> set = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return new ArrayList<>(set);
    }

    /**
     * 按请求顺序保留可见 encounter 交集（护士站病区过滤后）。
     *
     * @param requestedEncounterIds 请求就诊 id
     * @param visibleRows           可见就诊上下文
     * @return 可见且被请求的 encounterId（保序）
     */
    private List<Long> retainRequestedVisibleEncounterIds(
        List<Long> requestedEncounterIds,
        List<InpatientEncounterContextQueryModel> visibleRows) {
        if (requestedEncounterIds == null || requestedEncounterIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> visibleIdSet = new HashSet<>();
        if (visibleRows != null) {
            for (InpatientEncounterContextQueryModel row : visibleRows) {
                if (row != null && row.getEncounterId() != null) {
                    visibleIdSet.add(row.getEncounterId());
                }
            }
        }
        List<Long> result = new ArrayList<>();
        for (Long encounterId : requestedEncounterIds) {
            if (encounterId != null && visibleIdSet.contains(encounterId)) {
                result.add(encounterId);
            }
        }
        return result;
    }

    /**
     * 长临筛选项是否合法（全部/长期/临时）。
     *
     * @param therapy 筛选项 code
     * @return 允许为 true
     */
    private boolean isAllowedTherapyFilter(String therapy) {
        if (InpatientNurseStationConstants.THERAPY_FILTER_ALL.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_LONG.equals(therapy)) {
            return true;
        }
        if (InpatientNurseStationConstants.THERAPY_FILTER_TEMP.equals(therapy)) {
            return true;
        }
        return false;
    }
}
