/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.pharmacy.summarydispense.service;

import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
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
import com.openhis.health.module.querysupport.encounter.EncounterContextFacade;
import com.openhis.health.module.querysupport.pharmacy.InpatientMedicationDispenseChargePricingLoader;
import com.openhis.health.module.querysupport.pharmacy.MedicationSummarySlipQuerySupport;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionDisplaySupport;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSummaryItemQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
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
import com.openhis.health.integration.support.insurance.inventory.DispenseTraceGateCommandSupport;
import com.openhis.health.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.integration.insurance.inventory.DispenseTraceGateExtension;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterDispenseCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
 *   <li><b>IF-IPS-01</b>：待核发（APPROVAL）/ 已核发（AGREE）type 27 汇总单头分页</li>
 *   <li><b>IF-IPS-02</b>：合并 Supply 行 + 底层发放行明细（含批号）</li>
 *   <li><b>IF-IPS-03</b>：批量核发（扣库 → complete → Delivery → AGREE → 追溯）</li>
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

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final DispenseTraceGateExtension dispenseTraceGateExtension;

    /** 写路径操作人门控（B.6.1）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 写路径发药药师/applicant 解析。 */
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
    /** 库存写侧（合并扣减/分配扣减）。 */
    private final InventoryItemDomainService inventoryItemDomainService;
    /** 追溯读侧（出库冲突校验）。 */
    private final TraceNumberRecordQueryService traceNumberRecordQueryService;
    /** 追溯写侧（核发后出库记录）。 */
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    /** 药品目录批量读侧。 */
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    /** 医务人员查询（调配药师校验）。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 库位名称展示。 */
    private final LocationQueryService locationQueryService;
    /** 患者姓名批量展示。 */
    private final PatientQueryService patientQueryService;
    /** 住院就诊上下文（床号展示）。 */
    private final EncounterContextFacade encounterContextFacade;
    /** 发放 ↔ 待结算账单只读协作（B.6.1.3）。 */
    private final InpatientMedicationDispenseChargePricingLoader inpatientMedicationDispenseChargePricingLoader;
    /** 汇总单头 SupplyRequest + SupplyDelivery 读编排（B.6.1.3）。 */
    private final MedicationSummarySlipQuerySupport medicationSummarySlipQuerySupport;
    /** Request/Query/Command/Response 映射。 */
    private final InpatientPharmacySummaryDispenseMapper inpatientPharmacySummaryDispenseMapper;
    /** 详情合并行/发放行装配。 */
    private final InpatientPharmacySummaryDispenseAssembler inpatientPharmacySummaryDispenseAssembler;

    /**
     * IF-IPS-01：分页查询待核发汇总单头。
     *
     * @param request 药房、时段、关键字与分页
     * @return 待核发单头分页
     * @throws BusinessException {@code EX-IPS-101} 未登录或无权；{@code EX-IPS-102} 查询失败
     */
    public PagePendingMedicationSummarySlipsResponse pagePendingMedicationSummarySlips(
        PagePendingMedicationSummarySlipsRequest request) {
        try {
            // Gate ①：分页参数必填
            pageWindowGate.requirePositivePageWindow(
                request,
                InpatientPharmacySummaryDispenseConstants.EX_IPS_102,
                "加载失败，请稍后重试");
            // Gate ②：解析当前用户可操作药房（与护士站 IF-IP-SUM-02 同族门控）
            List<Long> purposeLocationIds = this.resolvePharmacyPurposeLocationIds(request);
            // 严格模式且无岗位可操作药房：拒绝（非 strict 且 empty 时不拼 purpose 谓词，与护士站汇总 Tab 一致）
            if (purposeLocationIds.isEmpty() && managedLocationIdsResolver.isLocationScopeEnforcementActive()) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
                    "未登录或无权操作该药房");
            }
            // Pre ③：指定药房须在管辖集合内
            this.assertPurposeLocationInManagedScope(request, purposeLocationIds);
            // Pre ④：装配 Query（固定 type 27 + APPROVAL）
            SupplyRequestDocumentListQuery listQuery =
                inpatientPharmacySummaryDispenseMapper.toSupplyRequestDocumentListQuery(request, purposeLocationIds);
            // Pre ⑤：域分页查询待核发单头
            Page<MedicationSummarySlipQueryModel> page =
                medicationSummarySlipQuerySupport.pageMedicationSummarySlips(listQuery);
            // Asm ⑥：单头 DTO + 展示 enrichment
            List<InpatientPharmacySummarySlipHeaderDto> rows =
                inpatientPharmacySummaryDispenseMapper.toSummarySlipHeaderDtos(page);
            // Asm ⑦：分页响应
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
            // Gate ①：summaryNo 非空
            if (request == null || StringUtils.isBlank(request.getSummaryNo())) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                    "汇总单不存在或已失效，请刷新");
            }
            String summaryNo = request.getSummaryNo().trim();
            // Pre ②：强加载 type 27 Supply 行并解析可读审批态（待发 APPROVAL / 已核发 AGREE）
            List<SupplyRequestModel> supplyLines = this.loadSummarySupplyLinesOrThrow(summaryNo);
            String supplyStatus = this.requireReadableSupplyStatus(
                supplyLines,
                InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                "汇总单不存在或已失效，请刷新");
            // Pre ③：合并 Supply 明细行
            List<MedicationSummarySlipLineQueryModel> mergedDomainLines =
                supplyRequestQueryService.listMedicationSummarySlipLinesByBusNo(summaryNo);
            if (mergedDomainLines.isEmpty()) {
                throw new BusinessException(
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                    "汇总单不存在或已失效，请刷新");
            }
            // Pre ③b：跨聚合只读累加单价/总价（BR-IP-SUM-009）
            inpatientMedicationDispenseChargePricingLoader.enrichSummarySlipLineChargePricing(
                summaryNo, mergedDomainLines);
            // Pre ④：同号发放行明细
            List<MedicationDispenseSummaryItemQueryModel> dispenseRows =
                medicationDispenseQueryService.listMedicationDispensesBySummaryNo(summaryNo);
            // Pre ⑤：发放行态须与 Supply 审批态一致（待发 SUMMARIZED / 已核发 COMPLETED）
            this.assertDispenseRowsConsistentWithSupplyStatus(
                dispenseRows,
                summaryNo,
                supplyStatus,
                InpatientPharmacySummaryDispenseConstants.EX_IPS_103,
                "汇总单不存在或已失效，请刷新");
            // Pre ⑥：药品目录批量加载
            Map<String, ProductDefinitionDisplayDto> productByKey =
                productDefinitionBatchLoader.loadAsMap(this.collectProductLookups(mergedDomainLines, dispenseRows));
            // Pre ⑦：患者/就诊/库位展示批量索引
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
            // Pre ⑧：单头（与 IF-IPS-01 同构）
            InpatientPharmacySummarySlipHeaderDto header = this.loadSlipHeaderBySummaryNo(summaryNo);
            // Asm ⑨：合并行 DTO（禁止 lotNumber）
            List<InpatientPharmacySummarySlipLineDto> mergedLines =
                inpatientPharmacySummaryDispenseAssembler.buildMergedLineDtos(
                    mergedDomainLines,
                    productByKey,
                    header != null ? header.getPurposeLocationName() : null);
            // Asm ⑩：发放行 DTO（含批号/追溯）
            List<InpatientPharmacySummaryDispenseItemDto> dispenseItems =
                inpatientPharmacySummaryDispenseAssembler.buildDispenseItemDtos(
                    dispenseRows, productByKey, patientNameById, bedDisplayByEncounterId, locationNameById);
            // Asm ⑪：详情响应
            return inpatientPharmacySummaryDispenseMapper.toGetMedicationSummaryDispenseDetailResponse(
                summaryNo, header, mergedLines, dispenseItems);
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
        // Gate ①：批上限与必填参数
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
        // Gate ②：操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        // Gate ③：发药药师
        Long dispenserPractitionerId = currentPractitionerResolver.requirePractitionerId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        // Gate ④：调配药师存在性
        Practitioner preparer = practitionerQueryService.findById(request.getPreparerPractitionerId());
        if (preparer == null) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_204,
                "追溯码冲突或请选择有效调配药师");
        }
        // Gate ⑤：调配药师不得与发药药师同人（BR-IPS-005）
        if (Objects.equals(request.getPreparerPractitionerId(), dispenserPractitionerId)) {
            throw new BusinessException(
                InpatientPharmacySummaryDispenseConstants.EX_IPS_204,
                "追溯码冲突或请选择有效调配药师");
        }
        List<String> succeededSummaryNos = new ArrayList<>();
        List<MedicationDispense> succeededDispenses = new ArrayList<>();
        try {
            for (String summaryNo : summaryNos) {
                // Pre 6a：强加载 Supply 行并断言 APPROVAL
                List<SupplyRequestModel> supplyLines = this.loadSummarySupplyLinesOrThrow(summaryNo);
                this.assertAllSupplyLinesApproval(
                    supplyLines,
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_201,
                    "汇总单已核发或状态不允许，请刷新");
                // Pre 6c：强加载发放行实体
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
                // Pre 6d：合并请求追溯码与实体持久化追溯码
                this.mergeTraceLinesIntoDispenses(dispenses, request.getTraceLines());
                if (dispenseTraceGateExtension.isEnabled()) {
                    for (MedicationDispense dispense : dispenses) {
                        List<String> lineTraceCodes = splitTraceCodesFromDispense(dispense);
                        dispenseTraceGateExtension.assertTraceAllowedForPrepare(
                            DispenseTraceGateCommandSupport.from(dispense), lineTraceCodes);
                        dispenseTraceGateExtension.assertTraceAllowedForDispense(
                            DispenseTraceGateCommandSupport.from(dispense));
                    }
                }
                List<String> allTraceCodes = this.collectTraceCodesFromDispenses(dispenses);
                for (MedicationDispense dispense : dispenses) {
                    if (dispense == null) {
                        continue;
                    }
                    List<String> lineTraceCodes = splitTraceCodesFromDispense(dispense);
                    if (!lineTraceCodes.isEmpty()) {
                        traceNumberRecordQueryService.assertCodesInNetStockForDispenseLine(
                            TableNameConstants.MED_MEDICATION_DEFINITION,
                            dispense.getMedicationId(),
                            dispense.getLotNumber(),
                            dispense.getLocationId(),
                            lineTraceCodes);
                    }
                }
                traceNumberRecordQueryService.assertNoDuplicateTraceCodes(allTraceCodes);
                // Pre 6e：追溯码未出库冲突校验
                if (!allTraceCodes.isEmpty()) {
                    traceNumberRecordQueryService.assertCodesNotAlreadyOutbound(allTraceCodes);
                }
                // Pre 6f：药品目录（单位换算）
                Map<String, ProductDefinitionDisplayDto> productByKey =
                    productDefinitionBatchLoader.loadAsMap(this.collectProductLookupsFromDispenses(dispenses));
                // Pre 6g：逐行库存充足预校验
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
                    inventoryItemQueryService.assertSufficientForMedicationDispense(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        dispense.getMedicationId(),
                        dispense.getLocationId(),
                        dispense.getLotNumber(),
                        requiredQuantity,
                        dispense.getUnitCode(),
                        ProductDefinitionDisplaySupport.toUnitConversionSource(productRow));
                }
                // Cmd 6h：按 lot+location 合并扣库（仅 MedicationDispense 行）
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
                // Cmd 6i：发放行 SUMMARIZED → COMPLETED
                medicationDispenseDomainService.completeSummaryDispense(
                    inpatientPharmacySummaryDispenseMapper.toCompleteSummaryDispenseCommand(
                        summaryNo, operatorId, dispenserPractitionerId));
                // Cmd 6j：创建 SupplyDelivery（写 receiverId）
                supplyDeliveryDomainService.createSummarySupplyDelivery(
                    inpatientPharmacySummaryDispenseMapper.toCreateSummarySupplyDeliveryCommand(
                        request.getReceiverId(), operatorId, supplyLines));
                // Pre 6k：查新建 deliveryIds
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
                // Cmd 6l：Delivery PREPARATION → COMPLETED
                if (!deliveryIds.isEmpty()) {
                    supplyDeliveryDomainService.updateCompletedStatusBatch(
                        inpatientPharmacySummaryDispenseMapper.toUpdateCompletedStatusBatchCommand(
                            deliveryIds, dispenserPractitionerId, operatorId, completedTime));
                }
                // Cmd 6m：Supply APPROVAL → AGREE
                supplyRequestDomainService.agreeRequest(
                    inpatientPharmacySummaryDispenseMapper.toAgreeRequestCommand(
                        summaryNo, dispenserPractitionerId, operatorId));
                // Cmd 6n：追溯出库
                if (!allTraceCodes.isEmpty()) {
                    traceNumberRecordDomainService.appendTraceRecords(
                        inpatientPharmacySummaryDispenseMapper.toAppendTraceRecordsCommand(
                            dispenses, summaryNo, operatorId));
                }
                succeededSummaryNos.add(summaryNo);
                succeededDispenses.addAll(dispenses);
            }
            DispenseSummaryMedicationsResponse response =
                inpatientPharmacySummaryDispenseMapper.toDispenseSummaryMedicationsResponse(succeededSummaryNos);
            tryUploadMedicareInventoryAfterSummaryDispense(request, succeededDispenses, response);
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

    private void tryUploadMedicareInventoryAfterSummaryDispense(
        DispenseSummaryMedicationsRequest request,
        List<MedicationDispense> dispenses,
        DispenseSummaryMedicationsResponse response
    ) {
        if (request == null) {
            return;
        }
        if (dispenses == null || dispenses.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> dispenseIdsByEncounter = new LinkedHashMap<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getId() == null || dispense.getEncounterId() == null) {
                continue;
            }
            dispenseIdsByEncounter
                .computeIfAbsent(dispense.getEncounterId(), key -> new ArrayList<>())
                .add(dispense.getId());
        }
        if (dispenseIdsByEncounter.isEmpty()) {
            return;
        }
        if (!inventoryUploadExtension.isEnabled()) {
            return;
        }
        List<String> uploadWarnings = new ArrayList<>();
        for (Map.Entry<Long, List<Long>> entry : dispenseIdsByEncounter.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            try {
                InventoryUploadAfterDispenseCommand uploadCommand = new InventoryUploadAfterDispenseCommand()
                    .setEncounterId(entry.getKey())
                    .setDispenseIds(entry.getValue())
                    .setContractBusNo(request.getContractBusNo());
                inventoryUploadExtension.uploadAfterDispense(uploadCommand);
            } catch (RuntimeException ex) {
                log.warn("汇总发药医保进销存3505上传失败 encounterId={}", entry.getKey(), ex);
                uploadWarnings.add(ex.getMessage());
            }
        }
        if (!uploadWarnings.isEmpty()) {
            String base;
            if (response.getMessage() != null) {
                base = response.getMessage().trim();
            } else {
                base = "核对发药成功";
            }
            response.setMessage(base + "；" + ErrorCodeConstants.EX_OPP_207_MEDICARE_PARTIAL_FAILURE
                + "：" + String.join("；", uploadWarnings));
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
        // Gate ①：批上限与非空
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
        // Gate ②：操作人与审批人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        Long approverId = currentPractitionerResolver.requirePractitionerId(
            InpatientPharmacySummaryDispenseConstants.EX_IPS_101,
            "未登录或无权操作该药房");
        List<String> rejectedSummaryNos = new ArrayList<>();
        try {
            for (String summaryNo : summaryNos) {
                // Pre 3a：强加载 Supply 行
                List<SupplyRequestModel> supplyLines = this.loadSummarySupplyLinesOrThrow(summaryNo);
                // Pre 3b：须全 APPROVAL
                this.assertAllSupplyLinesApproval(
                    supplyLines,
                    InpatientPharmacySummaryDispenseConstants.EX_IPS_202,
                    "当前状态无法驳回，请刷新");
                // Cmd 3c：Supply APPROVAL → REJECT
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
                // Cmd 3d：发放行 SUMMARIZED → PREPARATION，清 summary_no
                medicationDispenseDomainService.cancelMedicationDispenseSummary(
                    inpatientPharmacySummaryDispenseMapper.toCancelMedicationDispenseSummaryCommand(
                        summaryNo, operatorId));
                rejectedSummaryNos.add(summaryNo);
            }
            // Asm ④：驳回成功响应
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
     * 解析汇总单可读审批态：同号 Supply 行须一致，且为 APPROVAL（待发）或 AGREE（已核发）。
     *
     * @param supplyLines 汇总 Supply 行
     * @param errorCode   失败业务码
     * @param message     用户提示
     * @return 统一审批态 code
     */
    private String requireReadableSupplyStatus(
        List<SupplyRequestModel> supplyLines,
        String errorCode,
        String message) {
        String approvalCode = SupplyStatus.APPROVAL.getCode();
        String agreeCode = SupplyStatus.AGREE.getCode();
        String uniformStatus = null;
        for (SupplyRequestModel line : supplyLines) {
            if (line == null) {
                continue;
            }
            String lineStatus = line.getStatusEnum();
            if (uniformStatus == null) {
                uniformStatus = lineStatus;
            } else if (!uniformStatus.equals(lineStatus)) {
                throw new BusinessException(errorCode, message);
            }
        }
        if (uniformStatus == null) {
            throw new BusinessException(errorCode, message);
        }
        if (!approvalCode.equals(uniformStatus) && !agreeCode.equals(uniformStatus)) {
            throw new BusinessException(errorCode, message);
        }
        return uniformStatus;
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
            if (!approvalCode.equals(line.getStatusEnum())) {
                throw new BusinessException(errorCode, message);
            }
        }
    }

    /**
     * 读路径断言：发放行 summaryNo 一致，且态与 Supply 审批态匹配（APPROVAL→SUMMARIZED，AGREE→COMPLETED）。
     *
     * @param dispenseRows  发放行 QueryModel 列表
     * @param summaryNo     汇总单号
     * @param supplyStatus  统一 Supply 审批态
     * @param errorCode     失败业务码
     * @param message       用户提示
     */
    private void assertDispenseRowsConsistentWithSupplyStatus(
        List<MedicationDispenseSummaryItemQueryModel> dispenseRows,
        String summaryNo,
        String supplyStatus,
        String errorCode,
        String message) {
        if (dispenseRows == null || dispenseRows.isEmpty()) {
            throw new BusinessException(errorCode, message);
        }
        // 发放行 statusEnum 为 DispenseStatus 枚举；须与 Supply 审批态成对匹配，禁止 String 与枚举混比
        DispenseStatus expectedDispenseStatus;
        if (SupplyStatus.APPROVAL.getCode().equals(supplyStatus)) {
            expectedDispenseStatus = DispenseStatus.SUMMARIZED;
        } else if (SupplyStatus.AGREE.getCode().equals(supplyStatus)) {
            expectedDispenseStatus = DispenseStatus.COMPLETED;
        } else {
            throw new BusinessException(errorCode, message);
        }
        for (MedicationDispenseSummaryItemQueryModel row : dispenseRows) {
            if (row == null) {
                continue;
            }
            if (!expectedDispenseStatus.equals(row.getStatusEnum())) {
                throw new BusinessException(errorCode, message);
            }
            if (summaryNo != null && row.getSummaryNo() != null && !summaryNo.equals(row.getSummaryNo())) {
                throw new BusinessException(errorCode, message);
            }
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
        Page<MedicationSummarySlipQueryModel> page = medicationSummarySlipQuerySupport.pageMedicationSummarySlips(listQuery);
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
     * 批量加载就诊 id → 床号展示（经 EncounterContextFacade）。
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
            encounterContextFacade.listInpatientEncounterContexts(query);
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
            if (StringUtils.isBlank(summaryNo)) {
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
     * 将请求追溯码覆盖合并到发放实体（内存，供校验与出库）。
     *
     * @param dispenses  发放实体列表
     * @param traceLines 可选追溯码提交行
     */
    private void mergeTraceLinesIntoDispenses(
        List<MedicationDispense> dispenses,
        List<SummaryDispenseTraceLineSubmitDto> traceLines) {
        if (dispenses == null || dispenses.isEmpty()) {
            return;
        }
        Map<Long, String> traceOverrideByDispenseId = new HashMap<>();
        if (traceLines != null) {
            for (SummaryDispenseTraceLineSubmitDto traceLine : traceLines) {
                if (traceLine == null || traceLine.getDispenseId() == null) {
                    continue;
                }
                if (StringUtils.isNotBlank(traceLine.getTraceNo())) {
                    traceOverrideByDispenseId.put(traceLine.getDispenseId(), traceLine.getTraceNo().trim());
                }
            }
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            String overrideTrace = traceOverrideByDispenseId.get(dispense.getId());
            if (overrideTrace != null) {
                dispense.setTraceNo(overrideTrace);
            }
        }
    }

    /**
     * 从单条发放实体追溯串拆分为码列表。
     */
    private static List<String> splitTraceCodesFromDispense(MedicationDispense dispense) {
        List<String> codes = new ArrayList<>();
        if (dispense == null || StringUtils.isBlank(dispense.getTraceNo())) {
            return codes;
        }
        for (String part : dispense.getTraceNo().split(GeneralSharedConstants.COMMA)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

    /**
     * 从发放实体追溯串拆分为码列表（逗号分隔）。
     *
     * @param dispenses 发放实体列表
     * @return 追溯码片段列表
     */
    private List<String> collectTraceCodesFromDispenses(List<MedicationDispense> dispenses) {
        List<String> codes = new ArrayList<>();
        if (dispenses == null) {
            return codes;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || StringUtils.isBlank(dispense.getTraceNo())) {
                continue;
            }
            for (String part : dispense.getTraceNo().split(GeneralSharedConstants.COMMA)) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    codes.add(trimmed);
                }
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
            if (StringUtils.isNotBlank(dispense.getLotNumber())) {
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
