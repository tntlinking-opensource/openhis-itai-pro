/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.summarydispense.service;

import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.assembler.InpatientPharmacySummaryDispenseAssembler;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummaryDispenseItemDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipHeaderDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.InpatientPharmacySummarySlipLineDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.dto.SummaryDispenseTraceLineSubmitDto;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.mapper.InpatientPharmacySummaryDispenseMapper;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.request.DispenseSummaryMedicationsRequest;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.request.GetMedicationSummaryDispenseDetailRequest;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.request.PagePendingMedicationSummarySlipsRequest;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.request.RejectSummaryDispenseRequest;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.DispenseSummaryMedicationsResponse;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.GetMedicationSummaryDispenseDetailResponse;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.PagePendingMedicationSummarySlipsResponse;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.response.RejectSummaryDispenseResponse;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.inpatient.pharmacy.dispense.support.InpatientMedicationDispenseChargePricingLoader;
import com.openhis.health.application.inpatient.pharmacy.summarydispense.support.MedicationSummarySlipLoader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberDispenseTraceOrchestrator;
import com.openhis.health.application.shared.tracenumber.DispenseTraceUnitSnapshotEnricher;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.SummaryDispenseGateQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientPharmacySummaryDispenseConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.AllocateAndDecreaseCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.application.shared.pharmacy.MedicationDispenseInventoryAvailabilityGate;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.health.domain.workflow.supplydelivery.queries.SupplyDeliveryRequestIdListQuery;
import com.openhis.health.domain.workflow.supplydelivery.queries.model.SupplyDeliveryByRequestModel;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 住院药房汇总发药应用服务（AS-IP-PHA-002，IF-IPS-01～04）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li><b>IF-IPS-01</b>：Tab 仅 {@code summaryDispenseCompletedOnly}；发放表门控（{@code SUMMARIZED}/{@code COMPLETED}）收窄 {@code busNoIn}，已发药不以 Supply {@code AGREE} 为准</li>
 *   <li><b>IF-IPS-02</b>：合并 Supply 行 + 底层发放行明细（含批号）</li>
 *   <li><b>IF-IPS-03</b>：批量核发（扣库 → complete → Delivery → AGREE → 追溯）；<b>不</b>触发医保 3505（汇总径进销存不上报）</li>
 *   <li><b>IF-IPS-04</b>：批量驳回（reject → 释放发放行）</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inpatientPharmacySummaryDispense/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientPharmacySummaryDispenseAppService {

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 可操作药房 location 收窄（BR-IPS-001）。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    /** 分页窗口 Gate（IF-IPS-01 pageNo/pageSize 必填）。 */
    private final PageWindowGate pageWindowGate;
    /** Supply 读侧（单头/明细、busNo 强加载）。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** Supply 写侧（agree/reject）。 */
    private final SupplyRequestDomainService supplyRequestDomainService;
    /** 发放读侧（汇总单号维度明细、写前强加载）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 发放写侧（complete/cancel summary）。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** Delivery 读侧（核发后查 deliveryId）。 */
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    /** Delivery 写侧（create/update completed）。 */
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    /** 库存读侧（充足校验、批号解析）。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    private final MedicationDispenseInventoryAvailabilityGate medicationDispenseInventoryAvailabilityGate;
    /** 库存写侧（合并扣减/分配扣减）。 */
    private final InventoryItemDomainService inventoryItemDomainService;
    /** 追溯读侧（出库冲突校验）。 */
    private final TraceNumberQueryService traceNumberQueryService;
    /** 追溯写侧（核发后出库记录）。 */
    private final TraceNumberDispenseTraceOrchestrator traceNumberDispenseTraceOrchestrator;
    /** 发药行主档贴码快照回填（与患者发药同口径）。 */
    private final DispenseTraceUnitSnapshotEnricher dispenseTraceUnitSnapshotEnricher;
    /** 药品目录批量读侧。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 医务人员查询（调配药师校验）。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 库位名称展示。 */
    private final LocationQueryService locationQueryService;
    /** 患者姓名批量展示。 */
    private final PatientQueryService patientQueryService;
    /** 住院就诊上下文（床号展示）。 */
    private final EncounterContextReader encounterContextReader;
    /** 发放 ↔ 待结算账单只读协作（B.6.1.3）。 */
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    /** 汇总单头 SupplyRequest + SupplyDelivery 读编排（B.6.1.3）。 */
    private final MedicationSummarySlipLoader medicationSummarySlipLoader;
    /** Request/Query/Command/Response 映射。 */
    private final InpatientPharmacySummaryDispenseMapper inpatientPharmacySummaryDispenseMapper;
    /** 详情合并行/发放行装配。 */
    private final InpatientPharmacySummaryDispenseAssembler inpatientPharmacySummaryDispenseAssembler;

    /**
     * IF-IPS-01：分页查询汇总单头（待核对 / 已发药 Tab）。
     *
     * @param request {@code summaryDispenseCompletedOnly} 切换 Tab；发放表门控收窄单号，不接受客户端 Supply 审批态入参
     * @return 汇总单头分页（含 {@code summaryDispenseCompleted} 门控字段）
     * @throws BusinessException {@code EX-IPS-101} 未登录或无权；{@code EX-IPS-102} 查询失败
     */
    public PagePendingMedicationSummarySlipsResponse pagePendingMedicationSummarySlips(
        PagePendingMedicationSummarySlipsRequest request) {
        try {
            // Gate：分页参数必填
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientPharmacySummaryDispenseConstants.EX_IPS_102,
                "加载失败，请稍后重试");
            // Gate：解析当前用户可操作药房（与护士站 IF-IP-SUM-02 同族门控）
            List<Long> purposeLocationIds = this.resolvePharmacyPurposeLocationIds(request);
            // 严格模式且无岗位可操作药房：拒绝（非 strict 且 empty 时不拼 purpose 谓词，与护士站汇总 Tab 一致）
            if (purposeLocationIds.isEmpty() && managedLocationIdsResolver.isLocationScopeEnforcementActive()) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
                    "未登录或无权操作该药房");
            }
            // Pre：指定药房须在管辖集合内
            this.assertPurposeLocationInManagedScope(request, purposeLocationIds);
            boolean completedOnly =
                request != null && Boolean.TRUE.equals(request.getSummaryDispenseCompletedOnly());
            // Pre：Tab 对应发放表一致态（待发 SUMMARIZED / 已发药 COMPLETED），不以 Supply AGREE 替代
            DispenseStatus requiredDispenseStatus = DispenseStatus.SUMMARIZED;
            if (completedOnly) {
                requiredDispenseStatus = DispenseStatus.COMPLETED;
            }
            List<String> busNoIn = medicationDispenseQueryService.listSummaryNosWhereAllDispenseRowsHaveStatus(
                requiredDispenseStatus, purposeLocationIds);
            // 发放表无满足门控的单号：直接空页，避免回退查全量 Supply
            if (busNoIn.isEmpty()) {
                return inpatientPharmacySummaryDispenseMapper.toPagePendingMedicationSummarySlipsResponse(
                    Page.empty(), new ArrayList<>());
            }
            // Pre：装配 Query（type 27 + busNoIn；待发服务端写死 Supply APPROVAL）
            SupplyRequestDocumentListQuery listQuery =
                inpatientPharmacySummaryDispenseMapper.toSupplyRequestDocumentListQuery(request, purposeLocationIds);
            listQuery.setBusNoIn(busNoIn);
            // Pre：域分页查询汇总单头
            Page<MedicationSummarySlipQueryModel> page =
                medicationSummarySlipLoader.pageMedicationSummarySlips(listQuery);
            // Asm：单头 DTO + 发放表门控 enrichment
            List<InpatientPharmacySummarySlipHeaderDto> rows =
                inpatientPharmacySummaryDispenseMapper.toSummarySlipHeaderDtos(page);
            this.enrichSummarySlipHeadersWithDispenseGate(rows);
            // Asm：分页响应
            return inpatientPharmacySummaryDispenseMapper.toPagePendingMedicationSummarySlipsResponse(page, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("pagePendingMedicationSummarySlips failed", ex);
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IPS-02：查询汇总单详情（合并行 + 发放行）。
     *
     * @param request summaryNo
     * @return 单头、合并 Supply 行与底层发放行
     * @throws BusinessException {@code EX-IPS-103} 单号无效或状态不可读；{@code EX-IPS-102} 其他异常
     */
    public GetMedicationSummaryDispenseDetailResponse getMedicationSummaryDispenseDetail(
        GetMedicationSummaryDispenseDetailRequest request) {
        try {
            // Gate：summaryNo 非空
            if (request == null || StringUtils.isEmpty(request.getSummaryNo())) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                    "汇总单不存在或已失效，请刷新");
            }
            String summaryNo = request.getSummaryNo().trim();
            // Pre：强加载 type 27 Supply 行（中转单据，不用于判定已发药）
            List<SupplyRequestModel> supplyLines = this.loadSummarySupplyLinesOrThrow(summaryNo);
            // Pre：同号发放行明细（已发药真源）
            List<MedicationDispenseSummaryItemQueryModel> dispenseRows =
                medicationDispenseQueryService.listMedicationDispensesBySummaryNo(summaryNo);
            SummaryDispenseGateQueryModel dispenseGate =
                this.resolveSummaryDispenseGate(dispenseRows, summaryNo);
            // Pre：发放行须同号且态统一（SUMMARIZED 待核对 / COMPLETED 已发药）
            this.assertDispenseRowsReadable(
                dispenseRows,
                summaryNo,
                InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                "汇总单不存在或已失效，请刷新");
            // 待发核对：Supply 须仍为 APPROVAL，拒绝 AGREE+SUMMARIZED 等脏数据可读
            if (dispenseGate.isAllSummarized()) {
                this.assertAllSupplyLinesApproval(
                    supplyLines,
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                    "汇总单不存在或已失效，请刷新");
            }
            // Pre：合并 Supply 明细行
            List<MedicationSummarySlipLineQueryModel> mergedDomainLines =
                supplyRequestQueryService.listMedicationSummarySlipLinesByBusNo(summaryNo);
            if (mergedDomainLines.isEmpty()) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                    "汇总单不存在或已失效，请刷新");
            }
            // Pre：跨聚合只读累加单价/总价（BR-IP-SUM-009）
            inpatientMedicationDispenseChargePricingLoader.enrichSummarySlipLineChargePricing(
                summaryNo, mergedDomainLines);
            // Pre：药品目录批量加载
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectProductLookups(mergedDomainLines, dispenseRows));
            // Pre：患者/就诊/库位展示批量索引
            Set<Long> patientIds = new HashSet<>();
            Set<Long> encounterIds = new HashSet<>();
            Set<Long> locationIds = new HashSet<>();
            for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
                if (row == null) {
                    continue;
                }
                if (row.getPatientId() != null) {
                    patientIds.add(row.getPatientId());
                }
                if (row.getEncounterId() != null) {
                    encounterIds.add(row.getEncounterId());
                }
                if (row.getLocationId() != null) {
                    locationIds.add(row.getLocationId());
                }
            }
            Map<Long, String> patientNameById = patientQueryService.findPatientNamesByIds(patientIds);
            Map<Long, String> bedDisplayByEncounterId = this.loadBedDisplayByEncounterIds(encounterIds);
            Map<Long, String> locationNameById = locationQueryService.findLocationNamesByIds(locationIds);
            // Pre：单头（与 IF-IPS-01 同构）+ 发放表门控
            InpatientPharmacySummarySlipHeaderDto header = this.loadSlipHeaderBySummaryNo(summaryNo);
            this.applySummaryDispenseGateToHeader(header, dispenseGate);
            boolean summaryDispenseCompleted =
                dispenseGate != null && dispenseGate.isAllCompleted();
            // Asm：合并行 DTO（禁止 lotNumber；追溯码读 SupplyDelivery）
            Map<Long, String> traceBySupplyRequestId =
                this.loadTraceBySupplyRequestIdForDetail(supplyLines, summaryDispenseCompleted);
            String purposeLocationName = null;
            if (header != null) {
                purposeLocationName = header.getPurposeLocationName();
            }
            List<InpatientPharmacySummarySlipLineDto> mergedLines =
                inpatientPharmacySummaryDispenseAssembler.buildMergedLineDtos(
                    mergedDomainLines,
                    productByKey,
                    purposeLocationName,
                    traceBySupplyRequestId);
            // Asm：发放行 DTO（含批号；汇总路径不含追溯码）
            List<InpatientPharmacySummaryDispenseItemDto> dispenseItems =
                inpatientPharmacySummaryDispenseAssembler.buildDispenseItemDtos(
                    dispenseRows, productByKey, patientNameById, bedDisplayByEncounterId, locationNameById);
            // Asm：合并行回填主档贴码快照 + 代表批号（待扫换算与门诊/住院患者发药同口径）
            this.enrichSummaryMergedLinesTraceUnitSnapshot(mergedLines, dispenseItems, header);
            // Asm：详情响应
            GetMedicationSummaryDispenseDetailResponse response =
                inpatientPharmacySummaryDispenseMapper.toGetMedicationSummaryDispenseDetailResponse(
                    summaryNo, header, mergedLines, dispenseItems);
            response.setSummaryDispenseCompleted(summaryDispenseCompleted);
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("getMedicationSummaryDispenseDetail failed", ex);
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_102,
                "加载失败，请稍后重试");
        }
    }

    /**
     * IF-IPS-03：批量汇总核发（扣库 + 完成发放 + Delivery + AGREE + 追溯）。
     *
     * @param request 汇总单号、领药人、调配药师与可选追溯码
     * @return 已成功核发单号
     * @throws BusinessException {@code EX-IPS-101}～{@code EX-IPS-205} 见 AS §5
     */
    public DispenseSummaryMedicationsResponse dispenseSummaryMedications(DispenseSummaryMedicationsRequest request) {
        // Gate：批上限与必填参数
        List<String> summaryNos;
        if (request == null) {
            summaryNos = this.distinctSummaryNos(null);
        } else {
            summaryNos = this.distinctSummaryNos(request.getSummaryNos());
        }
        if (summaryNos.isEmpty()) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_205,
                "核发失败，请检查汇总单状态");
        }
        if (summaryNos.size() > InpatientPharmacySummaryDispenseConstants.SUMMARY_DISPENSE_BATCH_MAX) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_205,
                "核发失败，请检查汇总单状态");
        }
        if (request.getReceiverId() == null) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_205,
                "核发失败，请检查汇总单状态");
        }
        if (request.getPreparerPractitionerId() == null) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_204,
                "追溯码冲突或请选择有效调配药师");
        }
        // Gate：操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        // Gate：发药药师
        Long dispenserPractitionerId = currentPractitionerResolver.requirePractitionerId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        // Gate：调配药师存在性
        Practitioner preparer = practitionerQueryService.findById(request.getPreparerPractitionerId());
        if (preparer == null) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_204,
                "追溯码冲突或请选择有效调配药师");
        }
        // Gate：调配药师不得与发药药师同人（BR-IPS-005）
        if (Objects.equals(request.getPreparerPractitionerId(), dispenserPractitionerId)) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_204,
                "追溯码冲突或请选择有效调配药师");
        }
        List<String> succeededSummaryNos = new ArrayList<>();
        try {
            for (String summaryNo : summaryNos) {
                // Pre：强加载 Supply 行并断言 APPROVAL
                List<SupplyRequestModel> supplyLines = this.loadSummarySupplyLinesOrThrow(summaryNo);
                this.assertAllSupplyLinesApproval(
                    supplyLines,
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_201,
                    "汇总单已核发或状态不允许，请刷新");
                // Pre：强加载发放行实体
                List<MedicationDispenseSummaryItemQueryModel> dispenseQueryRows =
                    medicationDispenseQueryService.listMedicationDispensesBySummaryNo(summaryNo);
                this.assertAllDispenseRowsSummarized(
                    dispenseQueryRows,
                    summaryNo,
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_201,
                    "汇总单已核发或状态不允许，请刷新");
                List<Long> dispenseIds = new ArrayList<>();
                for (MedicationDispenseSummaryItemQueryModel row : dispenseQueryRows) {
                    if (row != null && row.getId() != null) {
                        dispenseIds.add(row.getId());
                    }
                }
                List<MedicationDispense> dispenses =
                    medicationDispenseQueryService.listMedicationDispensesByIds(dispenseIds);
                if (dispenses.size() != dispenseIds.size()) {
                    throw new BusinessException(
                        InpatientPharmacySummaryDispenseConstants.EX_IPS_201,
                        "汇总单已核发或状态不允许，请刷新");
                }
                // Pre：汇总追溯码仅落 SupplyDelivery，不写入患者发放行
                Map<Long, String> traceBySupplyRequestId =
                    this.buildTraceOverrideBySupplyRequestId(request.getTraceLines());
                List<String> allTraceCodes = this.collectTraceCodesFromSupplyLines(supplyLines, traceBySupplyRequestId);
                List<String> hardHospitalCodes = new ArrayList<>();
                for (SupplyRequestModel supplyLine : supplyLines) {
                    if (supplyLine == null || supplyLine.getId() == null) {
                        continue;
                    }
                    String traceNoRaw = traceBySupplyRequestId.get(supplyLine.getId());
                    List<String> lineTraceCodes = splitTraceCodes(traceNoRaw);
                    if (lineTraceCodes.isEmpty()) {
                        continue;
                    }
                    if (supplyLine.getPurposeLocationId() == null) {
                        throw new BusinessException(
                            InpatientPharmacySummaryDispenseConstants.EX_IPS_204,
                            "追溯码冲突或请选择有效调配药师");
                    }
                    // Gate：有码批本库优先否则全院；无码批跳过硬校验
                    String lotForAssert = this.resolveSummaryTraceLotNumber(supplyLine, traceNoRaw);
                    boolean lotHasMaster = traceNumberQueryService.existsMasterByItemLot(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        supplyLine.getItemId(),
                        lotForAssert);
                    if (!lotHasMaster) {
                        continue;
                    }
                    hardHospitalCodes.addAll(lineTraceCodes);
                    traceNumberQueryService.assertCodesInNetStockForDispenseLine(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        supplyLine.getItemId(),
                        lotForAssert,
                        supplyLine.getPurposeLocationId(),
                        lineTraceCodes);
                }
                traceNumberQueryService.assertNoDuplicateTraceCodes(allTraceCodes);
                if (!hardHospitalCodes.isEmpty()) {
                    traceNumberQueryService.assertCodesInHospitalForDispense(hardHospitalCodes);
                }
                // Pre：药品目录（单位换算）
                Map<String, ProductDefinitionDisplayDto> productByKey =
                    productDefinitionDisplayTerminologyAssembler.loadAsMap(this.collectProductLookupsFromDispenses(dispenses));
                // Pre：逐行库存充足预校验（BR-IPS-006）
                for (MedicationDispense dispense : dispenses) {
                    if (dispense == null) {
                        continue;
                    }
                    String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
                    ProductDefinitionDisplayDto productRow = productByKey.get(productKey);
                    if (productRow == null) {
                        throw new BusinessException(
                            InpatientPharmacySummaryDispenseConstants.EX_IPS_203,
                            "库存不足，请联系库管");
                    }
                    BigDecimal requiredQuantity = dispense.getQuantity();
                    if (requiredQuantity == null) {
                        requiredQuantity = dispense.getDispenseQuantity();
                    }
                    medicationDispenseInventoryAvailabilityGate.assertSufficientForMedicationDispenseWithReserve(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        dispense.getMedicationId(),
                        dispense.getLocationId(),
                        dispense.getLotNumber(),
                        requiredQuantity,
                        dispense.getUnitCode(),
                        productRow,
                        dispense.getId());
                }
                // Cmd：按 lot+location 合并扣库（仅 MedicationDispense 行；BR-IPS-008 须先于发放终态）
                Map<Long, ProductDefinitionDisplayDto> productByMedicationId = new HashMap<>();
                for (MedicationDispense dispense : dispenses) {
                    if (dispense == null || dispense.getMedicationId() == null) {
                        continue;
                    }
                    String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, dispense.getMedicationId());
                    ProductDefinitionDisplayDto productRow = productByKey.get(compositeKey);
                    if (productRow != null) {
                        productByMedicationId.put(dispense.getMedicationId(), productRow);
                    }
                }
                this.applyInventoryDeductionForDispensedLines(dispenses, operatorId, productByMedicationId);
                // Cmd：发放行 SUMMARIZED → COMPLETED，写入配药/发药人与时刻（发放表为核发终态真源，BR-IPS-008）
                medicationDispenseDomainService.completeSummaryDispense(
                    inpatientPharmacySummaryDispenseMapper.toCompleteSummaryDispenseCommand(
                        summaryNo,
                        operatorId,
                        dispenserPractitionerId,
                        request.getPreparerPractitionerId()));
                // Cmd：创建 SupplyDelivery（写 receiverId 与汇总追溯码）
                supplyDeliveryDomainService.createSummarySupplyDelivery(
                    inpatientPharmacySummaryDispenseMapper.toCreateSummarySupplyDeliveryCommand(
                        request.getReceiverId(), operatorId, supplyLines, traceBySupplyRequestId));
                // Pre：查新建 deliveryIds
                List<Long> supplyRequestIds = new ArrayList<>();
                for (SupplyRequestModel line : supplyLines) {
                    if (line != null && line.getId() != null) {
                        supplyRequestIds.add(line.getId());
                    }
                }
                SupplyDeliveryRequestIdListQuery deliveryQuery = new SupplyDeliveryRequestIdListQuery();
                deliveryQuery.setRequestIdList(supplyRequestIds);
                List<SupplyDeliveryByRequestModel> deliveryRows =
                    supplyDeliveryQueryService.selectByRequestIdList(deliveryQuery);
                List<Long> deliveryIds = new ArrayList<>();
                for (SupplyDeliveryByRequestModel deliveryRow : deliveryRows) {
                    if (deliveryRow != null && deliveryRow.getDeliveryId() != null) {
                        deliveryIds.add(deliveryRow.getDeliveryId());
                    }
                }
                LocalDateTime completedTime = LocalDateTime.now();
                // Cmd：Delivery PREPARATION → COMPLETED
                if (!deliveryIds.isEmpty()) {
                    supplyDeliveryDomainService.updateCompletedStatusBatch(
                        inpatientPharmacySummaryDispenseMapper.toUpdateCompletedStatusBatchCommand(
                            deliveryIds, dispenserPractitionerId, operatorId, completedTime));
                }
                // Cmd：Supply APPROVAL → AGREE（审批链收尾；Supply 仅为中转，终态以 MedicationDispense COMPLETED 为准）
                supplyRequestDomainService.agreeRequest(
                    inpatientPharmacySummaryDispenseMapper.toAgreeRequestCommand(
                        summaryNo, dispenserPractitionerId, operatorId));
                // Cmd：追溯出库（SupplyDelivery 维度）
                for (SupplyRequestModel supplyLine : supplyLines) {
                    if (supplyLine == null || supplyLine.getId() == null) {
                        continue;
                    }
                    String traceNoRaw = traceBySupplyRequestId.get(supplyLine.getId());
                    if (StringUtils.isEmpty(traceNoRaw)) {
                        continue;
                    }
                    String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        TableNameConstants.MED_MEDICATION_DEFINITION, supplyLine.getItemId());
                    ProductDefinitionDisplayDto productRow = productByKey.get(productKey);
                    String lotNumber = this.resolveSummaryTraceLotNumber(supplyLine, traceNoRaw);
                    MedicationProductUnitConversionSource unitConversionSource = null;
                    if (productRow != null) {
                        unitConversionSource = ProductDefinitionDisplaySupport.toUnitConversionSource(productRow);
                    }
                    traceNumberDispenseTraceOrchestrator.recordSummaryDispenseOutLine(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        supplyLine.getItemId(),
                        supplyLine.getPurposeLocationId(),
                        lotNumber,
                        traceNoRaw,
                        supplyLine.getUnitCode(),
                        supplyLine.getItemQuantity(),
                        unitConversionSource,
                        summaryNo,
                        operatorId);
                }
                succeededSummaryNos.add(summaryNo);
            }
            DispenseSummaryMedicationsResponse response =
                inpatientPharmacySummaryDispenseMapper.toDispenseSummaryMedicationsResponse(succeededSummaryNos);
            // Asm：汇总核发本地闭环完成即返回；医保 3505 不在汇总径触发（与按患者发药分流）
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("dispenseSummaryMedications failed", ex);
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_205,
                "核发失败，请检查汇总单状态");
        }
    }

    /**
     * IF-IPS-04：批量汇总驳回（reject + 释放发放行）。
     *
     * @param request 汇总单号与可选驳回原因
     * @return 已驳回单号
     * @throws BusinessException {@code EX-IPS-103}、{@code EX-IPS-202}、{@code EX-IPS-205}
     */
    public RejectSummaryDispenseResponse rejectSummaryDispense(RejectSummaryDispenseRequest request) {
        // Gate：批上限与非空
        List<String> summaryNos;
        if (request == null) {
            summaryNos = this.distinctSummaryNos(null);
        } else {
            summaryNos = this.distinctSummaryNos(request.getSummaryNos());
        }
        if (summaryNos.isEmpty()) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_205,
                "驳回失败，请检查汇总单状态");
        }
        if (summaryNos.size() > InpatientPharmacySummaryDispenseConstants.SUMMARY_DISPENSE_BATCH_MAX) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_205,
                "驳回失败，请检查汇总单状态");
        }
        // Gate：操作人与审批人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        Long approverId = currentPractitionerResolver.requirePractitionerId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        List<String> rejectedSummaryNos = new ArrayList<>();
        try {
            for (String summaryNo : summaryNos) {
                // Pre：强加载 Supply 行
                List<SupplyRequestModel> supplyLines = this.loadSummarySupplyLinesOrThrow(summaryNo);
                // Pre：须全 APPROVAL
                this.assertAllSupplyLinesApproval(
                    supplyLines,
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_202,
                    "当前状态无法驳回，请刷新");
                // Cmd：Supply APPROVAL → REJECT
                String rejectReasonCode;
                if (request == null) {
                    rejectReasonCode = null;
                } else {
                    rejectReasonCode = request.getRejectReasonCode();
                }
                supplyRequestDomainService.rejectRequest(
                    inpatientPharmacySummaryDispenseMapper.toRejectRequestCommand(
                        summaryNo,
                        approverId,
                        operatorId,
                        rejectReasonCode));
                // Cmd：发放行 SUMMARIZED → PREPARATION，清 summary_no
                medicationDispenseDomainService.cancelMedicationDispenseSummary(
                    inpatientPharmacySummaryDispenseMapper.toCancelMedicationDispenseSummaryCommand(
                        summaryNo, operatorId));
                rejectedSummaryNos.add(summaryNo);
            }
            // Asm：驳回成功响应
            return inpatientPharmacySummaryDispenseMapper.toRejectSummaryDispenseResponse(rejectedSummaryNos);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("rejectSummaryDispense failed", ex);
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_205,
                "驳回失败，请检查汇总单状态");
        }
    }

    /**
     * 解析 IF-IPS-01 可操作药房 id 列表：指定药房或当前用户全部管辖药房。
     *
     * @param request 列表请求（含可选 purposeLocationId）
     * @return 用于 Supply 查询收窄的 location id 列表
     * @throws BusinessException {@code EX-IPS-101} 指定药房不在管辖范围
     */
    private List<Long> resolvePharmacyPurposeLocationIds(PagePendingMedicationSummarySlipsRequest request) {
        Long purposeLocationId = null;
        if (request != null) {
            purposeLocationId = request.getPurposeLocationId();
        }
        if (purposeLocationId != null && purposeLocationId.longValue() != 0L) {
            Set<Long> managed = managedLocationIdsResolver.managedLocationIdSetOrEmpty();
            if (!managed.isEmpty() && !managed.contains(purposeLocationId)) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
                    "未登录或无权操作该药房");
            }
            List<Long> single = new ArrayList<>();
            single.add(purposeLocationId);
            return single;
        }
        List<Location> pharmacies = managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty();
        List<Long> ids = new ArrayList<>();
        for (Location pharmacy : pharmacies) {
            if (pharmacy != null && pharmacy.getId() != null) {
                ids.add(pharmacy.getId());
            }
        }
        return ids;
    }

    /**
     * 断言请求指定药房落在已解析的可操作药房集合内（BR-IPS-001）。
     *
     * @param request            列表请求
     * @param purposeLocationIds  已解析药房 id 集合
     * @throws BusinessException {@code EX-IPS-101} 药房越权
     */
    private void assertPurposeLocationInManagedScope(
        PagePendingMedicationSummarySlipsRequest request,
        List<Long> purposeLocationIds) {
        if (request == null || request.getPurposeLocationId() == null) {
            return;
        }
        Long purposeLocationId = request.getPurposeLocationId();
        if (purposeLocationId.longValue() == 0L) {
            return;
        }
        if (!purposeLocationIds.contains(purposeLocationId)) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
                "未登录或无权操作该药房");
        }
    }

    /**
     * 按 busNo 强加载 type 27 汇总 Supply 行。
     *
     * @param summaryNo 汇总单号
     * @return 同号全部汇总 Supply 行
     * @throws BusinessException {@code EX-IPS-103} 无 type 27 行
     */
    private List<SupplyRequestModel> loadSummarySupplyLinesOrThrow(String summaryNo) {
        SupplyByBusNoQuery busNoQuery = new SupplyByBusNoQuery();
        busNoQuery.setBusNo(summaryNo);
        List<SupplyRequestModel> allLines = supplyRequestQueryService.getSupplyByBusNo(busNoQuery);
        List<SupplyRequestModel> summaryLines = new ArrayList<>();
        for (SupplyRequestModel line : allLines) {
            if (line == null) {
                continue;
            }
            if (SupplyType.MEDICATION_SUMMARY_ORDER.getCode().equals(line.getTypeEnum())) {
                summaryLines.add(line);
            }
        }
        if (summaryLines.isEmpty()) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                "汇总单不存在或已失效，请刷新");
        }
        return summaryLines;
    }

    /**
     * 断言 Supply 行全部处于 APPROVAL（待发药审批态）。
     *
     * @param supplyLines 汇总 Supply 行
     * @param errorCode   失败业务码（读 EX-IPS-103 / 核发 EX-IPS-201 / 驳回 EX-IPS-202）
     * @param message     用户提示
     */
    private void assertAllSupplyLinesApproval(
        List<SupplyRequestModel> supplyLines,
        String errorCode,
        String message) {
        String approvalCode = SupplyStatus.APPROVAL.getCode();
        for (SupplyRequestModel line : supplyLines) {
            if (line == null) {
                continue;
            }
            // 任一供应行非 APPROVAL：不可核发/不可作待发详情
            if (!approvalCode.equals(line.getStatusEnum())) {
                throw new BusinessException(errorCode, message);
            }
        }
    }

    /**
     * 读路径断言：同 {@code summaryNo} 下发放行非空、summary_no 一致且态统一（SUMMARIZED 或 COMPLETED）。
     *
     * @param dispenseRows  发放行 QueryModel 列表
     * @param summaryNo     汇总单号
     * @param errorCode     失败业务码
     * @param message       用户提示
     */
    private void assertDispenseRowsReadable(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows,
        String summaryNo,
        String errorCode,
        String message) {
        if (dispenseRows == null || dispenseRows.isEmpty()) {
            throw new BusinessException(errorCode, message);
        }
        DispenseStatus uniformStatus = null;
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isEmpty(row.getSummaryNo())) {
                throw new BusinessException(errorCode, message);
            }
            if (summaryNo != null && row.getSummaryNo() != null && !summaryNo.equals(row.getSummaryNo())) {
                throw new BusinessException(errorCode, message);
            }
            DispenseStatus rowStatus = row.getStatusEnum();
            // 读路径仅允许待发 SUMMARIZED 或已发药 COMPLETED，其余态视为不可读
            if (!DispenseStatus.SUMMARIZED.equals(rowStatus)
                && !DispenseStatus.COMPLETED.equals(rowStatus)) {
                throw new BusinessException(errorCode, message);
            }
            if (uniformStatus == null) {
                uniformStatus = rowStatus;
            } else if (!uniformStatus.equals(rowStatus)) {
                // 同号发放行态不一致：拒绝详情/列表穿透
                throw new BusinessException(errorCode, message);
            }
        }
        if (uniformStatus == null) {
            throw new BusinessException(errorCode, message);
        }
    }

    /**
     * 由发放行列表解析汇总单号门控（已发药仅以 {@code COMPLETED} 为准）。
     *
     * @param dispenseRows 同号发放行 QueryModel
     * @param summaryNo    汇总单号
     * @return 门控快照（{@code allCompleted}/{@code allSummarized}）
     */
    private SummaryDispenseGateQueryModel resolveSummaryDispenseGate(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows,
        String summaryNo) {
        SummaryDispenseGateQueryModel gate = new SummaryDispenseGateQueryModel();
        gate.setSummaryNo(summaryNo);
        if (dispenseRows == null || dispenseRows.isEmpty()) {
            return gate;
        }
        boolean allCompleted = true;
        boolean allSummarized = true;
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row == null) {
                continue;
            }
            if (!DispenseStatus.COMPLETED.equals(row.getStatusEnum())) {
                allCompleted = false;
            }
            if (!DispenseStatus.SUMMARIZED.equals(row.getStatusEnum())) {
                allSummarized = false;
            }
        }
        gate.setAllCompleted(allCompleted);
        gate.setAllSummarized(allSummarized);
        return gate;
    }

    /**
     * 列表单头批量回填发放表门控展示与发药时刻（覆盖 SupplyDelivery 时刻，以发放表为准）。
     *
     * @param rows IF-IPS-01 单头 DTO 列表
     */
    private void enrichSummarySlipHeadersWithDispenseGate(List<InpatientPharmacySummarySlipHeaderDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> summaryNos = new ArrayList<>();
        for (InpatientPharmacySummarySlipHeaderDto row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getBusNo())) {
                summaryNos.add(row.getBusNo().trim());
            }
        }
        Map<String, SummaryDispenseGateQueryModel> gateBySummaryNo =
            medicationDispenseQueryService.indexSummaryDispenseGateBySummaryNos(summaryNos);
        for (InpatientPharmacySummarySlipHeaderDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            SummaryDispenseGateQueryModel gate = gateBySummaryNo.get(row.getBusNo().trim());
            this.applySummaryDispenseGateToHeader(row, gate);
        }
    }

    /**
     * 单头 DTO 回填发放表门控字段。
     *
     * @param header 待 enrichment 的单头
     * @param gate   发放表门控读模型；空则跳过
     */
    private void applySummaryDispenseGateToHeader(
        InpatientPharmacySummarySlipHeaderDto header,
        SummaryDispenseGateQueryModel gate) {
        if (header == null || gate == null) {
            return;
        }
        header.setSummaryDispenseCompleted(gate.isAllCompleted());
        header.setSummaryDispenseStatusText(
            inpatientPharmacySummaryDispenseAssembler.resolveSummaryDispenseGateDisplayText(gate));
        // 已发药时刻以发放行 dispense_time 最大值为准，不用 Supply 审批态推断
        if (gate.getMaxDispenseTime() != null) {
            header.setDispenseTime(gate.getMaxDispenseTime());
        }
    }

    /**
     * 断言同汇总单号下发放行全部处于 SUMMARIZED 且 summaryNo 一致。
     *
     * @param dispenseRows 发放行 QueryModel 列表
     * @param summaryNo    汇总单号
     * @param errorCode    失败业务码（读路径 EX-IPS-103；写路径 EX-IPS-201）
     * @param message      用户提示
     */
    private void assertAllDispenseRowsSummarized(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows,
        String summaryNo,
        String errorCode,
        String message) {
        if (dispenseRows == null || dispenseRows.isEmpty()) {
            throw new BusinessException(errorCode, message);
        }
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row == null) {
                continue;
            }
            // 发放行须仍为汇总批次，否则不能核发或详情已失效
            if (!DispenseStatus.SUMMARIZED.equals(row.getStatusEnum())) {
                throw new BusinessException(errorCode, message);
            }
            if (summaryNo != null && row.getSummaryNo() != null && !summaryNo.equals(row.getSummaryNo())) {
                throw new BusinessException(errorCode, message);
            }
        }
    }

    /**
     * 按汇总单号加载单头展示（详情场景不限 APPROVAL 过滤）。
     *
     * @param summaryNo 汇总单号
     * @return 单头 DTO
     * @throws BusinessException {@code EX-IPS-103} 单号不存在
     */
    private InpatientPharmacySummarySlipHeaderDto loadSlipHeaderBySummaryNo(String summaryNo) {
        PagePendingMedicationSummarySlipsRequest pageRequest = new PagePendingMedicationSummarySlipsRequest();
        pageRequest.setPageNo(1);
        pageRequest.setPageSize(1);
        pageRequest.setKeyword(summaryNo);
        SupplyRequestDocumentListQuery listQuery =
            inpatientPharmacySummaryDispenseMapper.toSupplyRequestDocumentListQuery(pageRequest, null);
        listQuery.setStatusEnum(null);
        Page<MedicationSummarySlipQueryModel> page = medicationSummarySlipLoader.pageMedicationSummarySlips(listQuery);
        List<InpatientPharmacySummarySlipHeaderDto> headers =
            inpatientPharmacySummaryDispenseMapper.toSummarySlipHeaderDtos(page);
        for (InpatientPharmacySummarySlipHeaderDto header : headers) {
            if (header != null && summaryNo.equals(header.getBusNo())) {
                return header;
            }
        }
        if (headers.isEmpty()) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                "汇总单不存在或已失效，请刷新");
        }
        return headers.get(0);
    }

    /**
     * 批量加载就诊 id → 床号展示（经 EncounterContextReader）。
     *
     * @param encounterIds 就诊主键集合
     * @return 床号展示索引
     */
    private Map<Long, String> loadBedDisplayByEncounterIds(Set<Long> encounterIds) {
        Map<Long, String> bedByEncounterId = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return bedByEncounterId;
        }
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setEncounterIdsIn(encounterIds);
        List<InpatientEncounterContextQueryModel> rows =
            encounterContextReader.listInpatientEncounterContexts(query);
        for (InpatientEncounterContextQueryModel row : rows) {
            if (row != null && row.getEncounterId() != null) {
                bedByEncounterId.put(row.getEncounterId(), row.getBedName());
            }
        }
        return bedByEncounterId;
    }

    /**
     * 从合并行与发放行收集药品目录批量查询键（去重）。
     *
     * @param mergedLines  合并 Supply 行
     * @param dispenseRows 发放行
     * @return 目录批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookups(
        List<MedicationSummarySlipLineQueryModel> mergedLines,
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows) {
        Set<String> dedupe = new HashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        if (mergedLines != null) {
            for (MedicationSummarySlipLineQueryModel row : mergedLines) {
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
        }
        if (dispenseRows != null) {
            for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
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
        }
        return lookups;
    }

    /**
     * 从发放实体列表收集药品目录批量查询键（核发扣库单位换算用）。
     *
     * @param dispenses 发放实体列表
     * @return 目录批量 lookup 列表
     */
    private List<ProductDefinitionBatchLookup> collectProductLookupsFromDispenses(List<MedicationDispense> dispenses) {
        Set<String> dedupe = new HashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
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
     * 批量写请求汇总单号去重（保序、trim）。
     *
     * @param summaryNos 原始单号列表
     * @return 去重后的非空单号列表
     */
    private List<String> distinctSummaryNos(List<String> summaryNos) {
        List<String> result = new ArrayList<>();
        if (summaryNos == null) {
            return result;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String summaryNo : summaryNos) {
            if (StringUtils.isEmpty(summaryNo)) {
                continue;
            }
            String trimmed = summaryNo.trim();
            if (seen.add(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 将请求追溯码按供应行 id 索引（汇总路径不落 MedicationDispense）。
     *
     * @param traceLines 可选追溯码提交行
     * @return supplyRequestId → 追溯码串
     */
    private Map<Long, String> buildTraceOverrideBySupplyRequestId(
        List<SummaryDispenseTraceLineSubmitDto> traceLines) {
        Map<Long, String> traceOverrideBySupplyRequestId = new HashMap<>();
        if (traceLines == null) {
            return traceOverrideBySupplyRequestId;
        }
        for (SummaryDispenseTraceLineSubmitDto traceLine : traceLines) {
            if (traceLine == null || traceLine.getSupplyRequestId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(traceLine.getTraceNo())) {
                traceOverrideBySupplyRequestId.put(traceLine.getSupplyRequestId(), traceLine.getTraceNo().trim());
            }
        }
        return traceOverrideBySupplyRequestId;
    }

    /**
     * 已发药详情：从 SupplyDelivery 回填供应行追溯码（仅以发放表 COMPLETED 为门控）。
     *
     * @param supplyLines              type 27 供应行
     * @param summaryDispenseCompleted 同号发放行是否已全部 COMPLETED
     * @return supplyRequestId → 追溯码；未发药时为空 map
     */
    private Map<Long, String> loadTraceBySupplyRequestIdForDetail(
        List<SupplyRequestModel> supplyLines,
        boolean summaryDispenseCompleted) {
        Map<Long, String> traceBySupplyRequestId = new HashMap<>();
        // 待发核对不读 SupplyDelivery 追溯码，避免 Supply 态与发放表不一致时误展示
        if (!summaryDispenseCompleted) {
            return traceBySupplyRequestId;
        }
        List<Long> supplyRequestIds = new ArrayList<>();
        for (SupplyRequestModel line : supplyLines) {
            if (line != null && line.getId() != null) {
                supplyRequestIds.add(line.getId());
            }
        }
        if (supplyRequestIds.isEmpty()) {
            return traceBySupplyRequestId;
        }
        SupplyDeliveryRequestIdListQuery deliveryQuery = new SupplyDeliveryRequestIdListQuery();
        deliveryQuery.setRequestIdList(supplyRequestIds);
        List<SupplyDeliveryByRequestModel> deliveryRows =
            supplyDeliveryQueryService.selectByRequestIdList(deliveryQuery);
        for (SupplyDeliveryByRequestModel deliveryRow : deliveryRows) {
            if (deliveryRow == null || deliveryRow.getRequestId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(deliveryRow.getTraceNo())) {
                traceBySupplyRequestId.put(deliveryRow.getRequestId(), deliveryRow.getTraceNo().trim());
            }
        }
        return traceBySupplyRequestId;
    }

    /**
     * 从汇总供应行收集全部追溯码片段。
     *
     * @param supplyLines              type 27 供应行
     * @param traceBySupplyRequestId   供应行 id → 追溯码
     * @return 追溯码片段列表
     */
    private List<String> collectTraceCodesFromSupplyLines(
        List<SupplyRequestModel> supplyLines,
        Map<Long, String> traceBySupplyRequestId) {
        List<String> codes = new ArrayList<>();
        if (supplyLines == null || traceBySupplyRequestId == null) {
            return codes;
        }
        for (SupplyRequestModel supplyLine : supplyLines) {
            if (supplyLine == null || supplyLine.getId() == null) {
                continue;
            }
            codes.addAll(splitTraceCodes(traceBySupplyRequestId.get(supplyLine.getId())));
        }
        return codes;
    }

    /**
     * 按药品 id 选取代表性发放行（追溯门控快照用；不用于追溯码落库）。
     *
     * @param dispenses    汇总下发放行
     * @param medicationId 药品定义 id
     * @return 首条匹配行或 null
     */
    private MedicationDispense findRepresentativeDispenseForItemId(
        List<MedicationDispense> dispenses,
        Long medicationId) {
        if (dispenses == null || medicationId == null) {
            return null;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense != null && medicationId.equals(dispense.getMedicationId())) {
                return dispense;
            }
        }
        return null;
    }

    /**
     * 汇总合并行回填主档贴码快照与代表批号。
     *
     * <p>批号取同药品首个发放行；库房取单头 purposeLocationId。</p>
     *
     * @param mergedLines   合并 Supply 行
     * @param dispenseItems 底层发放行（供批号）
     * @param header        汇总单头（供库房）
     */
    private void enrichSummaryMergedLinesTraceUnitSnapshot(
        List<InpatientPharmacySummarySlipLineDto> mergedLines,
        List<InpatientPharmacySummaryDispenseItemDto> dispenseItems,
        InpatientPharmacySummarySlipHeaderDto header
    ) {
        if (mergedLines == null || mergedLines.isEmpty()) {
            return;
        }
        Long locationId = null;
        if (header != null) {
            locationId = header.getPurposeLocationId();
        }
        Map<Long, String> firstLotByMedicationId = new HashMap<>();
        if (dispenseItems != null) {
            for (InpatientPharmacySummaryDispenseItemDto item : dispenseItems) {
                if (item == null || item.getMedicationId() == null) {
                    continue;
                }
                if (StringUtils.isEmpty(item.getLotNumber())) {
                    continue;
                }
                // 同药品仅取首个批号作代表（待扫换算用）
                if (!firstLotByMedicationId.containsKey(item.getMedicationId())) {
                    firstLotByMedicationId.put(item.getMedicationId(), item.getLotNumber().trim());
                }
            }
        }
        List<DispenseTraceUnitSnapshotEnricher.Request> requests = new ArrayList<>();
        for (InpatientPharmacySummarySlipLineDto line : mergedLines) {
            if (line == null || line.getItemId() == null) {
                continue;
            }
            String lot = firstLotByMedicationId.get(line.getItemId());
            line.setRepresentativeLotNumber(lot);
            InpatientPharmacySummarySlipLineDto target = line;
            requests.add(new DispenseTraceUnitSnapshotEnricher.Request(
                locationId,
                line.getItemId(),
                lot,
                (code, ratio) -> {
                    target.setTraceUnitCode(code);
                    target.setTraceUnitRatio(ratio);
                },
                target::setLotHasTraceMaster
            ));
        }
        this.dispenseTraceUnitSnapshotEnricher.enrich(requests);
    }

    /**
     * 汇总核发写追溯时解析批号：供应行 → 主表首码 → 失败则抛业务异常。
     *
     * @param supplyLine  汇总供应行
     * @param traceNoRaw  逗号追溯串
     * @return 非空批号
     * @throws BusinessException 无法解析批号时
     */
    private String resolveSummaryTraceLotNumber(SupplyRequestModel supplyLine, String traceNoRaw) {
        if (supplyLine != null && StringUtils.isNotEmpty(supplyLine.getLotNumber())) {
            return supplyLine.getLotNumber().trim();
        }
        List<String> codes = splitTraceCodes(traceNoRaw);
        if (!codes.isEmpty()) {
            TraceNumberQueryModel master = traceNumberQueryService.getTraceNumberByTraceNo(codes.get(0));
            if (master != null && StringUtils.isNotEmpty(master.getLotNumber())) {
                return master.getLotNumber().trim();
            }
        }
        throw new BusinessException(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_204,
            "追溯码缺少批号，无法写入主子表");
    }

    /**
     * 拆分追溯码提交串（逗号分隔）；空串不产生条目。
     *
     * @param traceNo 原始串
     * @return 去空白后的码片段列表
     */
    private static List<String> splitTraceCodes(String traceNo) {
        List<String> codes = new ArrayList<>();
        if (StringUtils.isEmpty(traceNo)) {
            return codes;
        }
        for (String part : traceNo.split(GeneralSharedConstants.COMMA)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

    /**
     * 按发放行 lot+location 合并扣减库存（与住院逐行发药同族；禁止对 SupplyDelivery 扣库）。
     *
     * @param dispensersInSubmitOrder 待扣库发放行
     * @param operatorPractitionerId          操作用户 id
     * @param productByMedicationId   药品 id → 产品读模型
     */
    private void applyInventoryDeductionForDispensedLines(
        List<MedicationDispense> dispensersInSubmitOrder,
        Long operatorPractitionerId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId) {
        if (dispensersInSubmitOrder == null || dispensersInSubmitOrder.isEmpty()) {
            return;
        }
        String sep = String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR);
        Map<String, BigDecimal> mergedAllocateRequiredByKey = new LinkedHashMap<>();
        Map<String, BigDecimal> mergedLotDecreaseByLotKey = new LinkedHashMap<>();
        for (MedicationDispense dispense : dispensersInSubmitOrder) {
            if (dispense == null) {
                continue;
            }
            ProductDefinitionDisplayDto productRow = productByMedicationId.get(dispense.getMedicationId());
            if (productRow == null) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_203,
                    "库存不足，请联系库管");
            }
            BigDecimal businessQty = dispense.getQuantity();
            if (businessQty == null) {
                businessQty = dispense.getDispenseQuantity();
            }
            BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
                ProductDefinitionDisplaySupport.toUnitConversionSource(productRow), dispense.getUnitCode(), businessQty);
            if (StringUtils.isNotEmpty(dispense.getLotNumber())) {
                String lotKey = dispense.getMedicationId() + sep + dispense.getLocationId() + sep + dispense.getLotNumber().trim();
                BigDecimal existedLot = mergedLotDecreaseByLotKey.get(lotKey);
                if (existedLot == null) {
                    mergedLotDecreaseByLotKey.put(lotKey, splitQty);
                } else {
                    mergedLotDecreaseByLotKey.put(lotKey, existedLot.add(splitQty));
                }
            } else {
                String allocateKey = TableNameConstants.MED_MEDICATION_DEFINITION + sep
                    + dispense.getMedicationId() + sep + dispense.getLocationId();
                BigDecimal existedAllocate = mergedAllocateRequiredByKey.get(allocateKey);
                if (existedAllocate == null) {
                    mergedAllocateRequiredByKey.put(allocateKey, splitQty);
                } else {
                    mergedAllocateRequiredByKey.put(allocateKey, existedAllocate.add(splitQty));
                }
            }
        }
        Map<Long, BigDecimal> inventoryIdToMergedDecrease = new HashMap<>();
        for (Map.Entry<String, BigDecimal> lotEntry : mergedLotDecreaseByLotKey.entrySet()) {
            String[] lotParts = lotEntry.getKey().split(Pattern.quote(sep), -1);
            if (lotParts.length != 3) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_203,
                    "库存不足，请联系库管");
            }
            Long medicationId = Long.parseLong(lotParts[0]);
            Long locationId = Long.parseLong(lotParts[1]);
            String lotNumber = lotParts[2];
            Long inventoryItemId = inventoryItemQueryService.requireInventoryItemIdForMedicationDispenseBatch(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationId, locationId, lotNumber);
            BigDecimal existedInv = inventoryIdToMergedDecrease.get(inventoryItemId);
            if (existedInv == null) {
                inventoryIdToMergedDecrease.put(inventoryItemId, lotEntry.getValue());
            } else {
                inventoryIdToMergedDecrease.put(inventoryItemId, existedInv.add(lotEntry.getValue()));
            }
        }
        inventoryItemDomainService.decreaseQuantitiesMerged(operatorPractitionerId, inventoryIdToMergedDecrease);
        for (Map.Entry<String, BigDecimal> allocateEntry : mergedAllocateRequiredByKey.entrySet()) {
            String[] allocateParts = allocateEntry.getKey().split(Pattern.quote(sep), -1);
            if (allocateParts.length != 3) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_203,
                    "库存不足，请联系库管");
            }
            AllocateAndDecreaseCommand allocateCommand = new AllocateAndDecreaseCommand();
            allocateCommand.setOperatorId(operatorPractitionerId);
            AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand allocateItem =
                new AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand();
            allocateItem.setItemTable(allocateParts[0]);
            allocateItem.setItemId(Long.parseLong(allocateParts[1]));
            allocateItem.setLocationId(Long.parseLong(allocateParts[2]));
            allocateItem.setRequiredQty(allocateEntry.getValue());
            allocateCommand.setItem(allocateItem);
            inventoryItemDomainService.allocateAndDecrease(allocateCommand);
        }
    }
}
