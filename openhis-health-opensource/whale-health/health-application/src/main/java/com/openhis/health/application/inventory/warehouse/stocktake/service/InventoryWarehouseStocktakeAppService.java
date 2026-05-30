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

package com.openhis.health.application.inventory.warehouse.stocktake.service;

import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.openhis.health.application.common.pagination.PagedResultBuilder;
import com.openhis.health.application.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.common.validator.PageWindowGate;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.inventory.warehouse.stocktake.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.stocktake.dto.StocktakeDetailLineDto;
import com.openhis.health.application.inventory.warehouse.stocktake.dto.StocktakeLineDto;
import com.openhis.health.application.inventory.warehouse.stocktake.dto.StocktakeListRowDto;
import com.openhis.health.application.inventory.warehouse.stocktake.mapper.InventoryWarehouseStocktakeMapper;
import com.openhis.health.application.inventory.warehouse.stocktake.request.*;
import com.openhis.health.application.inventory.warehouse.stocktake.response.*;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
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
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.DecreaseQuantityCommand;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 药库盘库单（{@link SupplyType#CABINET_STOCKTAKE}）应用编排。
 *
 * <p>同意后按盘点差异：发放记录 + 在盘点库房对 {@link InventoryItemDomainService} 做盈亏加减（盘盈增加、盘亏扣减）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseStocktakeAppService {

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
    private final InventoryWarehouseStocktakeMapper stocktakeMapper;
    /** 盘盈首次建库存项时复用进货侧命令装配。 */
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    public LoadStocktakeFormInitResponse loadStocktakeFormInit(LoadStocktakeFormInitRequest request) {
        Long resolvedOrg = this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        try {
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.loadAllPractitionersForRoleInOrganization(
                PractitionerBusinessRoleCode.PHARMACIST.getNumericCode(), resolvedOrg);
            List<Practitioner> warehouseKeepers = this.loadAllPractitionersForRoleInOrganization(
                PractitionerBusinessRoleCode.WAREHOUSE_ADMIN.getNumericCode(), resolvedOrg);
            return stocktakeMapper.toLoadStocktakeFormInitResponse(
                stocktakeMapper.toOptionDtoListFromLocations(gatedWarehouses),
                stocktakeMapper.toOptionDtoListInboundItemTableTypes(),
                stocktakeMapper.toOptionDtoListSupplyStatuses(),
                stocktakeMapper.toOptionDtoListFromPractitioners(pharmacists),
                stocktakeMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadStocktakeFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_ST_101, "盘点页主数据加载失败");
        }
    }

    public PageStocktakesResponse pageStocktakes(PageStocktakesRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_ST_001, "分页参数不能为空");
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_STOCKTAKE.getCode());
        if (request.getPurposeLocationId() != null) {
            if (Long.valueOf(0L).equals(request.getPurposeLocationId())) {
                if (allowedWarehouses.isEmpty()) {
                    listQuery.setPurposeLocationIds(List.of(-1L));
                } else {
                    listQuery.setPurposeLocationIds(new ArrayList<>(allowedWarehouses));
                }
            } else {
                this.assertWarehouseLocationAllowed(request.getPurposeLocationId(), allowedWarehouses);
                listQuery.setPurposeLocationId(request.getPurposeLocationId());
            }
        }
        if (StringUtils.isNotBlank(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "statusEnum 非法");
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
            List<StocktakeListRowDto> records = this.stocktakeMapper.toStocktakeListAggregated(busNos, lines);
            this.enrichStocktakeListDisplayNames(records, lines);
            this.enrichStocktakeListPurposeLocationNames(records);
            this.enrichStocktakeListStatusEnumText(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            return this.stocktakeMapper.toPageStocktakesAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageStocktakes failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_ST_102, "盘库单列表查询失败");
        }
    }

    public GetStocktakeDetailResponse getStocktakeDetail(GetStocktakeDetailRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_103, "盘库单不存在");
        }
        this.assertCabinetStocktake(lines);
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
            log.warn("batchGetProductByIds degraded for stocktake detail", ex);
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
        GetStocktakeDetailResponse res = stocktakeMapper.toGetStocktakeDetailResponse(lines, hints);
        SupplyRequestModel head = lines.get(0);
        if (head.getPurposeLocationId() != null) {
            Optional<Location> locOpt = locationQueryService.findOptionalById(head.getPurposeLocationId());
            if (locOpt.isPresent() && StringUtils.isNotBlank(locOpt.get().getName())) {
                res.setPurposeLocationName(locOpt.get().getName().trim());
            }
        }
        this.enrichStocktakeDetailLines(res.getLines(), lines, products);
        this.enrichStocktakeDetailLinesCatalogPricing(res.getLines());
        return res;
    }

    public CreateStocktakeResponse createStocktake(CreateStocktakeRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "purposeLocationId 不能为空");
        }
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        this.assertStocktakePurposeLocationGate(request.getPurposeLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "经手人不能为空");
        }
        this.assertStocktakeLinesFields(request.getLines());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            String busNo = supplyRequestDomainService.createStocktake(
                stocktakeMapper.toCreateStocktakeCommand(request, operatorId, applicantId, null));
            return stocktakeMapper.toCreateStocktakeResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            throw this.mapStocktakeLocationException(ex);
        }
    }

    public UpdateStocktakeResponse updateStocktake(UpdateStocktakeRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "purposeLocationId 不能为空");
        }
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        this.assertStocktakePurposeLocationGate(request.getPurposeLocationId());
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_004, "当前状态不允许编辑盘库单");
        }
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "经手人不能为空");
        }
        this.assertStocktakeLinesFields(request.getLines());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            String busNo = supplyRequestDomainService.createStocktake(
                stocktakeMapper.toCreateStocktakeCommand(request, operatorId, applicantId));
            return stocktakeMapper.toUpdateStocktakeResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_105, "盘库单已被并发修改，请刷新后重试", ex);
        } catch (BusinessException ex) {
            throw this.mapStocktakeLocationException(ex);
        }
    }

    public SubmitStocktakeApprovalResponse submitStocktakeApproval(SubmitStocktakeApprovalRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            supplyRequestDomainService.submitApproval(
                stocktakeMapper.toSubmitApprovalCommand(request,
                    currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return stocktakeMapper.toSubmitStocktakeApprovalResponse(st);
    }

    public WithdrawStocktakeApprovalResponse withdrawStocktakeApproval(WithdrawStocktakeApprovalRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            supplyRequestDomainService.withdrawApproval(
                stocktakeMapper.toWithdrawApprovalCommand(request,
                    currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return stocktakeMapper.toWithdrawStocktakeApprovalResponse(st);
    }

    public AgreeStocktakeResponse agreeStocktake(AgreeStocktakeRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_103, "盘库单不存在");
        }
        this.assertCabinetStocktake(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "盘库单已同意，禁止重复同意");
        }
        try {
            supplyRequestDomainService.agreeRequest(stocktakeMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_105, "盘库单已被并发修改，请刷新后重试", ex);
        }
        this.supplyRequestDomainService.flushPendingChanges();
        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertCabinetStocktake(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "同意后回读状态异常，中止盘点编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "盘点明细缺少持久化主键，无法完成编排");
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
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "供应请求行已存在完成态发放，禁止重复同意盘点");
        }
        try {
            supplyDeliveryDomainService.createStocktakingDelivery(
                stocktakeMapper.toCreateStocktakingDeliveryCommand(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "盘点发放落库失败：" + ex.getMessage());
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
                log.warn("batchGetProductByIds failed during stocktake inventory assemble", ex);
            }
        }
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            // 仅非零盈亏行调整库存；零盈亏行在发放命令中已跳过。
            BigDecimal profitLossQty = line.getItemQuantity();
            if (profitLossQty == null || profitLossQty.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            String resolvedLot = this.resolveLotNumberForStocktakeInventory(line.getLotNumber());
            InventoryMatchKey matchKey = new InventoryMatchKey();
            matchKey.setLotNumber(resolvedLot);
            matchKey.setLocationId(purposeLocationId);
            matchKey.setItemTable(line.getItemTable());
            matchKey.setItemId(line.getItemId());
            Long inventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(matchKey).orElse(null);
            int sign = profitLossQty.compareTo(BigDecimal.ZERO);
            if (sign > 0) {
                try {
                    if (inventoryId != null) {
                        IncreaseQuantityCommand incCmd =
                            stocktakeMapper.toIncreaseQuantityCommand(inventoryId, profitLossQty, operatorId);
                        inventoryItemDomainService.increaseQuantity(incCmd);
                    } else {
                        String productName = this.resolveProductName(line, productByKey);
                        String key = ProductDefinitionKeyUtil.buildCompositeKey(
                            line.getItemTable(), line.getItemId());
                        ProductDefinitionDisplayDto product = productByKey.get(key);
                        String splitUnitCode = this.resolveSplitUnitCode(line, product);
                        CreateInventoryItemCommand createCmd = inboundReceiptMapper.toCreateInventoryItemCommand(
                            line,
                            purposeLocationId,
                            productName,
                            resolvedLot,
                            profitLossQty,
                            splitUnitCode,
                            operatorId,
                            product,
                            line.getSupplierId());
                        inventoryItemDomainService.createInventoryItem(createCmd);
                    }
                } catch (BusinessException ex) {
                    throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "盘盈库存增加失败：" + ex.getMessage());
                }
            } else {
                BigDecimal absQty = profitLossQty.abs();
                if (inventoryId == null) {
                    throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "盘点库房未找到匹配库存行，无法盘亏扣减");
                }
                try {
                    DecreaseQuantityCommand decCmd =
                        stocktakeMapper.toDecreaseQuantityCommand(inventoryId, absQty, true, operatorId);
                    inventoryItemDomainService.decreaseQuantity(decCmd);
                } catch (BusinessException ex) {
                    throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "盘亏库存扣减失败：" + ex.getMessage());
                }
            }
        }
        return stocktakeMapper.toAgreeStocktakeResponse(
            request.getBusNo().trim(), SupplyStatus.AGREE.getCode());
    }

    public RejectStocktakeResponse rejectStocktake(RejectStocktakeRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getChangeReason()) && StringUtils.isBlank(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "驳回原因不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        try {
            supplyRequestDomainService.rejectRequest(stocktakeMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_003);
        }
        return stocktakeMapper.toRejectStocktakeResponse(true);
    }

    public RemoveStocktakeResponse removeStocktake(RemoveStocktakeRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        try {
            supplyRequestDomainService.removeByBusNo(stocktakeMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_005);
        }
        return stocktakeMapper.toRemoveStocktakeResponse(true);
    }

    public PageProductsForStocktakeResponse pageProductsForStocktake(PageProductsForStocktakeRequest request) {
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_ST_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "purposeLocationId 不能为空");
        }
        this.resolveOrganizationId(this.currentUser.getCurrentOrganizationUnitId());
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForStocktakeSelection(request.getProductTableName());
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getPurposeLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
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
                    log.warn("batchGetProductByIds failed for stocktake selection", ex);
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
                        itemTable, pageItemIds, List.of(request.getPurposeLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getPurposeLocationId())) {
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
                ProductDefinitionItemDto dto = stocktakeMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
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
            return PagedResultBuilder.build(new PageProductsForStocktakeResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForStocktake failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_ST_102, "盘点选品查询失败");
        }
    }

    private void enrichStocktakeListPurposeLocationNames(List<StocktakeListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> purposeIds = new HashSet<>();
        for (StocktakeListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long pid : purposeIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(pid);
            if (locOpt.isEmpty()) {
                continue;
            }
            Location loc = locOpt.get();
            if (StringUtils.isNotBlank(loc.getName())) {
                nameById.put(pid, loc.getName().trim());
            }
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || row.getPurposeLocationId() == null) {
                continue;
            }
            row.setPurposeLocationName(nameById.get(row.getPurposeLocationId()));
        }
    }

    private void enrichStocktakeListStatusEnumText(List<StocktakeListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getStatusEnum())) {
                continue;
            }
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    private void enrichStocktakeListDisplayNames(List<StocktakeListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty() || detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isBlank(line.getBusNo())) {
                continue;
            }
            String bn = line.getBusNo().trim();
            SupplyRequestModel existing = headByBusNo.get(bn);
            if (existing == null) {
                headByBusNo.put(bn, line);
            } else {
                LocalDateTime existingApply = existing.getApplyTime();
                LocalDateTime lineApply = line.getApplyTime();
                if (lineApply != null) {
                    if (existingApply == null || lineApply.isAfter(existingApply)) {
                        headByBusNo.put(bn, line);
                    }
                }
            }
        }
        for (Map.Entry<String, SupplyRequestModel> e : headByBusNo.entrySet()) {
            SupplyRequestModel head = e.getValue();
            if (head == null) {
                continue;
            }
            String bn = e.getKey();
            if (head.getApplicantId() != null) {
                busNoToApplicantId.put(bn, head.getApplicantId());
            }
            if (head.getApproverId() != null) {
                busNoToApproverId.put(bn, head.getApproverId());
            }
        }
        Set<Long> practitionerIds = new HashSet<>();
        practitionerIds.addAll(busNoToApplicantId.values());
        practitionerIds.addAll(busNoToApproverId.values());
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long pid : practitionerIds) {
            if (pid == null) {
                continue;
            }
            Practitioner pr = this.practitionerQueryService.findById(pid);
            if (pr != null && StringUtils.isNotBlank(pr.getName())) {
                practitionerNameById.put(pid, pr.getName());
            }
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            Long aid = busNoToApplicantId.get(bn);
            if (aid != null) {
                String name = practitionerNameById.get(aid);
                if (name != null) {
                    row.setApplicantName(name);
                }
            }
            Long approverId = busNoToApproverId.get(bn);
            if (approverId != null) {
                String name = practitionerNameById.get(approverId);
                if (name != null) {
                    row.setApproverName(name);
                }
            }
        }
    }

    private void enrichStocktakeDetailLines(
        List<StocktakeDetailLineDto> detailLines,
        List<SupplyRequestModel> supplyLines,
        Map<String, ProductDefinitionDisplayDto> products) {
        if (detailLines == null || detailLines.isEmpty() || supplyLines == null) {
            return;
        }
        Map<Long, SupplyRequestModel> lineById = new HashMap<>();
        for (SupplyRequestModel line : supplyLines) {
            if (line != null && line.getId() != null) {
                lineById.put(line.getId(), line);
            }
        }
        Long purposeLocationId = null;
        if (!supplyLines.isEmpty() && supplyLines.get(0) != null) {
            purposeLocationId = supplyLines.get(0).getPurposeLocationId();
        }
        List<Long> locationIds = new ArrayList<>();
        if (purposeLocationId != null) {
            locationIds.add(purposeLocationId);
        }
        for (StocktakeDetailLineDto dto : detailLines) {
            if (dto == null || dto.getLineId() == null) {
                continue;
            }
            SupplyRequestModel src = lineById.get(dto.getLineId());
            if (src == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(src.getItemTable(), src.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr != null) {
                dto.setProductName(pr.getProductName());
                dto.setSpecification(pr.getTotalVolume());
                dto.setManufacturer(pr.getManufacturer());
            }
            if (purposeLocationId == null || StringUtils.isBlank(src.getItemTable()) || src.getItemId() == null) {
                dto.setBookQuantity(BigDecimal.ZERO);
                continue;
            }
            String itemTable = src.getItemTable().trim();
            List<Long> itemIds = List.of(src.getItemId());
            List<InventoryCatalogAvailabilityRowModel> availRows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(itemTable, itemIds, locationIds);
            String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(src.getLotNumber());
            BigDecimal bookQty = BigDecimal.ZERO;
            for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                if (ar == null || ar.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(ar.getLocationId(), purposeLocationId)) {
                    continue;
                }
                if (!Objects.equals(ar.getItemId(), src.getItemId())) {
                    continue;
                }
                String arLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                if (!normLot.equals(arLot)) {
                    continue;
                }
                bookQty = bookQty.add(ar.getQuantity());
            }
            dto.setBookQuantity(bookQty);
        }
    }

    private void enrichStocktakeDetailLinesCatalogPricing(List<StocktakeDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (StocktakeDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
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

    private void assertStocktakePurposeLocationGate(Long purposeLocationId) {
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(purposeLocationId, warehouses);
    }

    private Set<Long> resolveGatedWarehouseLocationIdsForWarehouseGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_ST_002, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_002, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_002, "盘点位置须为启用中的药库");
        }
    }

    private Long resolveOrganizationId(Long organizationId) {
        Long orgId = organizationId;
        if (orgId == null) {
            orgId = currentUser.getCurrentOrganizationUnitId();
        }
        if (orgId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_101, "机构上下文缺失");
        }
        return orgId;
    }

    private Long requireWarehousePreparerPractitionerId(
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

    private void assertStocktakeLinesFields(List<StocktakeLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (StocktakeLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细单位不能为空");
            }
            if (line.getProfitLossQuantity() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盈亏数量不能为空");
            }
            if (line.getActualQuantity() != null && line.getActualQuantity().compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "实盘数量不能为负");
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细制单时间不能为空");
            }
            String table = line.getItemTable().trim();
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "物品表非法");
            }
        }
    }

    private List<Practitioner> loadAllPractitionersForRoleInOrganization(int businessRoleCode, Long organizationId) {
        if (organizationId == null) {
            return Collections.emptyList();
        }
        List<Practitioner> acc = new ArrayList<>();
        int pageIndex = 0;
        int pageSize = 500;
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

    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
    }

    private void preAssertCabinetStocktake(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_103, "盘库单不存在");
        }
        this.assertCabinetStocktake(lines);
    }

    private void assertCabinetStocktake(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_STOCKTAKE.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_103, "单据类型非药库盘库单");
            }
        }
    }

    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        String statusCode = lines.get(0).getStatusEnum();
        if (StringUtils.isBlank(statusCode)) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, statusCode.trim());
    }

    private void assertProductTableNameWhitelistForStocktakeSelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "productTableName 非法");
        }
    }

    private String resolveLotNumberForStocktakeInventory(String lotNumber) {
        if (StringUtils.isBlank(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isBlank(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isBlank(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    private String resolveSplitUnitCode(SupplyRequestModel line, ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "产品定义缺失或缺少拆零单位编码");
        }
        if (line != null && StringUtils.isNotBlank(line.getUnitCode())) {
            return line.getUnitCode().trim();
        }
        return product.getSplitUnitCode().trim();
    }

    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.WHS_ST_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_ST_003, msg);
        }
        return ex;
    }

    private BusinessException mapStocktakeLocationException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("库房") || msg.contains("位置") || msg.contains("药库"))) {
                return new BusinessException(ErrorCodeConstants.WHS_ST_002, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_ST_001, msg);
        }
        return ex;
    }
}
