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

package com.openhis.health.application.inventory.pharmacy.stockin.service;

import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.application.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInDetailLineDto;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInLineDto;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInListRowDto;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.pharmacy.stockin.mapper.InventoryPharmacyStockInMapper;
import com.openhis.health.application.inventory.pharmacy.stockin.request.*;
import com.openhis.health.application.inventory.pharmacy.stockin.response.*;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsFromLineItemsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.application.querysupport.product.ProductDefinitionSelectionTerminologySupport;
import com.openhis.health.application.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.PractitionerBusinessRoleCode;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 药房入库单（{@link SupplyType#PHARMACY_STOCK_IN}）应用编排。
 *
 * <p>区分目的药房与来源药库；列表按 {@code bus_no} 聚合。同意后：已同意 → 发放 → 目的药房库存（增/建）→ 追溯码。
 * 库存入账装配复用 {@link InventoryWarehouseInboundReceiptMapper}（与药库进货同意同构）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPharmacyStockInAppService {

    private final ICurrentUser currentUser;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final InventoryPharmacyStockInMapper pharmacyStockInMapper;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;

    public LoadPharmacyStockInFormInitResponse loadPharmacyStockInFormInit(LoadPharmacyStockInFormInitRequest request) {
        Long resolvedOrg = this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        try {
            List<Location> pharmacies = locationQueryService.listAllActivePharmacies();
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.loadAllPractitionersForRoleInOrganization(
                PractitionerBusinessRoleCode.PHARMACIST.getNumericCode(), resolvedOrg);
            List<Practitioner> warehouseKeepers = this.loadAllPractitionersForRoleInOrganization(
                PractitionerBusinessRoleCode.WAREHOUSE_ADMIN.getNumericCode(), resolvedOrg);
            return pharmacyStockInMapper.toLoadPharmacyStockInFormInitResponse(
                pharmacyStockInMapper.toOptionDtoListFromLocations(gatedPharmacies),
                pharmacyStockInMapper.toOptionDtoListFromLocations(gatedWarehouses),
                pharmacyStockInMapper.toOptionDtoListInboundItemTableTypes(),
                pharmacyStockInMapper.toOptionDtoListSupplyStatuses(),
                pharmacyStockInMapper.toOptionDtoListFromPractitioners(pharmacists),
                pharmacyStockInMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadPharmacyStockInFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_IN_101, "药房入库页主数据加载失败");
        }
    }

    public PagePharmacyStockInsResponse pagePharmacyStockIns(PagePharmacyStockInsRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_IN_001, "分页参数不能为空");
        this.validateOptionalPharmacyStockInTypeEnum(request.getTypeEnum());
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIdsForPharmacyGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.PHARMACY_STOCK_IN.getCode());
        // purposeLocationId=0：当前用户可见的全部药房；否则单药房且须在管辖门控内
        if (Long.valueOf(0L).equals(request.getPurposeLocationId())) {
            if (allowedPharmacies.isEmpty()) {
                listQuery.setPurposeLocationIds(List.of(-1L));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedPharmacies));
            }
        } else {
            // 单药房筛选（防横向越权）
            this.assertPharmacyLocationAllowed(request.getPurposeLocationId(), allowedPharmacies);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (request.getSourceLocationId() != null) {
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (StringUtils.isNotBlank(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        LocalDateTime rangeStart = request.getApplyTimeFrom();
        if (rangeStart == null) {
            rangeStart = request.getStartDate();
        }
        listQuery.setStartDate(rangeStart);
        LocalDateTime rangeEnd = request.getApplyTimeTo();
        if (rangeEnd == null) {
            rangeEnd = request.getEndDate();
        }
        listQuery.setEndDate(rangeEnd);
        try {
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<PharmacyStockInListRowDto> records = this.pharmacyStockInMapper.toPharmacyStockInListAggregated(busNos, lines);
            this.enrichPharmacyStockInListSourceLocationNames(records);
            this.enrichPharmacyStockInListStatusEnumText(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            return this.pharmacyStockInMapper.toPagePharmacyStockInsResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pagePharmacyStockIns failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_IN_102, "药房入库单列表查询失败");
        }
    }

    public GetPharmacyStockInDetailResponse getPharmacyStockInDetail(GetPharmacyStockInDetailRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_103, "药房入库单不存在");
        }
        this.assertPharmacyStockIn(lines);
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products;
        try {
            products = productDefinitionBatchLoader.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for pharmacy stock in detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
        Map<Long, ProductDefinitionCatalogEditorHintsDto> hints = new HashMap<>();
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            ProductDefinitionCatalogEditorHintsDto hint =
                ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
            if (hint != null) {
                hints.put(line.getId(), hint);
            }
        }
        GetPharmacyStockInDetailResponse res = pharmacyStockInMapper.toGetPharmacyStockInDetailResponse(lines, hints);
        // —— 与药库出库/进货单详情同构：写模型行仅含 item 引用，展示名/规格/厂家须从产品读模型回填。——
        this.enrichPharmacyStockInDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichPharmacyStockInDetailLines(res.getLines());
        return res;
    }

    public CreatePharmacyStockInResponse createPharmacyStockIn(CreatePharmacyStockInRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "入库明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "来源药库与目的药房不能为空");
        }
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        this.assertPharmacyStockInLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        this.assertPharmacyStockInTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.PHA_IN_001);
        if (StringUtils.isNotBlank(request.getOriginalBusNo())) {
            this.validateOriginalReferenceForPharmacyStockIn(request.getOriginalBusNo().trim());
        }
        this.assertAvailableQuantitiesAgainstSourceWarehouse(request.getLines(), request.getSourceLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "经手人不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.PHA_IN_101, "用户未登录");
        Long applicantId = this.requirePharmacyStockInApplicantPractitionerId(
            ErrorCodeConstants.PHA_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        LocalDateTime applyTime = LocalDateTime.now();
        try {
            String busNo = supplyRequestDomainService.createRequisition(
                pharmacyStockInMapper.toCreateRequisitionCommand(request, operatorId, applicantId, applyTime));
            return pharmacyStockInMapper.toCreatePharmacyStockInResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "药房入库参数不合法：" + ex.getMessage());
        }
    }

    public UpdatePharmacyStockInResponse updatePharmacyStockIn(UpdatePharmacyStockInRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "入库明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "来源药库与目的药房不能为空");
        }
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        this.assertPharmacyStockInLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_103, "药房入库单不存在");
        }
        SupplyRequestModel head = existing.get(0);
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_004, "当前状态不允许编辑药房入库单");
        }
        this.assertPharmacyStockInTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.PHA_IN_001);
        if (StringUtils.isNotBlank(request.getOriginalBusNo())) {
            this.validateOriginalReferenceForPharmacyStockIn(request.getOriginalBusNo().trim());
        }
        this.assertAvailableQuantitiesAgainstSourceWarehouse(request.getLines(), request.getSourceLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "经手人不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.PHA_IN_101, "用户未登录");
        Long applicantId = this.requirePharmacyStockInApplicantPractitionerId(
            ErrorCodeConstants.PHA_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        LocalDateTime applyTime = LocalDateTime.now();
        try {
            String busNo = supplyRequestDomainService.createRequisition(
                pharmacyStockInMapper.toCreateRequisitionCommand(request, operatorId, applicantId, applyTime));
            return pharmacyStockInMapper.toUpdatePharmacyStockInResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "药房入库参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_105, "药房入库单已被并发修改，请刷新后重试", ex);
        }
    }

    public SubmitPharmacyStockInApprovalResponse submitPharmacyStockInApproval(SubmitPharmacyStockInApprovalRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requirePharmacyStockInApplicantPractitionerId(
            ErrorCodeConstants.PHA_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            supplyRequestDomainService.submitApproval(
                pharmacyStockInMapper.toSubmitApprovalCommand(request,
                    currentOperatorResolver.requireOperatorId(ErrorCodeConstants.PHA_IN_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_IN_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return pharmacyStockInMapper.toSubmitPharmacyStockInApprovalResponse(st);
    }

    public WithdrawPharmacyStockInApprovalResponse withdrawPharmacyStockInApproval(WithdrawPharmacyStockInApprovalRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            supplyRequestDomainService.withdrawApproval(
                pharmacyStockInMapper.toWithdrawApprovalCommand(request,
                    currentOperatorResolver.requireOperatorId(ErrorCodeConstants.PHA_IN_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_IN_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return pharmacyStockInMapper.toWithdrawPharmacyStockInApprovalResponse(st);
    }

    /** 同意：领域已同意 → 发放 → 目的药房库存（增/建）→ 追溯；含防重复同意与完成态发放门禁。 */
    public AgreePharmacyStockInResponse agreePharmacyStockIn(AgreePharmacyStockInRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "busNo 不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.PHA_IN_101, "用户未登录");


        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_103, "药房入库单不存在");
        }
        this.assertPharmacyStockIn(beforeLines);
        for (SupplyRequestModel row : beforeLines) {
            if (row != null && row.getId() != null) {
                if (Boolean.TRUE.equals(supplyDeliveryQueryService.supplyDeliveryValidation(row.getId()).getHasCompletedDelivery())) {
                    throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "供应请求行已存在完成态发放，禁止重复同意入库");
                }
            }
        }

        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "药房入库单已同意，禁止重复同意");
        }

        try {
            supplyRequestDomainService.agreeRequest(pharmacyStockInMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_105, "药房入库单已被并发修改，请刷新后重试", ex);
        }

        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertPharmacyStockIn(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "同意后回读状态异常，中止入库编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "入库明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "入库明细数量无效，无法完成编排");
            }
        }

        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        Set<Long> duplicateCompletedRequestIds =
            supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "供应请求行已存在完成态发放，禁止重复同意入库");
        }

        try {
            supplyDeliveryDomainService.createSupplyDelivery(
                pharmacyStockInMapper.toCreateSupplyDeliveryCommandForPharmacyStockIn(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "药房入库发放落库失败：" + ex.getMessage());
        }

        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                productByKey = productDefinitionBatchLoader.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during pharmacy stock in inventory assemble", ex);
            }
        }

        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        String traceOperationType = TraceNumberRecordOperationType.supplyTypeCode(SupplyType.PHARMACY_STOCK_IN);
        List<AppendTraceRecordsFromLineItemsCommand.AppendLineItemCommand> traceItems = new ArrayList<>();

        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            String productName = this.resolveProductName(line, productByKey);
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.PHA_IN_104, "药房入库供应行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());

            InventoryMatchKey dstKey = new InventoryMatchKey();
            dstKey.setSupplierId(line.getSupplierId());
            dstKey.setLotNumber(resolvedLot);
            dstKey.setLocationId(purposeLocationId);
            dstKey.setItemTable(line.getItemTable());
            dstKey.setItemId(line.getItemId());
            Long destInventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(dstKey).orElse(null);
            try {
                if (destInventoryId != null) {
                    IncreaseQuantityCommand incCmd =
                        inboundReceiptMapper.toIncreaseQuantityCommand(destInventoryId, splitQty, operatorId);
                    inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    CreateInventoryItemCommand createCmd = inboundReceiptMapper.toCreateInventoryItemCommand(
                        line, purposeLocationId, productName, resolvedLot, splitQty, splitUnitCode, operatorId, product);
                    inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "目的药房库存增加失败：" + ex.getMessage());
            }

            if (StringUtils.isNotBlank(line.getTraceNo())) {
                String lt = this.resolvePurposeLocationTypeCode(line, head);
                AppendTraceRecordsFromLineItemsCommand.AppendLineItemCommand it =
                    new AppendTraceRecordsFromLineItemsCommand.AppendLineItemCommand();
                it.setItemTable(line.getItemTable().trim());
                it.setItemId(line.getItemId());
                it.setLocationTypeEnum(lt);
                it.setLocationId(purposeLocationId);
                it.setLocationStoreId(null);
                it.setLotNumber(resolvedLot);
                it.setTraceNos(line.getTraceNo());
                it.setStatusEnum(TraceDirection.IN.getCode());
                it.setUnitCode(splitUnitCode);
                it.setOperationType(traceOperationType);
                it.setBusNo(busNoTrim);
                traceItems.add(it);
            }
        }

        if (!traceItems.isEmpty()) {
            try {
                AppendTraceRecordsFromLineItemsCommand traceCmd = new AppendTraceRecordsFromLineItemsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                traceNumberRecordDomainService.appendTraceRecordsFromLineItems(traceCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "追溯码入库记录失败：" + ex.getMessage());
            }
        }

        return pharmacyStockInMapper.toAgreePharmacyStockInResponse(busNoTrim, SupplyStatus.AGREE.getCode());
    }

    public RejectPharmacyStockInResponse rejectPharmacyStockIn(RejectPharmacyStockInRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "busNo 不能为空");
        }
        // 驳回原因码/说明均可空；有值时由 Mapper 合并写入领域命令。
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.PHA_IN_101, "用户未登录");
        try {
            supplyRequestDomainService.rejectRequest(pharmacyStockInMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_IN_003);
        }
        return pharmacyStockInMapper.toRejectPharmacyStockInResponse(true);
    }

    public RemovePharmacyStockInResponse removePharmacyStockIn(RemovePharmacyStockInRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.PHA_IN_101, "用户未登录");
        try {
            supplyRequestDomainService.removeByBusNo(pharmacyStockInMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_IN_005);
        }
        return pharmacyStockInMapper.toRemovePharmacyStockInResponse(true);
    }

    public PageProductsForPharmacyStockInResponse pageProductsForPharmacyStockIn(PageProductsForPharmacyStockInRequest request) {
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_IN_001, "分页参数不能为空");
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "sourceLocationId 不能为空");
        }
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(request.getSourceLocationId(), allowedWarehouses);
        this.assertProductTableNameWhitelistForPharmacySelection(request.getProductTableName());
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getSourceLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            Page<InventoryItemModel> page = inventoryItemQueryService.queryInventoryItemsByPage(invQuery, pageable);
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null || inv.getItemId() == null || StringUtils.isBlank(inv.getItemTable())) {
                    continue;
                }
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(inv.getItemId());
                lookup.setProductTableName(inv.getItemTable().trim());
                lookups.add(lookup);
            }
            Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
            if (!lookups.isEmpty()) {
                try {
                    products = productDefinitionBatchLoader.loadAsMap(lookups);
                } catch (RuntimeException ex) {
                    log.warn("batchGetProductByIds failed for pharmacy stock in selection", ex);
                }
            }
            String itemTable = request.getProductTableName().trim();
            List<Long> pageItemIds = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv != null && inv.getItemId() != null) {
                    pageItemIds.add(inv.getItemId());
                }
            }
            Map<Long, Map<String, BigDecimal>> availableQtyByItemIdAndNormLot = new HashMap<>();
            if (!pageItemIds.isEmpty()) {
                List<InventoryCatalogAvailabilityRowModel> availRows =
                    this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                        itemTable, pageItemIds, List.of(request.getSourceLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getSourceLocationId())) {
                        continue;
                    }
                    String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                    availableQtyByItemIdAndNormLot
                        .computeIfAbsent(ar.getItemId(), k -> new HashMap<>())
                        .merge(normLot, ar.getQuantity(), BigDecimal::add);
                }
            }
            ProductDefinitionSelectionTerminologyMaps terminologyMaps =
                this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
            List<ProductDefinitionItemDto> records = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(inv.getItemTable(), inv.getItemId());
                ProductDefinitionDisplayDto pr = products.get(key);
                ProductDefinitionItemDto dto = pharmacyStockInMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
                if (inv.getItemId() != null) {
                    String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(inv.getLotNumber());
                    Map<String, BigDecimal> byLot = availableQtyByItemIdAndNormLot.get(inv.getItemId());
                    BigDecimal avail = BigDecimal.ZERO;
                    if (byLot != null) {
                        BigDecimal v = byLot.get(normLot);
                        if (v != null) {
                            avail = v;
                        }
                    }
                    dto.setAvailableQuantity(avail);
                } else {
                    dto.setAvailableQuantity(BigDecimal.ZERO);
                }
                BigDecimal lotCostUnit = null;
                if (StringUtils.isNotBlank(inv.getLotNumber())) {
                    lotCostUnit = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                        inv.getItemTable(), inv.getItemId(), inv.getLotNumber().trim());
                }
                dto.setPurchaseUnitPrice(lotCostUnit);
                ProductDefinitionCatalogEditorHintsDto hints =
                    ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                if (hints != null) {
                    List<ProductDefinitionCatalogUnitBandDto> bands = hints.getUnitBands();
                    dto.setUnitBands(bands);
                    BigDecimal packageAnchor = lotCostUnit;
                    if (packageAnchor == null && StringUtils.isNotBlank(inv.getItemTable()) && inv.getItemId() != null) {
                        packageAnchor = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                            inv.getItemTable().trim(), inv.getItemId());
                    }
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, packageAnchor);
                }
                records.add(dto);
            }
            return PagedResultBuilder.build(new PageProductsForPharmacyStockInResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForPharmacyStockIn failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_IN_102, "药房入库选品查询失败");
        }
    }

    private void enrichPharmacyStockInListSourceLocationNames(List<PharmacyStockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 先收集本页全部来源库位 id，再批量查名称，避免列表行数次的 location 点查。——
        Set<Long> sourceIds = new HashSet<>();
        for (PharmacyStockInListRowDto row : rows) {
            if (row != null && row.getSourceLocationId() != null) {
                sourceIds.add(row.getSourceLocationId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long sid : sourceIds) {
            locationQueryService.findOptionalById(sid).ifPresent(loc -> {
                if (StringUtils.isNotBlank(loc.getName())) {
                    nameById.put(sid, loc.getName().trim());
                }
            });
        }
        // —— 回填展示列：未命中名称时保持 null，由前端决定是否展示占位。——
        for (PharmacyStockInListRowDto row : rows) {
            if (row == null || row.getSourceLocationId() == null) {
                continue;
            }
            row.setSourceLocationName(nameById.get(row.getSourceLocationId()));
        }
    }

    private void enrichPharmacyStockInListStatusEnumText(List<PharmacyStockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (PharmacyStockInListRowDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getStatusEnum())) {
                continue;
            }
            // —— 列表行 statusEnum 为持久化 code：仅解析成功时写中文，非法码保持 text 为空。——
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    /**
     * 药房入库详情：补齐项目名称、规格、厂家与行单位展示名（与药库出库 IF-WHS-SO-03 同构）。
     *
     * @param lines            详情明细行
     * @param products         产品读模型映射（表名+主键复合键）
     * @param terminologyMaps  选品术语（单位展示名等）
     */
    private void enrichPharmacyStockInDetailProductDisplay(
        List<PharmacyStockInDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (products == null || products.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        for (PharmacyStockInDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isBlank(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            // —— 壳单/手工单共用：前端表格「项目名称/规格/厂家」读这三列，不能只依赖 productCatalog。——
            dto.setProductName(pr.getProductName());
            dto.setSpecification(pr.getTotalVolume());
            dto.setManufacturer(pr.getManufacturer());
            dto.setUnitCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, dto.getUnitCode(), pr));
            ProductDefinitionCatalogEditorHintsDto catalog = dto.getProductCatalog();
            if (catalog != null && catalog.getUnitBands() != null && !catalog.getUnitBands().isEmpty()) {
                this.productDefinitionSelectionTerminologySupport.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 详情行槽价：目录 {@code PURCHASE}、批号 {@code LOT_NUMBER_COST} 原样落槽；{@code STOCK}/{@code SPLIT} 仅包装锚换算。
     *
     * @param lines 详情行列表，可为 null 或空
     */
    private void enrichPharmacyStockInDetailLines(List<PharmacyStockInDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (PharmacyStockInDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            // —— 已确保 productCatalog 非空：先补 PURCHASE 参考价，再按行价口径写槽单价。——
            BigDecimal purchase = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                dto.getItemTable(), dto.getItemId());
            cat.setPurchasePrice(purchase);
            List<ProductDefinitionCatalogUnitBandDto> bands = cat.getUnitBands();
            if (bands == null || bands.isEmpty()) {
                continue;
            }
            BigDecimal lotPackagePrice = null;
            if (StringUtils.isNotBlank(dto.getLotNumber())) {
                lotPackagePrice = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                    dto.getItemTable(), dto.getItemId(), dto.getLotNumber().trim());
            }
            ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseDetailLineBandPrices(
                bands, null, purchase, lotPackagePrice, dto.getPrice(), dto.getUnitCode());
        }
    }

    /**
     * 新建/编辑入口的位置校验：来源须为岗位可见药库，目的须为岗位可见药房。
     *
     * @param sourceLocationId  来源药库主键
     * @param purposeLocationId 目的药房主键
     */
    private void assertPharmacyStockInLocationGate(Long sourceLocationId, Long purposeLocationId) {
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(sourceLocationId, warehouses);
        Set<Long> pharmacies = this.resolveGatedPharmacyLocationIdsForPharmacyGate();
        this.assertPharmacyLocationAllowed(purposeLocationId, pharmacies);
    }

    private Set<Long> resolveGatedWarehouseLocationIdsForWarehouseGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private Set<Long> resolveGatedPharmacyLocationIdsForPharmacyGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActivePharmacies());
    }

    /**
     * 断言给定药库主键在允许集合内且为启用中的仓库形态。
     *
     * @param locationId 位置主键
     * @param allowed      岗位可见药库 id 集合
     */
    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.PHA_IN_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "来源位置须为启用中的药库");
        }
    }

    /**
     * 断言给定药房主键在允许集合内且为启用中的药房形态。
     *
     * @param locationId 位置主键
     * @param allowed      岗位可见药房 id 集合
     */
    private void assertPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.PHA_IN_001, "目的药房不在当前用户可选药房范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "目的药房不在当前用户可选药房范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "目的位置须为启用中的药房");
        }
    }

    private Long resolveOrganizationId(Long organizationId) {
        Long orgId = organizationId;
        if (orgId == null) {
            // —— 入参未带机构时回落到登录上下文，供下游读模型/域查询租户隔离。——
            orgId = currentUser.getCurrentOrganizationUnitId();
        }
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_101, "机构上下文缺失");
        }
        return orgId;
    }

    /**
     * 分页拉取指定业务角色下、属于某机构的在岗医务人员（用于初始化下拉）。
     *
     * @param businessRoleCode PractitionerBusinessRoleCode 数值
     * @param organizationId   机构主键
     * @return 人员列表，永不为 null
     */
    private List<Practitioner> loadAllPractitionersForRoleInOrganization(int businessRoleCode, Long organizationId) {
        if (organizationId == null) {
            return Collections.emptyList();
        }
        List<Practitioner> acc = new ArrayList<>();
        int pageIndex = 0;
        int pageSize = 500;
        // —— 机构下人员可能超单页：固定页宽拉全量再筛 orgId（初始化下拉用，非高频热路径）。——
        while (true) {
            Page<Practitioner> page = this.practitionerQueryService.pageActivePractitionersForPosition(
                businessRoleCode,
                "",
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.ASC, FieldNameConstants.ID)));
            if (!page.hasContent()) {
                break;
            }
            for (Practitioner p : page.getContent()) {
                if (p == null || p.getId() == null) {
                    continue;
                }
                if (organizationId.equals(p.getOrgId())) {
                    acc.add(p);
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageIndex++;
        }
        return acc;
    }

    /** 业务单号非空校验。 */
    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "busNo 不能为空");
        }
    }

    /**
     * 药房入库单申请人（{@code wkf_supply_request.applicant_id}）：取当前登录用户绑定医务人员主键；
     *
     * @param notLoggedInErrorCode   未登录或缺用户主键时错误码
     * @param notLoggedInMessage     未登录提示
     * @param noPractitionerMessage  已登录但未绑定医务人员时的提示
     * @return 非空医务人员主键
     * @throws BusinessException 无登录用户或未绑定医务人员
     */
    private Long requirePharmacyStockInApplicantPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        Long userId = this.currentOperatorResolver.requireOperatorId(notLoggedInErrorCode, notLoggedInMessage);
        Practitioner practitioner = this.practitionerQueryService.findByUserId(userId);
        if (practitioner == null) {
            throw new BusinessException(notLoggedInErrorCode, noPractitionerMessage);
        }
        if (practitioner.getId() == null) {
            throw new BusinessException(notLoggedInErrorCode, noPractitionerMessage);
        }
        return practitioner.getId();
    }

    /**
     * 写侧前置：单据须存在、类型正确，且非系统生成已同意壳单。
     *
     * @param busNo 业务单号
     */
    private void preAssertPharmacyStockInWritable(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        // —— 写前按 busNo 拉全行：头字段与明细共享 supply_request 投影，首行即头。——
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_103, "药房入库单不存在");
        }
        this.assertPharmacyStockIn(lines);
        this.assertNotSystemGeneratedAgreedPharmacyShell(lines);
    }

    /**
     * 断言多行共享同一 {@code PHARMACY_STOCK_IN} 类型（详情/列表读侧一致性）。
     *
     * @param lines 供应请求行
     */
    private void assertPharmacyStockIn(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_STOCK_IN.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_103, "单据类型非药房入库单");
            }
        }
    }

    /**
     * 系统由出库同意生成的已同意药房入库壳单，禁止再次驱动写接口。
     */
    private void assertNotSystemGeneratedAgreedPharmacyShell(List<SupplyRequestModel> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        SupplyRequestModel head = lines.get(0);
        if (!SupplyStatus.AGREE.getCode().equals(head.getStatusEnum())) {
            return;
        }
        if (StringUtils.isBlank(head.getOriginalBusNo())) {
            return;
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(head.getOriginalBusNo().trim());
        List<SupplyRequestModel> orig = supplyRequestQueryService.getSupplyByBusNo(q);
        if (orig.isEmpty()) {
            // —— 上游单号已不可解析：无法判定是否系统壳，放行（其它校验仍会兜底）。——
            return;
        }
        SupplyRequestModel o0 = orig.get(0);
        if (SupplyType.CABINET_STOCK_OUT.getCode().equals(o0.getTypeEnum())
            && SupplyStatus.AGREE.getCode().equals(o0.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_004, "系统生成的已同意药房入库单不可再编辑或驱动审批流");
        }
    }

    /**
     * 列表可选 type 收窄：若传入则须为药房入库类型持久化码。
     *
     * @param typeEnumCode 请求 typeEnum，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_001} 非空白但无法解析或不是药房入库类型
     */
    private void validateOptionalPharmacyStockInTypeEnum(String typeEnumCode) {
        if (StringUtils.isBlank(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "typeEnum 非法");
        }
        if (declared != SupplyType.PHARMACY_STOCK_IN) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "typeEnum 必须为药房入库单类型");
        }
    }

    /**
     * 手工填写 {@code originalBusNo} 时须能解析到已存在的上游业务单。
     *
     * @param originalBusNo 已 trim 的上游单号
     */
    private void validateOriginalReferenceForPharmacyStockIn(String originalBusNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> reqLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (reqLines.isEmpty()) {
            // —— 用户手填 originalBusNo 必须能落到真实供应单，否则无法建立业务追溯。——
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "关联上游业务单不存在");
        }
    }

    /**
     * 保存草稿前，断言「来源药库」上待入数量不超过<strong>可用库存</strong>。
     *
     * <p><strong>不包含的业务</strong>：药库进货等「只看单品总和」的路径仍走
     * {@link InventoryItemQueryService#findAvailableInventories} 等契约，本方法不负责。</p>
     *
     * <p><strong>数量口径</strong>：{@code item_quantity} 与 {@code wkf_inventory_item.quantity} 均为拆零（最小）单位；
     * {@code unit_code} 仅作文书记录，不参与与库存的比较（见 {@link SupplyPersistedSplitQuantity}）。</p>
     *
     * <p><strong>两阶段可用量校验（与药库出库同构）</strong>：</p>
     * <ul>
     *   <li><strong>阶段 A</strong>：行上带 {@code inventoryItemId} 时，按库存主键批量拉可用行，逐键比对请求量与行上可用量，
     *   并核对库位、物品表、物品 id；若明细填了批号，还须与该行归一化批号一致（防前端串行）。</li>
     *   <li><strong>阶段 B</strong>：未带库存主键时，按物品表分组，在内存用「物品 + 归一化批号」聚合同品同批的多行可用量后再比；
     *   若明细未填批号（归一化后空串），则与该物品在来源库<strong>全部可用批</strong>的数量总和比较。</li>
     * </ul>
     *
     * @param lines             HTTP 明细；null/空由调用方保证已前置校验时可短路
     * @param sourceLocationId  用户选定的来源药库
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_001} 字段缺失、表类型非法、数量非正，或可用量不足
     */
    private void assertAvailableQuantitiesAgainstSourceWarehouse(List<PharmacyStockInLineDto> lines, Long sourceLocationId) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);

        // —— 阶段 A：带 inventoryItemId 的行 —— 按 wkf_inventory_item.id 精确门禁，避免仅靠批号字符串聚合时算错可用量。
        Map<Long, BigDecimal> requestedByInventoryItemId = new LinkedHashMap<>();
        Map<Long, PharmacyStockInLineDto> sampleLineByInventoryItemId = new LinkedHashMap<>();
        for (PharmacyStockInLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "入库明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "入库明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.PHA_IN_001, "明细数量须为正数");
            Long invId = line.getInventoryItemId();
            // —— 同一库存主键在明细多行出现时：持久化前按拆零数量求和，再与单行可用量比。——
            requestedByInventoryItemId.merge(invId, splitQty, BigDecimal::add);
            if (!sampleLineByInventoryItemId.containsKey(invId)) {
                sampleLineByInventoryItemId.put(invId, line);
            }
        }
        if (!requestedByInventoryItemId.isEmpty()) {
            List<Long> invIds = new ArrayList<>(requestedByInventoryItemId.keySet());
            List<InventoryCatalogAvailabilityRowModel> rowsById =
                this.inventoryItemQueryService.findAvailableCatalogRowsForInventoryItemIds(invIds, locationIds);
            Map<Long, InventoryCatalogAvailabilityRowModel> rowByInventoryItemId = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel row : rowsById) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                rowByInventoryItemId.put(row.getId(), row);
            }
            for (Map.Entry<Long, BigDecimal> want : requestedByInventoryItemId.entrySet()) {
                Long invId = want.getKey();
                BigDecimal requested = want.getValue();
                // —— 可用投影按 id 命中：查不到表示该行已非可用口径（锁定/过期/状态等），与前端选品行脱节。——
                InventoryCatalogAvailabilityRowModel row = rowByInventoryItemId.get(invId);
                PharmacyStockInLineDto sample = sampleLineByInventoryItemId.get(invId);
                if (row == null || sample == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_IN_001,
                        "所选库存行当前不可用、已锁定或已过期，请刷新选品后再试");
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "所选库存行与当前来源药库不一致");
                }
                String rowTable = "";
                if (row.getItemTable() != null) {
                    rowTable = row.getItemTable().trim();
                }
                if (!rowTable.equals(sample.getItemTable().trim())) {
                    throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "所选库存行物品表与明细不一致");
                }
                if (!Objects.equals(row.getItemId(), sample.getItemId())) {
                    throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "所选库存行物品主键与明细不一致");
                }
                String lineLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(sample.getLotNumber());
                if (!lineLotKey.isEmpty()) {
                    // —— 明细声明了批号：必须与所选库存行归一化批号一致，防止串批提交。——
                    String rowLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                    if (!lineLotKey.equals(rowLotKey)) {
                        throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "明细批号与所选库存行批号不一致");
                    }
                }
                BigDecimal available = row.getQuantity();
                if (available == null) {
                    available = BigDecimal.ZERO;
                }
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_IN_001,
                        "来源药库该库存行可用量不足，请减少入库数量或刷新库存后再试");
                }
            }
        }

        // —— 阶段 B：未带 inventoryItemId 的行 —— 在单库位 + 单物品表下按「物品 id → 归一化批号」二维 Map 聚合，与 DM 四列唯一（库位+表+品+批）一致。
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (PharmacyStockInLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() != null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "入库明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "入库明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.PHA_IN_001, "明细数量须为正数");
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            if (lotKeyPart.isEmpty()) {
                Map<Long, BigDecimal> perItem =
                    requestedWithoutLotByTable.computeIfAbsent(table, t -> new HashMap<>());
                perItem.merge(itemId, splitQty, BigDecimal::add);
            } else {
                requestedByLotByTable
                    .computeIfAbsent(table, t -> new HashMap<>())
                    .computeIfAbsent(itemId, i -> new HashMap<>())
                    .merge(lotKeyPart, splitQty, BigDecimal::add);
            }
        }
        Set<String> itemTables = new LinkedHashSet<>();
        itemTables.addAll(requestedByLotByTable.keySet());
        itemTables.addAll(requestedWithoutLotByTable.keySet());
        for (String itemTable : itemTables) {
            // 每张物品表单独一批 IN 查询：表名是库存行的物理分面，不可混在单次谓词里跨表 OR。
            Map<Long, Map<String, BigDecimal>> wantedByItemLot =
                requestedByLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Map<Long, BigDecimal> wantedWithoutLot = requestedWithoutLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Set<Long> itemIds = new LinkedHashSet<>();
            itemIds.addAll(wantedByItemLot.keySet());
            itemIds.addAll(wantedWithoutLot.keySet());
            if (itemIds.isEmpty()) {
                continue;
            }
            List<InventoryCatalogAvailabilityRowModel> catalogRows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    itemTable, new ArrayList<>(itemIds), locationIds);
            Map<Long, Map<String, BigDecimal>> availableByItemLot = new HashMap<>();
            Map<Long, BigDecimal> availableByItemId = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel crow : catalogRows) {
                if (crow == null || crow.getItemId() == null || crow.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(crow.getLocationId(), sourceLocationId)) {
                    continue;
                }
                Long crowItemId = crow.getItemId();
                String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(crow.getLotNumber());
                availableByItemLot
                    .computeIfAbsent(crowItemId, k -> new HashMap<>())
                    .merge(normLot, crow.getQuantity(), BigDecimal::add);
                // 无批号维的请求只关心「该品在库内全部可用行的总和」，故并行维护按 itemId 的纵向累计。
                availableByItemId.merge(crowItemId, crow.getQuantity(), BigDecimal::add);
            }
            // —— 有归一化批号键的请求：按「品 + 批」与可用目录逐键比较。——
            for (Map.Entry<Long, Map<String, BigDecimal>> wantItem : wantedByItemLot.entrySet()) {
                Long itemId = wantItem.getKey();
                Map<String, BigDecimal> wantedLots = wantItem.getValue();
                Map<String, BigDecimal> availLots = availableByItemLot.getOrDefault(itemId, Collections.emptyMap());
                for (Map.Entry<String, BigDecimal> wantLot : wantedLots.entrySet()) {
                    BigDecimal requested = wantLot.getValue();
                    BigDecimal available = availLots.getOrDefault(wantLot.getKey(), BigDecimal.ZERO);
                    if (requested.compareTo(available) > 0) {
                        throw new BusinessException(
                            ErrorCodeConstants.PHA_IN_001,
                            "明细数量超过来源药库该批号可用量，请减少数量或刷新库存后再试");
                    }
                }
            }
            // —— 无批号键：只与「该品在来源库全部可用批」纵向和比较（多批可混扣）。——
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_IN_001,
                        "明细数量超过来源药库可用量，请减少数量或刷新库存后再试");
                }
            }
        }
    }

    /**
     * 追溯码宽字符上限校验（逗号分隔多码），与进货/出库同口径。
     *
     * @param lines      明细行
     * @param errorCode  失败时使用的错误码常量
     */
    private void assertPharmacyStockInTraceNosWithinWideLimit(List<PharmacyStockInLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (PharmacyStockInLineDto line : lines) {
            if (line == null || StringUtils.isBlank(line.getTraceNo())) {
                continue;
            }
            // —— 整段串上限 + 逗号分片每段上限：与进货/出库追溯码宽表一致。——
            String raw = line.getTraceNo();
            if (raw.length() > maxLen) {
                throw new BusinessException(errorCode, "追溯码长度超过允许上限（" + maxLen + "）");
            }
            for (String part : raw.split(GeneralSharedConstants.COMMA)) {
                String t = part.trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (t.length() > maxLen) {
                    throw new BusinessException(errorCode, "追溯码片段长度超过允许上限（" + maxLen + "）");
                }
            }
        }
    }

    /**
     * 选品接口物品表白名单门控（药品定义表 / 设备定义表）。
     *
     * @param raw 请求 productTableName
     */
    private void assertProductTableNameWhitelistForPharmacySelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "productTableName 非法");
        }
    }

    /**
     * 读头行状态枚举（提交/撤回等接口出参用）。
     *
     * @param busNo 业务单号
     * @return 头行状态；单据不存在时 null
     */
    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, lines.get(0).getStatusEnum());
    }

    /**
     * 库存匹配用批号归一化：空白视为空串以与库存键一致。
     *
     * @param lotNumber 原始批号
     * @return 非 null 的批号串
     */
    private String resolveLotNumberForInventory(String lotNumber) {
        if (StringUtils.isBlank(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    /**
     * 从批量产品读模型中解析行展示名称（缺产品或名称时视为编排失败）。
     *
     * @param line          供应请求行
     * @param productByKey  复合键 → 产品读模型
     * @return 非空展示名
     */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isBlank(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isBlank(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    /**
     * 解析追溯写入所需的目的位置类型持久化码：行枚举优先，头枚举次之；皆空时按行、头顺序取 {@code purposeLocationId} 经
     * {@link LocationQueryService#requireSupplyRequestTypeForLocationId(Long)} 推导；仍不可得时回落为药房请领类型
     * （{@link SupplyRequestType#PHARMACY}），兼容历史缺字段数据。
     *
     * @param line 明细行，可 null
     * @param head 头行，可 null
     * @return 非空类型码
     */
    private String resolvePurposeLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotBlank(line.getPurposeTypeEnum())) {
            return line.getPurposeTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotBlank(head.getPurposeTypeEnum())) {
            return head.getPurposeTypeEnum().trim();
        }
        Long purposeLocationId = null;
        if (line != null && line.getPurposeLocationId() != null) {
            purposeLocationId = line.getPurposeLocationId();
        } else if (head != null && head.getPurposeLocationId() != null) {
            purposeLocationId = head.getPurposeLocationId();
        }
        if (purposeLocationId != null) {
            return this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
        }
        return SupplyRequestType.PHARMACY.getCode();
    }

    /**
     * 将领域「非法参数」口径映射为应用层列表/审批类默认错误码。
     *
     * @param ex          领域业务异常
     * @param defaultCode 映射目标错误码
     * @return 映射后的异常或原样返回
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * 同意路径专用：非法参数中「已同意」语义提升为 PHA_IN_104，其余回落 PHA_IN_003。
     *
     * @param ex 领域业务异常
     * @return 映射后的异常或原样返回
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.PHA_IN_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.PHA_IN_003, msg);
        }
        return ex;
    }
}
