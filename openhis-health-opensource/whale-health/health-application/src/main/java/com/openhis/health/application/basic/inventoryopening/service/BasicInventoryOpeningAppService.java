/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.service;

import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningDetailLineDto;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningImportErrorDto;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningLineDto;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningListRowDto;
import com.openhis.health.application.basic.inventoryopening.dto.InventoryOpeningProductRowDto;
import com.openhis.health.application.basic.inventoryopening.dto.LocationOpeningOptionDto;
import com.openhis.health.application.basic.inventoryopening.mapper.BasicInventoryOpeningMapper;
import com.openhis.health.application.basic.inventoryopening.request.*;
import com.openhis.health.application.basic.inventoryopening.response.*;
import com.openhis.health.application.basic.inventoryopening.support.InventoryOpeningBusinessLockSupport;
import com.openhis.health.application.basic.inventoryopening.support.InventoryOpeningExcelSupport;
import com.openhis.health.application.basic.inventoryopening.support.InventoryOpeningTraceGateSupport;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.support.ChargePricingTypeCodeResolver;
import com.openhis.health.application.inventory.warehouse.support.ProductDefinitionPurchasePageLoader;
import com.openhis.health.application.shared.inventoryopening.InventoryOpeningVoidOrchestrator;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.pricing.CatalogReferencePriceSyncSupport;
import com.openhis.health.application.shared.pricing.LotNumberPurchasePriceConsistencyGate;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.application.shared.tracenumber.RecordTraceFromSupplyLineSpec;
import com.openhis.health.application.shared.tracenumber.TraceNumberRecordOrchestrator;
import com.openhis.health.application.shared.tracenumber.TraceNumberSupplyAgreeTraceOrchestrator;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.chargeitemdefinition.command.DeactivateActivePriceLinesCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceNumberQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ProductDefinitionCatalogUnitBandConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyRequestWarehouseLineAmountSupport;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 基础数据 · 库存期初应用编排（AS-BAS-INV-001 / SDD v0.5 · IF-BAS-INV-OPN-01～16）。
 *
 * <p>三房 OPENING 单据 CRUD/审批、Excel、关闭、作废回滚；同意走 Delivery+库存+批号价+追溯真覆盖；不调医保、不走财务审批。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BasicInventoryOpeningAppService {

    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final CatalogReferencePriceSyncSupport catalogReferencePriceSyncSupport;
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;
    private final TraceNumberRecordOrchestrator traceNumberRecordOrchestrator;
    private final TraceNumberQueryService traceNumberQueryService;
    private final InventoryOpeningVoidOrchestrator inventoryOpeningVoidOrchestrator;
    private final InventoryOpeningBusinessLockSupport businessLockSupport;
    private final InventoryOpeningTraceGateSupport traceGateSupport;
    private final InventoryOpeningExcelSupport excelSupport;
    private final LotNumberPurchasePriceConsistencyGate lotNumberPurchasePriceConsistencyGate;
    private final BasicInventoryOpeningMapper openingMapper;
    private final SupplyRequestWarehouseLineAmountSupport warehouseLineAmountSupport;
    private final com.openhis.health.domain.custom.supplier.service.SupplierQueryService supplierQueryService;
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /**
     * 进入库存期初页初始化（IF-BAS-INV-OPN-01）。
     *
     * @param request 可空体
     * @return 三类库房（含关闭标志）、状态与经手人下拉
     */
    public LoadInventoryOpeningFormInitResponse loadInventoryOpeningFormInit(
        LoadInventoryOpeningFormInitRequest request) {
        // Gate：登录门禁
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        try {
            // Pre：装载三房启用位置与关闭态批量映射
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> pharmacies = locationQueryService.listAllActivePharmacyOnlyLocations();
            List<Location> deptPharms = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Long> allIds = new ArrayList<>();
            this.collectLocationIds(warehouses, allIds);
            this.collectLocationIds(pharmacies, allIds);
            this.collectLocationIds(deptPharms, allIds);
            Map<Long, Boolean> closedMap = locationQueryService.mapInventoryOpeningClosedByIds(allIds);
            List<LocationOpeningOptionDto> whOpts = openingMapper.toLocationOpeningOptions(warehouses, closedMap);
            List<LocationOpeningOptionDto> phOpts = openingMapper.toLocationOpeningOptions(pharmacies, closedMap);
            List<LocationOpeningOptionDto> dpOpts = openingMapper.toLocationOpeningOptions(deptPharms, closedMap);
            List<OptionDto> statusOpts = openingMapper.toOptionDtoListSupplyStatuses();
            Long orgId = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
                ErrorCodeConstants.BAS_INV_OPN_101, "机构上下文缺失");
            List<OptionDto> practitionerOpts = openingMapper.toOptionDtoListFromPractitioners(
                practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                    WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, orgId));
            List<OptionDto> supplierOpts = new ArrayList<>();
            List<com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel> suppliers =
                supplierQueryService.listSuppliersByTypeAndActive(
                    com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
            if (suppliers != null) {
                for (com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel s : suppliers) {
                    if (s == null || s.getId() == null) {
                        continue;
                    }
                    OptionDto opt = new OptionDto();
                    opt.setValue(String.valueOf(s.getId()));
                    String name = s.getName();
                    if (StringUtils.isEmpty(name)) {
                        name = String.valueOf(s.getId());
                    }
                    opt.setLabel(name);
                    supplierOpts.add(opt);
                }
            }
            // Asm：装配初始化响应
            return openingMapper.toFormInitResponse(
                whOpts, phOpts, dpOpts, statusOpts, practitionerOpts, supplierOpts);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("loadInventoryOpeningFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_101, "期初页主数据加载失败");
        }
    }

    /**
     * 期初单分页列表（IF-BAS-INV-OPN-02）。
     *
     * @param request 分页与筛选
     * @return 按 busNo 聚合行（含 currentEffective）
     */
    public PageInventoryOpeningsResponse pageInventoryOpenings(PageInventoryOpeningsRequest request) {
        // Gate：请求与分页窗口
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "请求不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.BAS_INV_OPN_002, "分页参数不能为空");
        // Pre：组装三类 OPENING 列表查询
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnums(SupplyRequestQueryService.openingTypeCodes());
        // locationId=0 与前端「全部库房」占位一致：不按 purpose 收窄（勿 equal 0 库）
        Long locationIdFilter = request.getLocationId();
        if (locationIdFilter != null && locationIdFilter.longValue() != 0L) {
            listQuery.setPurposeLocationId(locationIdFilter);
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        if (StringUtils.isNotEmpty(request.getBusNo())) {
            listQuery.setKeyword(request.getBusNo().trim());
        }
        PageQueryBinder.bind(request, listQuery);
        try {
            long mergedTotal = supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            Map<Long, String> effectiveByLoc = this.buildCurrentEffectiveMap(lines);
            List<InventoryOpeningListRowDto> records = openingMapper.toListRows(busNos, lines, effectiveByLoc);
            this.enrichListPurchaseTotals(records, lines);
            this.enrichListDisplayNames(records);
            // Asm：分页响应
            return openingMapper.toPageResponse(mergedTotal, records);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("pageInventoryOpenings failed", ex);
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_102, "期初单列表查询失败");
        }
    }

    /**
     * 期初单详情（IF-BAS-INV-OPN-03）。
     *
     * @param request 含 busNo
     * @return 头+明细（含 productCatalog / 单位展示，与进货详情同口径回显）
     */
    public GetInventoryOpeningDetailResponse getInventoryOpeningDetail(GetInventoryOpeningDetailRequest request) {
        // Gate：busNo 必填
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        // Pre：回读并断言 OPENING
        List<SupplyRequestModel> lines = this.loadOpeningLines(request.getBusNo().trim());
        Map<String, ProductDefinitionDisplayDto> products = this.loadProductsForLines(lines);
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        // Asm：详情装配 + 单位槽/厂家展示增强（对齐进货）
        GetInventoryOpeningDetailResponse res = openingMapper.toDetailResponse(lines, products);
        this.enrichOpeningDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichOpeningDetailCatalogFromPersistedLinePrice(res.getLines());
        SupplyRequestModel head = lines.get(0);
        if (head.getPurposeLocationId() != null) {
            Optional<Location> locOpt = locationQueryService.findOptionalById(head.getPurposeLocationId());
            if (locOpt.isPresent()) {
                res.setLocationName(locOpt.get().getName());
            }
        }
        return res;
    }

    /**
     * 新建期初单（IF-BAS-INV-OPN-04）；允许空草稿。
     *
     * @param request 库房、时点与可选明细
     * @return 新 busNo
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateInventoryOpeningResponse createInventoryOpening(CreateInventoryOpeningRequest request) {
        // Gate：locationId / occurrenceTime
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "locationId 不能为空");
        }
        if (request.getOccurrenceTime() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "occurrenceTime 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.BAS_INV_OPN_101, "当前用户未绑定医务人员档案");
        // Pre：关闭/封锁闸门
        businessLockSupport.assertWritableOrCloseAndReject(request.getLocationId(), operatorId);
        LocationTypePair typePair = this.resolveOpeningTypePair(request.getLocationId());
        Map<String, ProductDefinitionDisplayDto> products = this.loadProductsForWritableLines(request.getLines());
        // Pre：有行时校验唯一/效期/追溯/药库进价
        traceGateSupport.assertWritableLines(
            request.getLines(), typePair.openingTypeCode, products, false);
        // Gate：同表同品同批号不得与已有批号进价冲突
        this.assertOpeningLotPurchasePriceConsistency(request.getLines());
        // Cmd：createOpening
        String busNo = supplyRequestDomainService.createOpening(
            openingMapper.toCreateOpeningCommand(
                request, operatorId, applicantId, typePair.openingTypeCode, typePair.purposeTypeCode, null));
        // Asm
        return openingMapper.toCreateResponse(busNo);
    }

    /**
     * 编辑期初单（IF-BAS-INV-OPN-05）；全量替换明细。
     *
     * @param request 含 busNo 与明细
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateInventoryOpening(UpdateInventoryOpeningRequest request) {
        // Gate：busNo / locationId
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        if (request.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "locationId 不能为空");
        }
        if (request.getOccurrenceTime() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "occurrenceTime 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.BAS_INV_OPN_101, "当前用户未绑定医务人员档案");
        // Pre：可编辑态
        List<SupplyRequestModel> existing = this.loadOpeningLines(request.getBusNo().trim());
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(existing.get(0).getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_003, "当前状态不可编辑");
        }
        businessLockSupport.assertWritableOrCloseAndReject(request.getLocationId(), operatorId);
        LocationTypePair typePair = this.resolveOpeningTypePair(request.getLocationId());
        Map<String, ProductDefinitionDisplayDto> products = this.loadProductsForWritableLines(request.getLines());
        // 裁决 2B：持久化编辑须至少一行（空明细仅允许本地草稿，不可落库清空）
        traceGateSupport.assertWritableLines(
            request.getLines(), typePair.openingTypeCode, products, true);
        // Gate：同表同品同批号不得与已有批号进价冲突
        this.assertOpeningLotPurchasePriceConsistency(request.getLines());
        // Cmd：同号 createOpening 修订
        supplyRequestDomainService.createOpening(
            openingMapper.toCreateOpeningCommand(
                request, operatorId, applicantId, typePair.openingTypeCode, typePair.purposeTypeCode));
        // Asm：空响应
    }

    /**
     * 提交审批（IF-BAS-INV-OPN-06）。
     *
     * @param request 含 busNo
     */
    @Transactional(rollbackFor = Exception.class)
    public void submitInventoryOpeningApproval(SubmitInventoryOpeningApprovalRequest request) {
        // Gate
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.BAS_INV_OPN_101, "当前用户未绑定医务人员档案");
        // Pre：至少一行、关闭/封锁、同库无其它审核中
        List<SupplyRequestModel> lines = this.loadOpeningLines(request.getBusNo().trim());
        int productLineCount = 0;
        for (SupplyRequestModel line : lines) {
            if (line != null && line.getItemId() != null) {
                productLineCount++;
            }
        }
        if (productLineCount < 1) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_019, "请至少录入一行明细后再提交");
        }
        Long locationId = lines.get(0).getPurposeLocationId();
        businessLockSupport.assertWritableOrCloseAndReject(locationId, operatorId);
        if (supplyRequestQueryService.existsOpeningInApprovalForLocation(locationId)) {
            // 若本单已是 APPROVAL 则允许幂等；其它单审核中拒绝
            if (!SupplyStatus.APPROVAL.getCode().equals(lines.get(0).getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_017,
                    "该库房已有审核中的期初单，请先处理");
            }
        }
        try {
            // Cmd
            supplyRequestDomainService.submitApproval(
                openingMapper.toSubmitApprovalCommand(request, operatorId, applicantId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_004, "提交审批失败：" + ex.getMessage());
        }
        // Asm
    }

    /**
     * 撤回审批（IF-BAS-INV-OPN-07）。
     *
     * @param request 含 busNo
     */
    @Transactional(rollbackFor = Exception.class)
    public void withdrawInventoryOpeningApproval(WithdrawInventoryOpeningApprovalRequest request) {
        // Gate
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        this.loadOpeningLines(request.getBusNo().trim());
        try {
            // Cmd：对齐进货 withdrawApproval
            supplyRequestDomainService.withdrawApproval(
                openingMapper.toWithdrawApprovalCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_005, "撤回失败：" + ex.getMessage());
        }
        // Asm
    }

    /**
     * 审批同意（IF-BAS-INV-OPN-08）：库房串行；可选 void 前单；agree→flush→Delivery→库存→批号价→追溯真覆盖。
     *
     * @param request 含 busNo
     */
    @Transactional(rollbackFor = Exception.class)
    public void agreeInventoryOpening(AgreeInventoryOpeningRequest request) {
        // Gate
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        String busNo = request.getBusNo().trim();
        // Pre：回读须 APPROVAL
        List<SupplyRequestModel> beforeLines = this.loadOpeningLines(busNo);
        SupplyRequestModel head = beforeLines.get(0);
        if (!SupplyStatus.APPROVAL.getCode().equals(head.getStatusEnum())) {
            SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(busNo);
            if (Boolean.TRUE.equals(validation.getHasAgreed())) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_006, "期初单已同意，禁止重复同意");
            }
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_006, "仅审核中单据可同意");
        }
        Long locationId = head.getPurposeLocationId();
        // Pre：关闭/封锁闸门 + 库房串行锁
        String lockKey = ("INV_OPN_AGREE_" + locationId).intern();
        synchronized (lockKey) {
            businessLockSupport.assertWritableOrCloseAndReject(locationId, operatorId);
            Map<String, ProductDefinitionDisplayDto> products = this.loadProductsForLines(beforeLines);
            try {
                // Pre：追溯闸门
                traceGateSupport.assertAgreedLinesTrace(beforeLines, products);
            } catch (BusinessException ex) {
                throw ex;
            }
            // Cmd0：若存在前有效 AGREE，先 VoidOrchestrator 回滚
            Optional<String> priorBusNo = supplyRequestQueryService.findCurrentAgreedOpeningBusNo(locationId);
            if (priorBusNo.isPresent() && !busNo.equals(priorBusNo.get())) {
                inventoryOpeningVoidOrchestrator.voidOpening(priorBusNo.get(), operatorId, "被新期初单覆盖");
            }
            // Pre：裁决 5C — 码已在院则拒绝（前单冲平后仍在院的视为脏数据/外源码）
            this.assertOpeningTracesNotAlreadyInHospital(beforeLines);
            // Cmd1：agreeRequest
            try {
                supplyRequestDomainService.agreeRequest(openingMapper.toAgreeRequestCommand(request, operatorId));
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_006, "同意失败：" + ex.getMessage());
            }
            // Cmd2：flush 后回读 AGREE 行
            supplyRequestDomainService.flushPendingChanges();
            SupplyByBusNoQuery q = new SupplyByBusNoQuery();
            q.setBusNo(busNo);
            List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
            this.assertOpeningType(agreedLines);
            // Gate：同意入账前再拦同批号异价
            this.assertOpeningSupplyLotPurchasePriceConsistency(agreedLines);
            // Cmd3：发放
            try {
                supplyDeliveryDomainService.createSupplyDelivery(
                    openingMapper.toCreateSupplyDeliveryCommand(agreedLines, operatorId));
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_009, "发放落库失败：" + ex.getMessage());
            }
            // Cmd4～5：整库清账后按本单重建 + 批号价 + 追溯 IN（无 OUT 叠量）
            this.postAgreeInventoryPriceAndTrace(agreedLines, products, operatorId, busNo);
        }
        // Asm：无响应体
    }

    /**
     * 审批驳回（IF-BAS-INV-OPN-09）。
     *
     * @param request 含 busNo 与可选 reason
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejectInventoryOpening(RejectInventoryOpeningRequest request) {
        // Gate
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        this.loadOpeningLines(request.getBusNo().trim());
        try {
            // Cmd
            supplyRequestDomainService.rejectRequest(openingMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_010, "驳回失败：" + ex.getMessage());
        }
        // Asm
    }

    /**
     * 删除单据（IF-BAS-INV-OPN-10）；同意态须走作废。
     *
     * @param request 含 busNo
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeInventoryOpening(RemoveInventoryOpeningRequest request) {
        // Gate
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        List<SupplyRequestModel> lines = this.loadOpeningLines(request.getBusNo().trim());
        // Pre：已同意不可删
        if (SupplyStatus.AGREE.getCode().equals(lines.get(0).getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_011, "已同意单据请走作废");
        }
        try {
            // Cmd
            supplyRequestDomainService.removeByBusNo(openingMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_011, "删除失败：" + ex.getMessage());
        }
        // Asm
    }

    /**
     * 选品检索（IF-BAS-INV-OPN-11）。
     *
     * @param request 库房上下文与关键字分页
     * @return 选品行（含 categoryCode、unitBands、目录进价；与进货选品同装）
     */
    public PageInventoryOpeningsProductsResponse pageProductsForInventoryOpening(
        PageProductsForInventoryOpeningRequest request) {
        // Gate
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "请求不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.BAS_INV_OPN_002, "分页参数不能为空");
        try {
            // Pre：产品目录分页
            ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
            PageQueryBinder.bind(request, query);
            if (StringUtils.isNotEmpty(request.getProductTableName())) {
                query.setProductTableName(request.getProductTableName().trim());
            }
            Page<ProductDefinitionDisplayDto> page = productDefinitionPurchasePageLoader.pageForPurchase(query);
            List<InventoryOpeningProductRowDto> rows = openingMapper.toProductRows(page);
            // Asm：补齐单位槽与 PURCHASE 进价（选品走当前价合法场景）
            this.enrichOpeningProductSelectionRows(rows);
            PageInventoryOpeningsProductsResponse res = new PageInventoryOpeningsProductsResponse();
            res.setTotal(Long.valueOf(page.getTotalElements()));
            res.setRecords(rows);
            return res;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("pageProductsForInventoryOpening failed", ex);
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_104, "选品检索失败");
        }
    }

    /**
     * 下载导入模板（IF-BAS-INV-OPN-12）。
     *
     * @param request 可空
     * @return xlsx 字节包装
     */
    public DownloadInventoryOpeningImportTemplateResponse downloadInventoryOpeningImportTemplate(
        DownloadInventoryOpeningImportTemplateRequest request) {
        // Gate：登录
        currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.BAS_INV_OPN_105, "用户未登录");
        // Asm：生成模板
        try {
            DownloadInventoryOpeningImportTemplateResponse res = new DownloadInventoryOpeningImportTemplateResponse();
            res.setFileName(InventoryOpeningExcelSupport.TEMPLATE_FILE_NAME);
            res.setContentType(InventoryOpeningExcelSupport.XLSX_CONTENT_TYPE);
            res.setFileBytes(excelSupport.buildTemplateBytes());
            return res;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_105, "模板下载失败");
        }
    }

    /**
     * 上传导入（IF-BAS-INV-OPN-13）；整单失败零写入。
     *
     * @param request 库房与文件
     * @return 成功 busNo 或 errors
     */
    @Transactional(rollbackFor = Exception.class)
    public ImportInventoryOpeningResponse importInventoryOpening(ImportInventoryOpeningRequest request) {
        // Gate
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "locationId 不能为空");
        }
        if (request.getFileBytes() == null || request.getFileBytes().length == 0) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_012, "上传文件为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.BAS_INV_OPN_101, "当前用户未绑定医务人员档案");
        // Pre：封锁
        businessLockSupport.assertWritableOrCloseAndReject(request.getLocationId(), operatorId);
        InventoryOpeningExcelSupport.ParseResult parseResult = excelSupport.parse(request.getFileBytes());
        if (parseResult.hasErrors()) {
            return this.toImportFailResponse(parseResult.getErrors());
        }
        // Pre：业务编码 → 主键（物品/供应商）；失败整单回传错误清单
        List<InventoryOpeningImportErrorDto> resolveErrors = new ArrayList<>();
        List<InventoryOpeningLineDto> resolvedLines =
            this.resolveImportLines(parseResult.getRawLines(), resolveErrors);
        if (!resolveErrors.isEmpty()) {
            return this.toImportFailResponse(resolveErrors);
        }
        LocationTypePair typePair = this.resolveOpeningTypePair(request.getLocationId());
        Map<String, ProductDefinitionDisplayDto> products =
            this.loadProductsForWritableLines(resolvedLines);
        // Pre：数量/单位对齐页面保存——展示单位数量换算为拆零落库；有码默认贴码单位
        List<InventoryOpeningImportErrorDto> qtyErrors = new ArrayList<>();
        this.normalizeImportLinesQtyAndTraceUnitLikeUi(
            parseResult.getRawLines(), products, qtyErrors);
        if (!qtyErrors.isEmpty()) {
            return this.toImportFailResponse(qtyErrors);
        }
        try {
            // Pre：全量业务校验（与页面 create/update 同闸门）
            traceGateSupport.assertWritableLines(
                resolvedLines, typePair.openingTypeCode, products, true);
            // Gate：同表同品同批号不得与已有批号进价冲突
            this.assertOpeningLotPurchasePriceConsistency(resolvedLines);
        } catch (BusinessException ex) {
            InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
            err.setExcelRowNum(Integer.valueOf(0));
            err.setColumnName("校验");
            err.setCode(ex.getErrorCode());
            err.setMessage(ex.getMessage());
            List<InventoryOpeningImportErrorDto> errors = new ArrayList<>();
            errors.add(err);
            return this.toImportFailResponse(errors);
        }
        // Cmd：通过后单事务建草稿
        String busNo = supplyRequestDomainService.createOpening(
            openingMapper.toCreateOpeningCommandFromLines(
                request.getLocationId(),
                null,
                LocalDateTime.now(),
                resolvedLines,
                operatorId,
                applicantId,
                typePair.openingTypeCode,
                typePair.purposeTypeCode,
                null));
        // Asm：成功
        ImportInventoryOpeningResponse ok = new ImportInventoryOpeningResponse();
        ok.setBusNo(busNo);
        return ok;
    }

    /**
     * 关闭期初（IF-BAS-INV-OPN-14）；关前无审核中；可空关；幂等。
     *
     * @param request 含 locationId
     * @return openingClosed=true
     */
    @Transactional(rollbackFor = Exception.class)
    public CloseInventoryOpeningResponse closeInventoryOpening(CloseInventoryOpeningRequest request) {
        // Gate
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "locationId 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        Long locationId = request.getLocationId();
        // Pre：已关闭幂等
        if (locationQueryService.isInventoryOpeningClosed(locationId)) {
            return openingMapper.toCloseResponse(locationId);
        }
        // Pre：关前无审核中
        if (supplyRequestQueryService.existsOpeningInApprovalForLocation(locationId)) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_018,
                "请先撤回或驳回审核中的期初单再关闭");
        }
        try {
            // Cmd
            businessLockSupport.closeOpeningQuietly(locationId, operatorId);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_013, "关闭期初失败：" + ex.getMessage());
        }
        // Asm
        return openingMapper.toCloseResponse(locationId);
    }

    /**
     * 作废当前有效单（IF-BAS-INV-OPN-15）；关闭后禁止。
     *
     * @param request 含 busNo
     * @return 作废结果
     */
    @Transactional(rollbackFor = Exception.class)
    public VoidInventoryOpeningResponse voidInventoryOpening(VoidInventoryOpeningRequest request) {
        // Gate
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        List<SupplyRequestModel> lines = this.loadOpeningLines(request.getBusNo().trim());
        SupplyRequestModel head = lines.get(0);
        // Pre：须 AGREE
        if (!SupplyStatus.AGREE.getCode().equals(head.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_015, "仅已同意期初单可作废");
        }
        // Pre：关闭后禁止作废
        if (locationQueryService.isInventoryOpeningClosed(head.getPurposeLocationId())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_014,
                "期初已关闭，请走损益/盘点纠错");
        }
        try {
            // Cmd：VoidOrchestrator（库存+追溯+状态）
            inventoryOpeningVoidOrchestrator.voidOpening(
                request.getBusNo().trim(), operatorId, request.getReason());
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_015, "作废回滚失败：" + ex.getMessage());
        }
        // Asm
        VoidInventoryOpeningResponse res = new VoidInventoryOpeningResponse();
        res.setBusNo(request.getBusNo().trim());
        res.setSuccess(Boolean.TRUE);
        return res;
    }

    /**
     * 从上一张复制建草稿（IF-BAS-INV-OPN-16）。
     *
     * @param request 库房与可选源单号
     * @return 新草稿 busNo
     */
    @Transactional(rollbackFor = Exception.class)
    public CreateInventoryOpeningResponse copyInventoryOpeningFromPrevious(
        CopyInventoryOpeningFromPreviousRequest request) {
        // Gate
        if (request == null || request.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "locationId 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.BAS_INV_OPN_101, "用户未登录");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.BAS_INV_OPN_101, "当前用户未绑定医务人员档案");
        // Pre：封锁
        businessLockSupport.assertWritableOrCloseAndReject(request.getLocationId(), operatorId);
        String sourceBusNo = request.getSourceBusNo();
        if (StringUtils.isEmpty(sourceBusNo)) {
            Optional<String> cur = supplyRequestQueryService.findCurrentAgreedOpeningBusNo(request.getLocationId());
            if (cur.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_016, "无可复制源单");
            }
            sourceBusNo = cur.get();
        } else {
            sourceBusNo = sourceBusNo.trim();
        }
        // Pre：源单同库 OPENING 且 AGREE 或作废
        List<SupplyRequestModel> sourceLines = this.loadOpeningLines(sourceBusNo);
        SupplyRequestModel sourceHead = sourceLines.get(0);
        if (!request.getLocationId().equals(sourceHead.getPurposeLocationId())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_016, "源单与目标库房不一致");
        }
        String st = sourceHead.getStatusEnum();
        boolean allowed = SupplyStatus.AGREE.getCode().equals(st)
            || SupplyStatus.EXPIRED_INVALIDATED.getCode().equals(st);
        if (!allowed) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_016, "源单须为已同意或已作废");
        }
        LocationTypePair typePair = this.resolveOpeningTypePair(request.getLocationId());
        List<InventoryOpeningLineDto> copyLines = this.toWritableLinesFromModels(sourceLines);
        Map<String, ProductDefinitionDisplayDto> products = this.loadProductsForWritableLines(copyLines);
        // Pre：复制行须过闸门（供应商/效期/强制码等），避免脏源单静默落库
        traceGateSupport.assertWritableLines(copyLines, typePair.openingTypeCode, products, true);
        // Gate：同表同品同批号不得与已有批号进价冲突
        this.assertOpeningLotPurchasePriceConsistency(copyLines);
        // Cmd：createOpening 新草稿
        String busNo = supplyRequestDomainService.createOpening(
            openingMapper.toCreateOpeningCommandFromLines(
                request.getLocationId(),
                sourceHead.getPractitionerId(),
                LocalDateTime.now(),
                copyLines,
                operatorId,
                applicantId,
                typePair.openingTypeCode,
                typePair.purposeTypeCode,
                null));
        // Asm
        return openingMapper.toCreateResponse(busNo);
    }

    // —— 内部：同意后入账（整库清零 + 按本单重建）——

    /**
     * 同意后入账编排：整库清零 → 按本单建/加库存 → 批号价 → 追溯 IN。
     *
     * <p>裁决 4B：先扣光该库正数量，再对本单四元组命中行 {@code increase}、未命中 {@code create}。
     * 裁决 5C：调用前须已拒绝在院码；此处仅写 IN。</p>
     *
     * @param agreedLines 已同意供应行
     * @param products    产品读模型（复合键）
     * @param operatorId  操作人
     * @param busNo       本单号（追溯 bus_no）
     */
    private void postAgreeInventoryPriceAndTrace(
        List<SupplyRequestModel> agreedLines,
        Map<String, ProductDefinitionDisplayDto> products,
        Long operatorId,
        String busNo
    ) {
        Long locationId = agreedLines.get(0).getPurposeLocationId();
        // 裁决 4B：先清空该库全部正数量；残留零数量行保留，本单命中四元组走加量而非新建
        this.clearAllPositiveInventoryAtLocation(locationId, operatorId);

        List<RecordTraceFromSupplyLineSpec> traceCommands = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null || line.getItemId() == null) {
                continue;
            }
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.BAS_INV_OPN_009, "期初行数量须为拆零正数量");
            String resolvedLot = line.getLotNumber();
            if (StringUtils.isEmpty(resolvedLot)) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_009, "批号不能为空");
            }
            resolvedLot = resolvedLot.trim();
            ProductDefinitionDisplayDto product = products.get(
                ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId()));
            String splitUnitCode = null;
            if (product != null && StringUtils.isNotEmpty(product.getSplitUnitCode())) {
                splitUnitCode = product.getSplitUnitCode().trim();
            }
            if (StringUtils.isEmpty(splitUnitCode)) {
                splitUnitCode = line.getUnitCode();
            }
            String productName = "未知品规";
            if (product != null && StringUtils.isNotEmpty(product.getProductName())) {
                productName = product.getProductName().trim();
            }
            Long supplierId = line.getSupplierId();
            if (supplierId == null) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_009,
                    "供应商必填：" + resolvedLot);
            }
            // 清零后：五元组已有行则加至本单量；无行才新建（同批号不同供应商可并存）
            InventoryMatchKey matchKey = new InventoryMatchKey();
            matchKey.setSupplierId(supplierId);
            matchKey.setLotNumber(resolvedLot);
            matchKey.setLocationId(line.getPurposeLocationId());
            matchKey.setItemTable(line.getItemTable());
            matchKey.setItemId(line.getItemId());
            Long existingId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(matchKey).orElse(null);
            try {
                if (existingId != null) {
                    inventoryItemDomainService.increaseQuantity(
                        openingMapper.toIncreaseQuantityCommand(existingId, splitQty, operatorId));
                } else {
                    inventoryItemDomainService.createInventoryItem(
                        openingMapper.toCreateInventoryItemCommand(
                            line, line.getPurposeLocationId(), productName, resolvedLot,
                            splitQty, splitUnitCode, operatorId, product, supplierId));
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_009, "库存入账失败：" + ex.getMessage());
            }
            // 批号价：与进货核心一致，进价同时写 LOT_NUMBER_COST / LOT_NUMBER_PRICE（院点中药加价另挂 AOP）
            this.writeLotPricesForOpeningLine(line, product, resolvedLot, operatorId);
            // 追溯：仅 IN（裁决 5C 已拒绝在院码）；贴码单位取药典
            if (StringUtils.isNotEmpty(line.getTraceNo())
                && traceNumberRecordOrchestrator.hasTraceNos(line.getTraceNo())) {
                MedicationProductUnitConversionSource conversionSource =
                    ProductDefinitionDisplaySupport.toUnitConversionSource(product);
                String traceUnit = null;
                if (product != null && StringUtils.isNotEmpty(product.getTraceUnitCode())) {
                    traceUnit = product.getTraceUnitCode().trim();
                }
                if (StringUtils.isEmpty(traceUnit)) {
                    throw new BusinessException(
                        ErrorCodeConstants.BAS_INV_OPN_009,
                        "有追溯码的明细须在药品/耗材目录维护追溯码单位"
                    );
                }
                RecordTraceFromSupplyLineSpec inCmd = new RecordTraceFromSupplyLineSpec();
                inCmd.setOperatorId(operatorId);
                inCmd.setSupplyTypeCode(line.getTypeEnum());
                inCmd.setBusNo(busNo);
                inCmd.setItemTable(line.getItemTable().trim());
                inCmd.setItemId(line.getItemId());
                inCmd.setLotNumber(resolvedLot);
                inCmd.setTraceNos(line.getTraceNo());
                inCmd.setTraceUnitCode(traceUnit);
                inCmd.setQuantity(splitQty);
                inCmd.setUnitCode(splitUnitCode);
                inCmd.setUnitConversionSource(conversionSource);
                inCmd.setLocationId(line.getPurposeLocationId());
                inCmd.setLocationTypeEnum(line.getPurposeTypeEnum());
                inCmd.setRecordDirectionEnum(TraceDirection.IN.getCode());
                traceCommands.add(inCmd);
            }
        }
        if (!traceCommands.isEmpty()) {
            try {
                traceNumberSupplyAgreeTraceOrchestrator.recordSingleEndLines(traceCommands);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_009, "追溯写入失败：" + ex.getMessage());
            }
        }
    }

    /**
     * 裁决 4B：将该库房全部正数量库存扣至零（保留零数量行，供后续 increase）。
     *
     * @param locationId 目标库房
     * @param operatorId 操作人
     */
    private void clearAllPositiveInventoryAtLocation(Long locationId, Long operatorId) {
        List<com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel> existing =
            inventoryItemQueryService.listByLocationId(locationId);
        Map<Long, BigDecimal> decreaseById = new HashMap<>();
        for (com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel inv : existing) {
            if (inv == null || inv.getId() == null) {
                continue;
            }
            if (inv.getQuantity() == null || inv.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            decreaseById.put(inv.getId(), inv.getQuantity());
        }
        if (!decreaseById.isEmpty()) {
            inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseById);
        }
    }

    /**
     * 裁决 5C：本单追溯码若已在院且剩余&gt;0 则拒绝同意（须人工清码）。
     *
     * <p>应在 Void 前单之后调用：前单冲平后仍在院的码视为外源脏数据。</p>
     *
     * @param lines 待同意明细
     */
    private void assertOpeningTracesNotAlreadyInHospital(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
            if (!traceNumberRecordOrchestrator.hasTraceNos(line.getTraceNo())) {
                continue;
            }
            List<String> codes = traceNumberRecordOrchestrator.splitTraceNos(line.getTraceNo());
            for (String code : codes) {
                try {
                    TraceNumberQueryModel master = traceNumberQueryService.getTraceNumberByTraceNo(code);
                    if (master == null) {
                        continue;
                    }
                    String status = master.getInHospitalStatusEnum();
                    BigDecimal remaining = master.getRemainingSplitQuantity();
                    boolean inHospital = com.openhis.health.domain.share.enums.InHospitalStatus.IN_HOSPITAL.getCode()
                        .equals(status);
                    boolean hasRemain = remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0;
                    if (inHospital && hasRemain) {
                        throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_007,
                            "追溯码已在院，请先人工清码后再做期初：" + code);
                    }
                } catch (BusinessException ex) {
                    if (ErrorCodeConstants.BAS_INV_OPN_007.equals(ex.getErrorCode())) {
                        throw ex;
                    }
                    // 主档不存在或其它读失败：允许后续 IN
                }
            }
        }
    }

    /**
     * 按本单写/覆盖批号进价与售价，并回写目录参考价 {@code PURCHASE}/{@code SALE}。
     *
     * <p>行 {@code price} 恒为<strong>每包装单位价</strong>，与 {@code unitCode}（操作/数量单位）无关；
     * 直接写入 {@code LOT_NUMBER_COST}/{@code LOT_NUMBER_PRICE}（同值）。中草药 ×1.25 由院点 AOP 处理后再抬 {@code SALE}。</p>
     * <p>进价可空（药房/耗材房）；空则跳过写价与参考价。同批号进价同值跳过批号写，仍回写参考价；异价（门禁已过）或无价：停用旧 ACTIVE 后写入（BR-WHS-IN-018）。</p>
     *
     * @param line         供应行
     * @param product      产品（财务类型）
     * @param resolvedLot  批号
     * @param operatorId   操作人
     */
    private void writeLotPricesForOpeningLine(
        SupplyRequestModel line,
        ProductDefinitionDisplayDto product,
        String resolvedLot,
        Long operatorId
    ) {
        // 行进价=包装价；无进价则不写批号价（药房/耗材可空）
        BigDecimal costSource = line.getPrice();
        if (costSource == null) {
            return;
        }
        // 禁止按 unitCode 再换算：price 已是包装单位价
        BigDecimal priceVal = costSource.setScale(
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, RoundingMode.HALF_UP);
        BigDecimal existingPurchase = chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
            line.getItemTable().trim(), line.getItemId(), resolvedLot);
        String pricingTypeCode = ChargePricingTypeCodeResolver.resolveTypeCodeForLotPriceLine(
            line.getItemTable().trim(), line.getItemId(), product, chargeItemDefinitionQueryService);
        if (StringUtils.isEmpty(pricingTypeCode)) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_009, "批号维定价缺少财务类型");
        }
        // 同批号进价已相同：跳过批号写价，仍回写目录参考价
        boolean skipLotWrite = existingPurchase != null && existingPurchase.compareTo(priceVal) == 0;
        if (!skipLotWrite) {
            ChargeItemDefinitionQueryCommand priceDedupQuery = new ChargeItemDefinitionQueryCommand();
            priceDedupQuery.setInstanceTable(line.getItemTable().trim());
            priceDedupQuery.setInstanceId(line.getItemId());
            priceDedupQuery.setLotNo(resolvedLot);
            // 异价或仅有售价残留：先停用旧 ACTIVE 再写入
            if (chargeItemDefinitionQueryService.existsLotNumberCostOrLotNumberPriceForInstanceAndLot(priceDedupQuery)) {
                DeactivateActivePriceLinesCommand deactivate = new DeactivateActivePriceLinesCommand();
                deactivate.setOperatorId(operatorId);
                List<DeactivateActivePriceLinesCommand.Item> deactivateItems = new ArrayList<>();
                DeactivateActivePriceLinesCommand.Item costDeact = new DeactivateActivePriceLinesCommand.Item();
                costDeact.setInstanceTable(line.getItemTable().trim());
                costDeact.setInstanceId(line.getItemId());
                costDeact.setPriceTypeCode(PriceType.LOT_NUMBER_COST.getCode());
                costDeact.setLotNo(resolvedLot);
                deactivateItems.add(costDeact);
                DeactivateActivePriceLinesCommand.Item saleDeact = new DeactivateActivePriceLinesCommand.Item();
                saleDeact.setInstanceTable(line.getItemTable().trim());
                saleDeact.setInstanceId(line.getItemId());
                saleDeact.setPriceTypeCode(PriceType.LOT_NUMBER_PRICE.getCode());
                saleDeact.setLotNo(resolvedLot);
                deactivateItems.add(saleDeact);
                deactivate.setItems(deactivateItems);
                chargeItemDefinitionDomainService.deactivateActivePriceLinesByCommand(deactivate);
            }

            SavePriceLineCommand costCmd = new SavePriceLineCommand();
            costCmd.setOperatorId(operatorId);
            SavePriceLineCommand.Item costItem = new SavePriceLineCommand.Item();
            costItem.setInstanceTable(line.getItemTable().trim());
            costItem.setInstanceId(line.getItemId());
            costItem.setTypeCode(pricingTypeCode);
            costItem.setPriceTypeCode(PriceType.LOT_NUMBER_COST.getCode());
            costItem.setLotNo(resolvedLot);
            costItem.setPriceValue(priceVal);
            costCmd.setItem(costItem);
            chargeItemDefinitionDomainService.addLotPurchasePrice(costCmd);

            // LOT_NUMBER_PRICE 与进价同值；朝阳院点同意后 AOP 再对中草药 ×1.25
            SavePriceLineCommand saleCmd = new SavePriceLineCommand();
            saleCmd.setOperatorId(operatorId);
            SavePriceLineCommand.Item saleItem = new SavePriceLineCommand.Item();
            saleItem.setInstanceTable(line.getItemTable().trim());
            saleItem.setInstanceId(line.getItemId());
            saleItem.setTypeCode(pricingTypeCode);
            saleItem.setPriceTypeCode(PriceType.LOT_NUMBER_PRICE.getCode());
            saleItem.setLotNo(resolvedLot);
            saleItem.setPriceValue(priceVal);
            saleCmd.setItem(saleItem);
            chargeItemDefinitionDomainService.addLotSalePrice(saleCmd);
        }
        // Cmd：目录参考价跟本行包装进价（核心进售同值；中草药院点 after 再抬 SALE）
        this.catalogReferencePriceSyncSupport.syncPurchaseAndSaleIfChanged(
            line.getItemTable().trim(),
            line.getItemId(),
            pricingTypeCode,
            priceVal,
            priceVal,
            operatorId);
    }

    // —— 内部：装载 / 类型映射 / 展示增强 ——

    /**
     * 按单号加载期初供应行；不存在或非 OPENING 类型则拒绝。
     *
     * @param busNo 业务单号
     * @return 非空明细列表
     */
    private List<SupplyRequestModel> loadOpeningLines(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_103, "期初单不存在");
        }
        this.assertOpeningType(lines);
        return lines;
    }

    /**
     * 断言首行类型为三类 OPENING 之一。
     *
     * @param lines 供应行
     */
    private void assertOpeningType(List<SupplyRequestModel> lines) {
        SupplyRequestModel head = lines.get(0);
        if (!SupplyType.isOpeningTypeCode(head.getTypeEnum())) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_103, "单据类型非期初单");
        }
    }

    /**
     * 按 Location.form 映射 OPENING 类型与 purpose 类型码。
     *
     * @param locationId 目标库房
     * @return openingTypeCode + purposeTypeCode
     */
    private LocationTypePair resolveOpeningTypePair(Long locationId) {
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "库房不存在");
        }
        Location loc = locOpt.get();
        String form = loc.getFormEnum();
        String openingType;
        if (LocationForm.WAREHOUSE.getCode().equals(form)) {
            openingType = SupplyType.CABINET_OPENING.getCode();
        } else if (LocationForm.PHARMACY.getCode().equals(form)) {
            openingType = SupplyType.PHARMACY_OPENING.getCode();
        } else if (LocationForm.DEPARTMENT_PHARMACY.getCode().equals(form)) {
            openingType = SupplyType.DEPT_OPENING.getCode();
        } else {
            throw new BusinessException(ErrorCodeConstants.BAS_INV_OPN_002, "仅三房可做期初");
        }
        String purposeType = locationQueryService.requireSupplyRequestTypeForLocationId(locationId).getCode();
        return new LocationTypePair(openingType, purposeType);
    }

    /**
     * 批量装载供应行对应的产品读模型（复合键 map）。
     *
     * @param lines 供应行
     * @return 复合键 → 产品；加载失败返回空 map（调用方闸门对药品会 fail-closed）
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductsForLines(List<SupplyRequestModel> lines) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("loadProductsForLines degraded", ex);
            return new HashMap<>();
        }
    }

    /**
     * 批量装载可写 DTO 行对应的产品读模型。
     *
     * @param lines 可写明细
     * @return 复合键 → 产品；失败空 map
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductsForWritableLines(
        List<InventoryOpeningLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return new HashMap<>();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (InventoryOpeningLineDto line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("loadProductsForWritableLines degraded", ex);
            return new HashMap<>();
        }
    }

    /**
     * 按页内出现的库房，批量查当前有效 AGREE 期初单号（列表 currentEffective）。
     *
     * @param lines 页内明细行
     * @return locationId → 当前有效 busNo
     */
    private Map<Long, String> buildCurrentEffectiveMap(List<SupplyRequestModel> lines) {
        Map<Long, String> map = new HashMap<>();
        if (lines == null) {
            return map;
        }
        Set<Long> locIds = new HashSet<>();
        for (SupplyRequestModel line : lines) {
            if (line != null && line.getPurposeLocationId() != null) {
                locIds.add(line.getPurposeLocationId());
            }
        }
        for (Long locId : locIds) {
            Optional<String> busNo = supplyRequestQueryService.findCurrentAgreedOpeningBusNo(locId);
            if (busNo.isPresent()) {
                map.put(locId, busNo.get());
            }
        }
        return map;
    }

    /**
     * 列表进价总额：Mapper 已汇总落库 totalPrice；缺省行按「拆零量 + 包装进价」重算（期初 price 恒为包装价）。
     *
     * @param records 列表 DTO
     * @param lines   本页明细行
     */
    private void enrichListPurchaseTotals(
        List<InventoryOpeningListRowDto> records, List<SupplyRequestModel> lines) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, List<SupplyRequestModel>> byBusNo = new HashMap<>();
        if (lines != null) {
            for (SupplyRequestModel line : lines) {
                if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                    continue;
                }
                byBusNo.computeIfAbsent(line.getBusNo().trim(), k -> new ArrayList<>()).add(line);
            }
        }
        for (InventoryOpeningListRowDto row : records) {
            if (row == null || row.getTotalPrice() != null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            List<SupplyRequestModel> busLines = byBusNo.get(row.getBusNo().trim());
            if (busLines == null || busLines.isEmpty()) {
                continue;
            }
            List<BigDecimal> amounts = new ArrayList<>();
            for (SupplyRequestModel line : busLines) {
                if (line == null) {
                    continue;
                }
                BigDecimal tp = line.getTotalPrice();
                if (tp == null) {
                    // 期初行 price 恒为包装单位价，lineUnitCode 传 null 避免把包装价误当拆零价再放大
                    tp = warehouseLineAmountSupport.computeWarehouseSplitLineTotal(
                        line.getItemTable(),
                        line.getItemId(),
                        null,
                        line.getItemQuantity(),
                        line.getPrice());
                }
                if (tp != null) {
                    amounts.add(tp);
                }
            }
            if (!amounts.isEmpty()) {
                row.setTotalPrice(MonetaryAmountSupport.sumThenSettle(amounts));
            }
        }
    }

    /**
     * 列表行展示增强：库房名、制单人/审批人姓名（Mapper 占位 {@code id:xxx} 再解析）。
     *
     * @param records 列表 DTO
     */
    private void enrichListDisplayNames(List<InventoryOpeningListRowDto> records) {
        if (records == null) {
            return;
        }
        Set<Long> locIds = new HashSet<>();
        Set<Long> practitionerIds = new HashSet<>();
        for (InventoryOpeningListRowDto row : records) {
            if (row == null) {
                continue;
            }
            if (row.getLocationId() != null) {
                locIds.add(row.getLocationId());
            }
            Long applicantId = this.parseIdMarker(row.getApplicantName());
            if (applicantId != null) {
                practitionerIds.add(applicantId);
            }
            Long approverId = this.parseIdMarker(row.getApproverName());
            if (approverId != null) {
                practitionerIds.add(approverId);
            }
        }
        Map<Long, String> names = locationQueryService.mapNamesByIds(locIds);
        Map<Long, String> practitionerNames = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            List<Practitioner> practitioners = practitionerQueryService.findAllByIds(practitionerIds);
            if (practitioners != null) {
                for (Practitioner p : practitioners) {
                    if (p != null && p.getId() != null) {
                        practitionerNames.put(p.getId(), p.getName());
                    }
                }
            }
        }
        for (InventoryOpeningListRowDto row : records) {
            if (row == null) {
                continue;
            }
            if (row.getLocationId() != null) {
                row.setLocationName(names.get(row.getLocationId()));
            }
            Long applicantId = this.parseIdMarker(row.getApplicantName());
            if (applicantId != null) {
                row.setApplicantName(practitionerNames.get(applicantId));
            }
            Long approverId = this.parseIdMarker(row.getApproverName());
            if (approverId != null) {
                row.setApproverName(practitionerNames.get(approverId));
            }
        }
    }

    /**
     * 解析 Mapper 占位 {@code id:{practitionerId}}；非占位返回 null。
     *
     * @param raw 申请人/审批人展示字段
     * @return 人员主键或 null
     */
    private Long parseIdMarker(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        if (!raw.startsWith("id:")) {
            return null;
        }
        try {
            return Long.valueOf(raw.substring(3));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 收集位置主键列表（formInit 批量查关闭态）。
     *
     * @param locations 位置实体
     * @param out       输出 id 列表
     */
    private void collectLocationIds(List<Location> locations, List<Long> out) {
        if (locations == null) {
            return;
        }
        for (Location loc : locations) {
            if (loc != null && loc.getId() != null) {
                out.add(loc.getId());
            }
        }
    }

    /**
     * 源供应行 → 可写 DTO（复制 IF-16）。
     *
     * @param sourceLines 源单明细
     * @return 可写行列表
     */
    private List<InventoryOpeningLineDto> toWritableLinesFromModels(List<SupplyRequestModel> sourceLines) {
        List<InventoryOpeningLineDto> out = new ArrayList<>();
        for (SupplyRequestModel line : sourceLines) {
            if (line == null || line.getItemId() == null) {
                continue;
            }
            InventoryOpeningLineDto dto = new InventoryOpeningLineDto();
            dto.setItemTable(line.getItemTable());
            dto.setItemId(line.getItemId());
            dto.setLotNumber(line.getLotNumber());
            dto.setEndTime(line.getEndTime());
            dto.setStartTime(line.getStartTime());
            dto.setItemQuantity(line.getItemQuantity());
            dto.setUnitCode(line.getUnitCode());
            dto.setPrice(line.getPrice());
            // 期初不再维护独立售价；同意时批号售价=进价（院点中药加价另挂）
            dto.setTotalPrice(line.getTotalPrice());
            dto.setTraceNo(line.getTraceNo());
            dto.setSupplierId(line.getSupplierId());
            out.add(dto);
        }
        return out;
    }

    /**
     * 将 Excel 业务编码解析为明细主键：物品编码→itemId，供应商编码/名称→supplierId
     * （仅 {@link com.openhis.health.domain.share.enums.SupplierType#SUPPLIER} 启用主数据）；空单位留给后续按页面默认计量带补齐。
     *
     * @param rawLines 解析原始行
     * @param errors   解析错误（同码多命中/未找到等）
     * @return 已补齐主键的可写明细；有错误时仍返回已处理部分，调用方以 errors 为准
     */
    private List<InventoryOpeningLineDto> resolveImportLines(
        List<InventoryOpeningExcelSupport.RawImportLine> rawLines,
        List<InventoryOpeningImportErrorDto> errors
    ) {
        List<InventoryOpeningLineDto> out = new ArrayList<>();
        if (rawLines == null || rawLines.isEmpty()) {
            return out;
        }
        // 批量装载物品：按类型收集编码
        List<String> medBusNos = new ArrayList<>();
        List<String> deviceBusNos = new ArrayList<>();
        for (InventoryOpeningExcelSupport.RawImportLine raw : rawLines) {
            if (raw == null || raw.getLine() == null || StringUtils.isEmpty(raw.getItemBusNo())) {
                continue;
            }
            String table = raw.getLine().getItemTable();
            String code = raw.getItemBusNo().trim();
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
                medBusNos.add(code);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                deviceBusNos.add(code);
            }
        }
        Map<String, List<MedicationDefinition>> medByBusNo = new HashMap<>();
        if (!medBusNos.isEmpty()) {
            for (MedicationDefinition med : medicationDefinitionQueryService.findByBusNos(medBusNos)) {
                if (med == null || StringUtils.isEmpty(med.getBusNo()) || Boolean.TRUE.equals(med.getIsDeleted())) {
                    continue;
                }
                String key = med.getBusNo().trim();
                medByBusNo.computeIfAbsent(key, k -> new ArrayList<>()).add(med);
            }
        }
        Map<String, List<DeviceDefinition>> deviceByBusNo = new HashMap<>();
        if (!deviceBusNos.isEmpty()) {
            for (DeviceDefinition device : deviceDefinitionQueryService.findByBusNos(deviceBusNos)) {
                if (device == null || StringUtils.isEmpty(device.getBusNo())
                    || Boolean.TRUE.equals(device.getIsDeleted())) {
                    continue;
                }
                String key = device.getBusNo().trim();
                deviceByBusNo.computeIfAbsent(key, k -> new ArrayList<>()).add(device);
            }
        }
        // 供应商：仅认类型=供应商（code=2）且启用；禁止命中生产厂家（code=1），与页头下拉一致
        Map<String, Long> supplierIdByBusNo = new HashMap<>();
        Map<String, List<Long>> supplierIdsByName = new HashMap<>();
        for (SupplierListItemQueryModel supplier : supplierQueryService.listSuppliersByTypeAndActive(
            com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode())) {
            if (supplier == null || supplier.getId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(supplier.getBusNo())) {
                supplierIdByBusNo.put(supplier.getBusNo().trim(), supplier.getId());
            }
            if (StringUtils.isNotEmpty(supplier.getName())) {
                supplierIdsByName
                    .computeIfAbsent(supplier.getName().trim(), k -> new ArrayList<>())
                    .add(supplier.getId());
            }
        }

        for (InventoryOpeningExcelSupport.RawImportLine raw : rawLines) {
            if (raw == null || raw.getLine() == null) {
                continue;
            }
            InventoryOpeningLineDto line = raw.getLine();
            int excelRowNum = raw.getExcelRowNum();
            String itemBusNo = raw.getItemBusNo();
            if (StringUtils.isEmpty(itemBusNo) || StringUtils.isEmpty(line.getItemTable())) {
                this.addImportResolveError(errors, excelRowNum, "物品编码", "物品类型或编码无效");
                continue;
            }
            itemBusNo = itemBusNo.trim();
            Long itemId = null;
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable())) {
                List<MedicationDefinition> hits = medByBusNo.get(itemBusNo);
                if (hits == null || hits.isEmpty()) {
                    this.addImportResolveError(errors, excelRowNum, "物品编码", "未找到药品：" + itemBusNo);
                } else if (hits.size() > 1) {
                    this.addImportResolveError(errors, excelRowNum, "物品编码", "药品编码不唯一：" + itemBusNo);
                } else {
                    itemId = hits.get(0).getId();
                }
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(line.getItemTable())) {
                List<DeviceDefinition> hits = deviceByBusNo.get(itemBusNo);
                if (hits == null || hits.isEmpty()) {
                    this.addImportResolveError(errors, excelRowNum, "物品编码", "未找到耗材：" + itemBusNo);
                } else if (hits.size() > 1) {
                    this.addImportResolveError(errors, excelRowNum, "物品编码", "耗材编码不唯一：" + itemBusNo);
                } else {
                    itemId = hits.get(0).getId();
                }
            } else {
                this.addImportResolveError(errors, excelRowNum, "物品类型", "须为「药品」或「耗材」");
                continue;
            }
            if (itemId == null) {
                continue;
            }
            line.setItemId(itemId);
            // 空单位不在此回落拆零：由 normalizeImportLinesQtyAndTraceUnitLikeUi 按页面默认计量带（STOCK 优先）补齐并换算

            Long supplierId = null;
            if (StringUtils.isNotEmpty(raw.getSupplierBusNo())) {
                supplierId = supplierIdByBusNo.get(raw.getSupplierBusNo().trim());
                if (supplierId == null) {
                    this.addImportResolveError(errors, excelRowNum, "供应商编码",
                        "未找到供应商：" + raw.getSupplierBusNo().trim());
                    continue;
                }
            } else if (StringUtils.isNotEmpty(raw.getSupplierName())) {
                List<Long> nameHits = supplierIdsByName.get(raw.getSupplierName().trim());
                if (nameHits == null || nameHits.isEmpty()) {
                    this.addImportResolveError(errors, excelRowNum, "供应商名称",
                        "未找到供应商：" + raw.getSupplierName().trim());
                    continue;
                }
                if (nameHits.size() > 1) {
                    this.addImportResolveError(errors, excelRowNum, "供应商名称",
                        "供应商名称不唯一，请改填供应商编码：" + raw.getSupplierName().trim());
                    continue;
                }
                supplierId = nameHits.get(0);
            } else {
                this.addImportResolveError(errors, excelRowNum, "供应商", "请填写供应商编码或供应商名称");
                continue;
            }
            line.setSupplierId(supplierId);
            // 导入不落独立售价（与页面一致）
            line.setSalePrice(null);
            out.add(line);
        }
        return out;
    }

    /**
     * 导入行数量/进价/追溯单位规范化。
     *
     * <p><b>数量</b>：按用户填写的「单位编码」理解 Excel 数量 → × ratioToSplit 落拆零 {@code itemQuantity}；
     * 空单位时按期初页默认 STOCK 补齐；{@code unitCode} 始终保留该操作单位（不是计价单位）。</p>
     * <p><b>进价</b>：Excel {@code price} 一律为<strong>每包装单位价</strong>，与 {@code unitCode} 无换算关系，原样落库；
     * 同意写批号价时直接作包装锚，禁止再按 {@code unitCode} 二次换算。</p>
     * <p><b>追溯</b>：有码且无追溯码单位时默认目录包装单位。</p>
     *
     * @param rawLines Excel 原始行（含 excelRowNum；仅已解析出 itemId 的行处理）
     * @param products 产品读模型
     * @param errors   换算失败写入此处
     */
    private void normalizeImportLinesQtyAndTraceUnitLikeUi(
        List<InventoryOpeningExcelSupport.RawImportLine> rawLines,
        Map<String, ProductDefinitionDisplayDto> products,
        List<InventoryOpeningImportErrorDto> errors
    ) {
        if (rawLines == null || rawLines.isEmpty()) {
            return;
        }
        // 拆零数量刻度：与前端 persistDisplayQtyAsSplitForBands 落库一致（6 位）
        int splitQtyScale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;
        int moneyScale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE;
        for (InventoryOpeningExcelSupport.RawImportLine raw : rawLines) {
            if (raw == null || raw.getLine() == null) {
                continue;
            }
            InventoryOpeningLineDto line = raw.getLine();
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            int excelRowNum = raw.getExcelRowNum();
            ProductDefinitionDisplayDto product = null;
            if (products != null) {
                product = products.get(
                    ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId()));
            }
            // 贴码单位取药典：导入不写供应行（表已无 trace_unit_code 列）
            List<ProductDefinitionCatalogUnitBandDto> bands =
                ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
            // 操作单位：用户填什么就用什么换算数量；空则按期初页默认 STOCK
            String entryUnitCode = line.getUnitCode();
            if (StringUtils.isEmpty(entryUnitCode)) {
                ProductDefinitionCatalogUnitBandDto defaultBand =
                    this.resolveOpeningImportDefaultUnitBand(bands);
                if (defaultBand == null || StringUtils.isEmpty(defaultBand.getUnitCode())) {
                    this.addImportResolveError(
                        errors, excelRowNum, "单位编码", "品规无可用计量单位，无法换算数量");
                    continue;
                }
                entryUnitCode = defaultBand.getUnitCode().trim();
            } else {
                entryUnitCode = entryUnitCode.trim();
            }
            line.setUnitCode(entryUnitCode);

            // —— 数量：按用户填写单位 → 拆零 ——
            if (line.getItemQuantity() != null) {
                BigDecimal ratio = ProductDefinitionCatalogUnitBandsAssembler.resolveRatioToSplitByUnitCode(
                    bands, entryUnitCode);
                if (ratio == null) {
                    boolean isSplitUnit = product != null
                        && StringUtils.isNotEmpty(product.getSplitUnitCode())
                        && entryUnitCode.equals(product.getSplitUnitCode().trim());
                    if (!isSplitUnit) {
                        this.addImportResolveError(
                            errors,
                            excelRowNum,
                            "单位编码",
                            "无法识别单位「" + entryUnitCode + "」，请填写目录中的库存/包装/采购/拆零等单位码");
                        continue;
                    }
                } else if (ratio.compareTo(BigDecimal.ONE) != 0) {
                    BigDecimal displayQty = line.getItemQuantity();
                    BigDecimal splitQty = displayQty.multiply(ratio)
                        .setScale(splitQtyScale, RoundingMode.HALF_UP);
                    line.setItemQuantity(splitQty);
                }
            }

            // —— 进价：包装单位价原样落库（与 unitCode 无换算）——
            if (line.getPrice() != null) {
                line.setPrice(line.getPrice().setScale(moneyScale, RoundingMode.HALF_UP));
            }
        }
    }

    /**
     * 导入空单位时的默认计量带：对齐前端 {@code warehouseNonInboundDefaultBandKind}（期初页，非进货 PURCHASE）。
     *
     * @param bands 进货四槽
     * @return 默认带；无可用带时 null
     */
    private ProductDefinitionCatalogUnitBandDto resolveOpeningImportDefaultUnitBand(
        List<ProductDefinitionCatalogUnitBandDto> bands
    ) {
        if (bands == null || bands.isEmpty()) {
            return null;
        }
        ProductDefinitionCatalogUnitBandDto stock =
            this.findUnitBandByKind(bands, ProductDefinitionCatalogUnitBandConstants.BAND_STOCK);
        ProductDefinitionCatalogUnitBandDto pkg =
            this.findUnitBandByKind(bands, ProductDefinitionCatalogUnitBandConstants.BAND_PACKAGE);
        // STOCK 与 PACKAGE 同码不同倍数：默认 PACKAGE（对齐前端 conflict 分支）
        String pkgUnitCode = "";
        if (pkg.getUnitCode() != null) {
            pkgUnitCode = pkg.getUnitCode().trim();
        }
        if (stock != null && pkg != null
            && StringUtils.isNotEmpty(stock.getUnitCode())
            && stock.getUnitCode().trim().equals(pkgUnitCode)
            && stock.getRatioToSplit() != null
            && pkg.getRatioToSplit() != null
            && stock.getRatioToSplit().compareTo(pkg.getRatioToSplit()) != 0) {
            return pkg;
        }
        if (stock != null && stock.getRatioToSplit() != null
            && stock.getRatioToSplit().compareTo(BigDecimal.ZERO) > 0) {
            return stock;
        }
        ProductDefinitionCatalogUnitBandDto split =
            this.findUnitBandByKind(bands, ProductDefinitionCatalogUnitBandConstants.BAND_SPLIT);
        if (split != null && split.getRatioToSplit() != null
            && split.getRatioToSplit().compareTo(BigDecimal.ZERO) > 0) {
            return split;
        }
        if (pkg != null && pkg.getRatioToSplit() != null
            && pkg.getRatioToSplit().compareTo(BigDecimal.ZERO) > 0) {
            return pkg;
        }
        ProductDefinitionCatalogUnitBandDto purchase =
            this.findUnitBandByKind(bands, ProductDefinitionCatalogUnitBandConstants.BAND_PURCHASE);
        if (purchase != null && purchase.getRatioToSplit() != null
            && purchase.getRatioToSplit().compareTo(BigDecimal.ZERO) > 0) {
            return purchase;
        }
        return null;
    }

    /**
     * 按 bandKind 取单位槽。
     *
     * @param bands    单位槽列表
     * @param bandKind 槽语义
     * @return 命中带；未命中 null
     */
    private ProductDefinitionCatalogUnitBandDto findUnitBandByKind(
        List<ProductDefinitionCatalogUnitBandDto> bands,
        String bandKind
    ) {
        if (bands == null || StringUtils.isEmpty(bandKind)) {
            return null;
        }
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || StringUtils.isEmpty(band.getBandKind())) {
                continue;
            }
            if (bandKind.equals(band.getBandKind().trim())) {
                return band;
            }
        }
        return null;
    }

    /**
     * 追加导入解析错误项。
     *
     * @param errors       错误清单
     * @param excelRowNum  Excel 行号
     * @param columnName   列名
     * @param message      说明
     */
    private void addImportResolveError(
        List<InventoryOpeningImportErrorDto> errors,
        int excelRowNum,
        String columnName,
        String message
    ) {
        InventoryOpeningImportErrorDto err = new InventoryOpeningImportErrorDto();
        err.setExcelRowNum(Integer.valueOf(excelRowNum));
        err.setColumnName(columnName);
        err.setCode(ErrorCodeConstants.BAS_INV_OPN_012);
        err.setMessage(message);
        errors.add(err);
    }

    /**
     * 组装导入整单失败响应（零写入）。
     *
     * @param errors 行级错误清单
     * @return 失败响应
     */
    private ImportInventoryOpeningResponse toImportFailResponse(List<InventoryOpeningImportErrorDto> errors) {
        ImportInventoryOpeningResponse res = new ImportInventoryOpeningResponse();
        res.setMessage("导入失败已全部回滚，见错误清单");
        res.setErrors(errors);
        return res;
    }

    /**
     * 期初选品：写 PURCHASE 进价与单位四槽（对齐进货 IF-WHS-IN-11；选品当前价合法）。
     *
     * @param rows 当前页选品行
     */
    private void enrichOpeningProductSelectionRows(List<InventoryOpeningProductRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (InventoryOpeningProductRowDto row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getItemTable()) && row.getItemId() != null) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(row.getItemId());
                lookup.setProductTableName(row.getItemTable().trim());
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed for opening product selection rows", ex);
            }
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        for (InventoryOpeningProductRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isEmpty(row.getItemTable()) || row.getItemId() == null) {
                // 缺主键行：清空进价，避免分页残留
                row.setPurchasePrice(null);
                row.setReferencePurchasePrice(null);
                continue;
            }
            String table = row.getItemTable().trim();
            Long defId = row.getItemId();
            // 选品：读定价表当前 PURCHASE（与进货选品同锚）
            BigDecimal purchasePerPackage =
                this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(table, defId);
            row.setPurchasePrice(purchasePerPackage);
            row.setReferencePurchasePrice(purchasePerPackage);
            String key = ProductDefinitionKeyUtil.buildCompositeKey(table, defId);
            ProductDefinitionDisplayDto pr = products.get(key);
            List<ProductDefinitionCatalogUnitBandDto> bands =
                ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(
                    pr, terminologyMaps);
            ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                bands, pr, purchasePerPackage);
            row.setUnitBands(bands);
            if (pr != null) {
                row.setCategoryCodeDictText(pr.getCategoryCodeDictText());
                if (StringUtils.isEmpty(row.getManufacturer())) {
                    row.setManufacturer(pr.getManufacturer());
                }
                if (StringUtils.isEmpty(row.getProductBusNo())) {
                    row.setProductBusNo(pr.getProductBusNo());
                }
            }
        }
    }

    /**
     * 期初详情：补齐单位展示名与 productCatalog 单位槽（对齐进货详情）。
     *
     * @param lines           详情明细行
     * @param products        批量产品读模型
     * @param terminologyMaps 单位/术语映射
     */
    private void enrichOpeningDetailProductDisplay(
        List<InventoryOpeningDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (lines == null || lines.isEmpty() || products == null || products.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        for (InventoryOpeningDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            if (StringUtils.isEmpty(dto.getManufacturer())) {
                dto.setManufacturer(pr.getManufacturer());
            }
            dto.setUnitCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, dto.getUnitCode(), pr));
            ProductDefinitionCatalogEditorHintsDto catalog = dto.getProductCatalog();
            if (catalog == null) {
                catalog = ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                dto.setProductCatalog(catalog);
            } else if (catalog.getUnitBands() != null && !catalog.getUnitBands().isEmpty()) {
                this.productDefinitionDisplayTerminologyAssembler.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 详情行槽价：行 {@code price} 恒为每包装价；{@code unitCode} 仅为操作单位，禁止参与价换算。
     *
     * @param lines 详情明细列表
     */
    private void enrichOpeningDetailCatalogFromPersistedLinePrice(List<InventoryOpeningDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (InventoryOpeningDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            // 包装锚写四槽；勿把 unitCode 当计价单位
            ProductDefinitionCatalogUnitBandsAssembler.applyInboundBandsPriceUsingPackageAnchor(
                cat.getUnitBands(), null, dto.getPrice());
            if (dto.getPrice() != null) {
                cat.setPurchasePrice(dto.getPrice());
            }
        }
    }

    /**
     * 期初可写行：同表同品同批号进价须与已有 {@code LOT_NUMBER_COST} 一致。
     *
     * <p>期初行 {@code price} 恒为每包装单位价（与操作 {@code unitCode} 无关）；
     * 比对时禁止传入操作单位，否则门禁会把包装价误当拆零价再放大（如 20×50=1000）。</p>
     *
     * @param lines 期初明细
     */
    private void assertOpeningLotPurchasePriceConsistency(List<InventoryOpeningLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine> checkLines = new ArrayList<>();
        for (InventoryOpeningLineDto line : lines) {
            if (line == null) {
                continue;
            }
            LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine check =
                new LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine();
            check.setItemTable(line.getItemTable());
            check.setItemId(line.getItemId());
            check.setLotNumber(line.getLotNumber());
            check.setPrice(line.getPrice());
            // Gate：unitCode 留空 → 按已是包装锚比对，禁止二次换算
            checkLines.add(check);
        }
        this.lotNumberPurchasePriceConsistencyGate.assertIncomingLotPurchasePricesConsistent(
            checkLines, ErrorCodeConstants.BAS_INV_OPN_002);
    }

    /**
     * 期初同意行：同表同品同批号进价一致性。
     *
     * <p>供应行 {@code price} 同为包装单位价；比对不传操作 {@code unitCode}（与保存路径一致）。</p>
     *
     * @param lines AGREE 供应行
     */
    private void assertOpeningSupplyLotPurchasePriceConsistency(List<SupplyRequestModel> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine> checkLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine check =
                new LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine();
            check.setItemTable(line.getItemTable());
            check.setItemId(line.getItemId());
            check.setLotNumber(line.getLotNumber());
            check.setPrice(line.getPrice());
            // Gate：unitCode 留空 → 按已是包装锚比对，禁止二次换算
            checkLines.add(check);
        }
        this.lotNumberPurchasePriceConsistencyGate.assertIncomingLotPurchasePricesConsistent(
            checkLines, ErrorCodeConstants.BAS_INV_OPN_009);
    }

    /**
     * Location.form → OPENING type / purpose type 映射对。
     */
    private static final class LocationTypePair {
        /** 三类 OPENING 持久化码。 */
        private final String openingTypeCode;
        /** purpose_type_enum 持久化码。 */
        private final String purposeTypeCode;

        private LocationTypePair(String openingTypeCode, String purposeTypeCode) {
            this.openingTypeCode = openingTypeCode;
            this.purposeTypeCode = purposeTypeCode;
        }
    }
}
