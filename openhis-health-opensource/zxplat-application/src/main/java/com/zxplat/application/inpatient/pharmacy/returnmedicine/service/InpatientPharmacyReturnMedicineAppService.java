/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.service;

import com.zxplat.application.shared.option.OptionDtoAssembler;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.financial.EncounterContractDisplayAssembler;
import com.zxplat.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.assembler.InpatientReturnMedicinePatientQueueAssembler;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnPatientRowDto;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.mapper.InpatientPharmacyReturnMedicineMapper;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.request.ConfirmInpatientMedicineReturnRequest;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.request.GetInpatientReturnMedicineDetailRequest;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.request.InpatientReturnMedicineItemRequest;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.request.LoadInpatientReturnMedicineInitRequest;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.request.PageInpatientReturnPatientRequest;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.response.ConfirmInpatientMedicineReturnResponse;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.response.InpatientReturnMedicineDetailResponse;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.response.LoadInpatientReturnMedicineInitResponse;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.response.PageInpatientReturnPatientResponse;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.support.InpatientReturnMedicineDetailLoader;
import com.zxplat.application.shared.encounter.EncounterContextReader;
import com.zxplat.application.inpatient.pharmacy.returnmedicine.support.PharmacyReturnEncounterQueueLoader;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.application.shared.tracenumber.TraceNumberDispenseTraceOrchestrator;
import com.zxplat.domain.medication.medicationdispense.command.RefundDispensedMedicationCommand;
import com.zxplat.domain.medication.medicationdispense.entity.MedicationDispense;
import com.zxplat.domain.medication.medicationdispense.query.InpatientPharmacyReturnEncounterPageQuery;
import com.zxplat.domain.medication.medicationdispense.query.ReturnMedicineDetailQuery;
import com.zxplat.domain.medication.medicationdispense.queries.model.ReturnMedicineDetailReadRow;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.zxplat.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.InpatientPharmacyReturnQueueTab;
import com.zxplat.domain.share.constants.MedicationCommonConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.zxplat.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 住院药房退药工作站编排：初始化、在院患者队列、明细与确认退药（AS-IP-PHA-003）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>读侧：{@link PharmacyReturnEncounterQueueLoader} 预解析在院就诊；{@link EncounterContextReader} 住院上下文</li>
 *   <li>写侧：{@link MedicationDispenseDomainService#refundDispensedMedication} 推进发放态；库存/追溯补偿</li>
 *   <li>医保 3506：住院退药当时不上报（与发药不报 3505 对称；在院退药计入清床/无床直结净销售合并）</li>
 *   <li>不写 ChargeItem、不完成负向药嘱、不提供拒绝退药</li>
 * </ul>
 *
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/inpatientPharmacyReturnMedicine/{methodName}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InpatientPharmacyReturnMedicineAppService {

    /** 写路径操作人门控（B.6.1.4）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 退药审计医务人员解析。 */
    /** 可操作药房/库房范围解析。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    /** 会话科室解析，供病区候选加载。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 病区候选查询。 */
    private final LocationQueryService locationQueryService;
    /** 住院就诊上下文门面。 */
    private final EncounterContextReader EncounterContextReader;
    /** 药房患者队列 Loader。 */
    private final PharmacyReturnEncounterQueueLoader pharmacyReturnEncounterQueueLoader;
    /** 药品发放读服务。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    /** 药品发放写服务。 */
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    /** 库存写服务。 */
    private final InventoryItemDomainService inventoryItemDomainService;
    /** 库存读服务（批号定位）。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 追溯码写服务。 */
    private final TraceNumberDispenseTraceOrchestrator traceNumberDispenseTraceOrchestrator;
    /** 药品定义批量加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 住院退药明细 Loader。 */
    private final InpatientReturnMedicineDetailLoader inpatientReturnMedicineDetailLoader;
    /** 住院退药队列行装配。 */
    private final InpatientReturnMedicinePatientQueueAssembler inpatientReturnMedicinePatientQueueAssembler;
    /** 请求/响应映射。 */
    private final InpatientPharmacyReturnMedicineMapper returnMedicineMapper;
    /** 就诊费用性质（优先合同名）批量回填队列卡片，与门诊发药同口径。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;

    /**
     * 加载住院退药页初始化元数据（IF-IPR-01）。
     *
     * @param request 空壳请求
     * @return 药房/病区/退药状态下拉
     * @throws BusinessException {@code EX-IPR-101} 元数据加载失败或未登录
     */
    public LoadInpatientReturnMedicineInitResponse loadInpatientReturnMedicineInit(
        LoadInpatientReturnMedicineInitRequest request) {
        try {
            // Gate：须登录后才能加载退药页元数据
            currentPractitionerResolver.requireOperatorId(
                ErrorCodeConstants.EX_IPR_101_LOAD_FAILED,
                "数据加载失败，请稍后重试");
            if (request == null) {
                request = new LoadInpatientReturnMedicineInitRequest();
            }
            // Pre：管辖内启用药房下拉（adm_location PHARMACY）
            List<Location> pharmacies = managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty();
            List<OptionDto> pharmacyOptions = OptionDtoAssembler.toOptions(pharmacies, Location::getId, Location::getName, 0L);
            List<OptionDto> wardOptions = new ArrayList<>();
            Long organizationUnitId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
            if (organizationUnitId != null) {
                // Pre：当前机构下病区候选
                List<Location> wards = locationQueryService.listWardsByOrganization(organizationUnitId);
                wardOptions = OptionDtoAssembler.toOptions(wards, Location::getId, Location::getName, 0L);
            }
            List<OptionDto> refundStatusOptions = returnMedicineMapper.mapToRefundStatusOptions();
            return returnMedicineMapper.toLoadInitResponse(pharmacyOptions, wardOptions, refundStatusOptions);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_101_LOAD_FAILED, "数据加载失败，请稍后重试");
        }
    }

    /**
     * 分页查询住院退药患者队列（IF-IPR-02）。
     *
     * @param request 分页、病区、关键字与队列 Tab
     * @return 在院且存在退药发放事实的就诊分页
     * @throws BusinessException {@code EX-IPR-102} 分页非法或查询失败
     */
    public PageInpatientReturnPatientResponse pageInpatientReturnPatientQueue(PageInpatientReturnPatientRequest request) {
        try {
            if (request == null) {
                request = new PageInpatientReturnPatientRequest();
            }
            // Gate：分页参数与登录校验
            currentPractitionerResolver.requireOperatorId(
                ErrorCodeConstants.EX_IPR_102_QUERY_FAILED,
                "列表加载失败，请稍后重试");
            validatePageInpatientReturnPatientRequest(request);
            // Pre：管辖库房非空时写入 locationIds 谓词
            InpatientPharmacyReturnEncounterPageQuery query = returnMedicineMapper.toPageQuery(request);
            List<Long> pharmacyLocationIds = managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
            if (!pharmacyLocationIds.isEmpty()) {
                query.setLocationIds(pharmacyLocationIds);
            }
            // Pre：在院就诊 id 分页（含病区/关键字预解析）
            Page<Long> encounterIdPage = pharmacyReturnEncounterQueueLoader.pageEncounterIdsForInpatientPharmacyReturn(query);
            List<Long> encounterIds = encounterIdPage.getContent();
            // Pre：批量回补住院上下文展示字段
            List<InpatientEncounterContextQueryModel> contexts =
                EncounterContextReader.listInpatientContextsByEncounterIdsOrdered(encounterIds);
            Map<Long, String> refundCodeByEncounter =
                medicationDispenseQueryService.mapAggregatedInpatientRefundStatusCodeByEncounterIds(encounterIds);
            String queueTab = request.getQueueTab();
            if (StringUtils.isEmpty(queueTab)) {
                queueTab = InpatientPharmacyReturnQueueTab.PENDING;
            }
            // Pre：按 Tab 过滤待退/已退队列（内存过滤，BR-IPR-001）
            List<InpatientEncounterContextQueryModel> filteredContexts =
                inpatientReturnMedicinePatientQueueAssembler.buildRows(contexts, refundCodeByEncounter, queueTab);
            List<InpatientReturnPatientRowDto> content = new ArrayList<>();
            for (InpatientEncounterContextQueryModel ctx : filteredContexts) {
                String refundCode = refundCodeByEncounter.get(ctx.getEncounterId());
                content.add(returnMedicineMapper.toReturnPatientRow(ctx, refundCode));
            }
            // Asm：回填费用性质后封装分页（与门诊发药/医生站卡片同口径）。
            this.applyQueueContractDisplayLabels(content);
            PageInpatientReturnPatientResponse response = new PageInpatientReturnPatientResponse();
            return PagedResultBuilder.build(response, content, encounterIdPage);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_102_QUERY_FAILED, "列表加载失败，请稍后重试");
        }
    }

    /**
     * 住院退药队列行批量回填 {@code contractDisplay}（与门诊发药/医生站患者卡片费用性质同口径）。
     *
     * @param patientRows 本页已映射的队列 DTO；空或无就诊主键时直接返回
     */
    private void applyQueueContractDisplayLabels(List<InpatientReturnPatientRowDto> patientRows) {
        if (patientRows == null || patientRows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // Loop：收集本页就诊主键，供合同展示一次批量解析
        for (InpatientReturnPatientRowDto row : patientRows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        // 本页无有效就诊时跳过合同装配
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, String> contractDisplayByEncounterId =
            this.encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        // Loop：逐行写回费用性质展示名；无合同时留空，前端回落「自费」
        for (InpatientReturnPatientRowDto row : patientRows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            row.setContractDisplay(contractDisplayByEncounterId.get(row.getEncounterId()));
        }
    }

    /**
     * 查询指定在院就诊的住院退药明细（IF-IPR-03）。
     *
     * @param request 就诊与可选退药状态过滤
     * @return 逐发放行明细
     * @throws BusinessException {@code EX-IPR-103} 就诊无效或未登录
     */
    public InpatientReturnMedicineDetailResponse getInpatientReturnMedicineDetail(
        GetInpatientReturnMedicineDetailRequest request) {
        try {
            // Gate：须登录且 encounterId 必填
            currentPractitionerResolver.requireOperatorId(
                ErrorCodeConstants.EX_IPR_103_DETAIL_FAILED,
                "无法加载退药明细");
            if (request == null || request.getEncounterId() == null) {
                throw new BusinessException(ErrorCodeConstants.EX_IPR_103_DETAIL_FAILED, "无法加载退药明细");
            }
            // Pre：就诊门控，确保为有效住院上下文
            EncounterContextReader.mustGetInpatientContextByEncounterId(request.getEncounterId());
            ReturnMedicineDetailQuery detailQuery = returnMedicineMapper.toDetailQuery(request);
            List<Long> pharmacyLocationIds = managedLocationIdsResolver.listManagedLocationIdsOrEmpty();
            if (!pharmacyLocationIds.isEmpty()) {
                detailQuery.setLocationIds(pharmacyLocationIds);
            }
            List<ReturnMedicineDetailReadRow> rows =
                inpatientReturnMedicineDetailLoader.listReturnMedicineDetails(detailQuery);
            InpatientReturnMedicineDetailResponse response = new InpatientReturnMedicineDetailResponse();
            response.setItems(returnMedicineMapper.toDetailRows(rows));
            return response;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_103_DETAIL_FAILED, "无法加载退药明细");
        }
    }

    /**
     * 确认住院退药（IF-IPR-04）：推进发放态、回补库存、追溯入库；不写 ChargeItem。
     *
     * @param request 待退发放行列表与可选退药说明
     * @return 成功响应
     * @throws BusinessException {@code EX-IPR-201/202/203/301} 状态或库存不允许
     */
    public ConfirmInpatientMedicineReturnResponse confirmInpatientMedicineReturn(
        ConfirmInpatientMedicineReturnRequest request) {
        // Gate：退药项非空与登录
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_301_ITEMS_EMPTY, "请选择要退药的药品");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.EX_IPR_301_ITEMS_EMPTY,
            "请选择要退药的药品");
        Long practitionerId = currentPractitionerResolver.findPractitionerIdOrNull();
        if (practitionerId == null) {
            practitionerId = operatorId;
        }
        List<RefundDispensedMedicationCommand.Item> medRefundItems = new ArrayList<>();
        List<MedicationDispense> confirmedDispenses = new ArrayList<>();
        Map<Long, BigDecimal> inventoryIncreaseMerged = new HashMap<>();
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId = new HashMap<>();
        for (InpatientReturnMedicineItemRequest item : request.getItems()) {
            if (item == null || item.getDispenseId() == null) {
                continue;
            }
            // Pre：加载待退发放行并校验 PENDING_REFUND（幂等拒绝已 REFUNDED）
            MedicationDispense md = medicationDispenseQueryService.mustGetMedicationDispense(item.getDispenseId());
            assertPendingRefundDispense(md.getStatusEnum(), item.getDispenseId());
            RefundDispensedMedicationCommand.Item refundItem = new RefundDispensedMedicationCommand.Item();
            refundItem.setId(md.getId());
            refundItem.setRefundQuantity(resolveRefundQuantity(md));
            refundItem.setOperatorId(operatorId);
            refundItem.setPractitionerId(practitionerId);
            refundItem.setReasonText(request.getReason());
            medRefundItems.add(refundItem);
            confirmedDispenses.add(md);
            collectMedicationInventory(md, inventoryIncreaseMerged, productByMedicationId);
        }
        if (medRefundItems.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_301_ITEMS_EMPTY, "请选择要退药的药品");
        }
        RefundDispensedMedicationCommand cmd = new RefundDispensedMedicationCommand();
        cmd.setOperatorId(operatorId);
        cmd.setItems(medRefundItems);
        // Cmd：仅推进 REFUND 待退行 PENDING_REFUND→REFUNDED；原 COMPLETED 正向行保持不动（BR-IPR-007 / DM §6.1.4）
        medicationDispenseDomainService.refundDispensedMedication(cmd);
        traceNumberDispenseTraceOrchestrator.recordReturnInLines(
            confirmedDispenses, operatorId, productByMedicationId);
        if (!inventoryIncreaseMerged.isEmpty()) {
            IncreaseQuantityCommand inc = new IncreaseQuantityCommand();
            inc.setOperatorId(operatorId);
            List<IncreaseQuantityCommand.IncreaseQuantityItemCommand> incItems = new ArrayList<>();
            for (Map.Entry<Long, BigDecimal> entry : inventoryIncreaseMerged.entrySet()) {
                IncreaseQuantityCommand.IncreaseQuantityItemCommand incItem = new IncreaseQuantityCommand.IncreaseQuantityItemCommand();
                incItem.setId(entry.getKey());
                incItem.setIncreaseQty(entry.getValue());
                incItems.add(incItem);
            }
            inc.setItems(incItems);
            inventoryItemDomainService.increaseQuantities(inc);
        }
        ConfirmInpatientMedicineReturnResponse response = new ConfirmInpatientMedicineReturnResponse();
        response.setSuccess(Boolean.TRUE);
        response.setMessage(MedicationCommonConstants.INPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE);
        // Asm：住院退药不调 3506（无配对 3505；退量由清床/无床直结净销售合并相抵）
        return response;
    }

    /**
     * 校验住院退药队列分页请求（pageNo≥1，pageSize∈[1,100]）。
     *
     * @param request 分页请求
     * @throws BusinessException {@code EX-IPR-102} 分页非法
     */
    private void validatePageInpatientReturnPatientRequest(PageInpatientReturnPatientRequest request) {
        if (request.getPageNo() == null || request.getPageNo() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_102_QUERY_FAILED, "列表加载失败，请稍后重试");
        }
        if (request.getPageSize() == null) {
            request.setPageSize(10);
        }
        if (request.getPageSize() < 1) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_102_QUERY_FAILED, "列表加载失败，请稍后重试");
        }
        if (request.getPageSize() > 100) {
            request.setPageSize(100);
        }
    }

    /**
     * 待退发放行状态门禁：仅 PENDING_REFUND 可确认；已 REFUNDED 幂等拒绝。
     *
     * @param status     发放状态
     * @param dispenseId 发放行主键（错误提示用）
     * @throws BusinessException 状态不允许
     */
    private void assertPendingRefundDispense(DispenseStatus status, Long dispenseId) {
        if (status == null) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_201_STATUS_NOT_ALLOWED, "当前状态不允许退药，请刷新后重试");
        }
        if (DispenseStatus.REFUNDED.equals(status)) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_202_DUPLICATE_REFUND, "该药品已退药，请勿重复操作");
        }
        if (!DispenseStatus.PENDING_REFUND.equals(status)) {
            throw new BusinessException(
                ErrorCodeConstants.EX_IPR_201_STATUS_NOT_ALLOWED,
                "当前状态不允许退药，请刷新后重试");
        }
    }

    /**
     * 解析全量退药数量（绝对值，BR-IPR-005）。
     *
     * @param md 待退发放行
     * @return 退药数量
     */
    private BigDecimal resolveRefundQuantity(MedicationDispense md) {
        BigDecimal quantity = md.getDispenseQuantity();
        if (quantity == null) {
            quantity = md.getQuantity();
        }
        if (quantity == null) {
            return BigDecimal.ZERO;
        }
        return quantity.abs();
    }

    /**
     * 收集追溯入库项与库存回补增量；批号缺失时拒绝确认（EX-IPR-203）。
     *
     * @param md                      待退发放行
     * @param traceItems              追溯入库项输出
     * @param inventoryIncreaseMerged 库存项 id → 合并增量
     */
    private void collectMedicationInventory(
        MedicationDispense md,
        Map<Long, BigDecimal> inventoryIncreaseMerged,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        if (StringUtils.isEmpty(md.getLotNumber())) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_203_INVENTORY_MISSING, "库存信息异常，请联系管理员");
        }
        ProductDefinitionDisplayDto product = loadProductForMedication(md.getMedicationId(), productByMedicationId);
        if (product == null) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_203_INVENTORY_MISSING, "库存信息异常，请联系管理员");
        }
        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            ProductDefinitionDisplaySupport.toUnitConversionSource(product),
            md.getUnitCode(),
            resolveRefundQuantity(md));
        if (splitQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.EX_IPR_203_INVENTORY_MISSING, "库存信息异常，请联系管理员");
        }
        Long inventoryItemId = inventoryItemQueryService.requireInventoryItemIdForMedicationDispenseBatch(
            TableNameConstants.MED_MEDICATION_DEFINITION,
            md.getMedicationId(),
            md.getLocationId(),
            md.getLotNumber());
        BigDecimal existed = inventoryIncreaseMerged.get(inventoryItemId);
        if (existed == null) {
            inventoryIncreaseMerged.put(inventoryItemId, splitQty);
        } else {
            inventoryIncreaseMerged.put(inventoryItemId, existed.add(splitQty));
        }
    }

    /**
     * 按 medicationId 加载产品展示 DTO（带请求级 Map 缓存）。
     *
     * @param medicationId            药品定义 id
     * @param productByMedicationId   请求内缓存（mutate）
     * @return 产品展示；无法加载则 null
     */
    private ProductDefinitionDisplayDto loadProductForMedication(
        Long medicationId,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId
    ) {
        if (medicationId == null) {
            return null;
        }
        ProductDefinitionDisplayDto cached = productByMedicationId.get(medicationId);
        if (cached != null) {
            return cached;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(medicationId);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        ProductDefinitionDisplayDto product = productByKey.get(key);
        if (product != null) {
            productByMedicationId.put(medicationId, product);
        }
        return product;
    }
}
