/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.stocktake.service;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.tracenumber.TraceNumberStocktakeGainLossTraceOrchestrator;
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
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
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
import com.openhis.health.application.inventory.support.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterStocktakeExtensionIn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestPricedWriteOrchestrator supplyRequestPricedWriteOrchestrator;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryWarehouseStocktakeMapper stocktakeMapper;
    /** 盘盈首次建库存项时复用进货侧命令装配。 */
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** IF-TRC-14 盘点剩余码差分写追溯。 */
    private final TraceNumberStocktakeGainLossTraceOrchestrator traceNumberStocktakeGainLossTraceOrchestrator;

    /**
     * 加载库房单据页主数据（管辖药库、枚举选项、制单人员等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
     * @throws BusinessException {@link ErrorCodeConstants#WHS_ST_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_ST_102} 主数据加载失败
     */
    public LoadStocktakeFormInitResponse loadStocktakeFormInit(LoadStocktakeFormInitRequest request) {
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.WHS_ST_101, "机构上下文缺失");
        try {
            // Gate：机构会话上下文
            // Pre：装载管辖范围内启用位置与角色人员
            // Asm：Mapper 映射页初始化响应
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
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

    /**
     * 分页查询库房单据列表（按 busNo 聚合展示）。
     *
     * @param request 含分页窗口、库房与状态筛选
     * @return 聚合后的列表行与分页元数据
     * @throws BusinessException {@link ErrorCodeConstants#WHS_ST_001} 分页/位置参数非法；
     *                            {@link ErrorCodeConstants#WHS_ST_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_ST_102} 列表查询失败
     */
    public PageStocktakesResponse pageStocktakes(PageStocktakesRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        // Pre：组装列表查询与库房门禁
        // Cmd：领域分页查询供应请求
        // Asm：聚合 busNo 行并映射分页响应
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_ST_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_ST_101, "机构上下文缺失");
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
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
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
            // Cmd：领域分页查询供应请求
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<StocktakeListRowDto> records = this.stocktakeMapper.toStocktakeListAggregated(busNos, lines);
            this.enrichStocktakeListDisplayNames(records, lines);
            this.enrichStocktakeListPurposeLocationNames(records);
            this.enrichStocktakeListStatusEnumText(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合 busNo 行并映射分页响应
            return this.stocktakeMapper.toPageStocktakesAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageStocktakes failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_ST_102, "盘库单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读单据详情（含明细行与展示增强）。
     *
     * @param request 含业务单号 busNo
     * @return 单头与明细
     * @throws BusinessException {@link ErrorCodeConstants#WHS_ST_001} busNo 缺失；
     *                            {@link ErrorCodeConstants#WHS_ST_103} 单据不存在或类型非药库盘库单
     */
    public GetStocktakeDetailResponse getStocktakeDetail(GetStocktakeDetailRequest request) {
        // Gate：busNo 非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        // Pre：按 busNo 回读供应请求行
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_103, "盘库单不存在");
        }
        this.assertCabinetStocktake(lines);
        // Pre：批量装载产品读模型
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products;
        try {
            products = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for stocktake detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
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
            if (locOpt.isPresent() && StringUtils.isNotEmpty(locOpt.get().getName())) {
                res.setPurposeLocationName(locOpt.get().getName().trim());
            }
        }
        this.enrichStocktakeDetailLines(res.getLines(), lines, products);
        this.enrichStocktakeDetailLinesCatalogPricing(res.getLines());
        // Asm：Mapper 映射详情并增强展示字段
        return res;
    }

    /**
     * 新建库房单据草稿并落库供应请求。
     *
     * @param request 单头、明细与库房门禁字段
     * @return 新建后的 busNo
     */
    public CreateStocktakeResponse createStocktake(CreateStocktakeRequest request) {
        // Gate：请求体、明细与库房/供应商门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_ST_101, "机构上下文缺失");
        this.assertStocktakePurposeLocationGate(request.getPurposeLocationId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "经手人不能为空");
        }
        this.assertStocktakeLinesFields(request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            String busNo = supplyRequestPricedWriteOrchestrator.createStocktake(
                stocktakeMapper.toCreateStocktakeCommand(request, operatorId, applicantId, null));
            // Asm：返回 busNo
            return stocktakeMapper.toCreateStocktakeResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            throw this.mapStocktakeLocationException(ex);
        }
    }

    /**
     * 更新可编辑态库房单据（全量明细覆盖）。
     *
     * @param request 含 busNo、明细与乐观锁相关字段
     * @return 更新后的 busNo
     */
    public UpdateStocktakeResponse updateStocktake(UpdateStocktakeRequest request) {
        // Gate：busNo、明细与可编辑态校验
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_ST_101, "机构上下文缺失");
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
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            String busNo = supplyRequestPricedWriteOrchestrator.createStocktake(
                stocktakeMapper.toCreateStocktakeCommand(request, operatorId, applicantId));
            // Asm：返回 busNo
            return stocktakeMapper.toUpdateStocktakeResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_105, "盘库单已被并发修改，请刷新后重试", ex);
        } catch (BusinessException ex) {
            throw this.mapStocktakeLocationException(ex);
        }
    }

    /**
     * 提交单据进入待审批态。
     *
     * @param request 含业务单号 busNo
     * @return 提交后的展示状态
     */
    public SubmitStocktakeApprovalResponse submitStocktakeApproval(SubmitStocktakeApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_ST_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(
                stocktakeMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_003);
        }
        // Asm：映射提交响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return stocktakeMapper.toSubmitStocktakeApprovalResponse(st);
    }

    /**
     * 撤回审批（回到可编辑草稿态）。
     *
     * @param request 含业务单号 busNo
     * @return 撤回后的展示状态
     */
    public WithdrawStocktakeApprovalResponse withdrawStocktakeApproval(WithdrawStocktakeApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(
                stocktakeMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_003);
        }
        // Asm：映射撤回响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return stocktakeMapper.toWithdrawStocktakeApprovalResponse(st);
    }

    /**
     * 药库盘库单同意编排：领域落已同意态 → 盘点发放 → 按盈亏行增/减库存 → 可选医保进销存上报。
     *
     * @param request 含 busNo；须未重复同意
     * @return 同意后 busNo 与状态 code
     */
    public AgreeStocktakeResponse agreeStocktake(AgreeStocktakeRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");

        // Pre：回读盘库单并校验未重复同意
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
        // Cmd：领域状态机同意盘库单
        try {
            supplyRequestDomainService.agreeRequest(stocktakeMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_105, "盘库单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
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
        // Cmd：盘点发放落库
        try {
            supplyDeliveryDomainService.createStocktakingDelivery(
                stocktakeMapper.toCreateStocktakingDeliveryCommand(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "盘点发放落库失败：" + ex.getMessage());
        }

        // Pre：批量产品读模型，供拆零量与盘盈建库存行使用
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
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
                productByKey = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during stocktake inventory assemble", ex);
            }
        }
        // Cmd：按盈亏行增减库存
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
            String resolvedLot = StringUtils.isEmpty(line.getLotNumber())
                ? GeneralSharedConstants.EMPTY_STRING
                : line.getLotNumber().trim();
            InventoryMatchKey matchKey = new InventoryMatchKey();
            matchKey.setLotNumber(resolvedLot);
            matchKey.setLocationId(purposeLocationId);
            matchKey.setItemTable(line.getItemTable());
            matchKey.setItemId(line.getItemId());
            Long inventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(matchKey).orElse(null);
            int sign = profitLossQty.compareTo(BigDecimal.ZERO);
            // 盘盈：命中既有库存行则加量，否则新建库存项
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
                // 盘亏：须命中库存行后按绝对量扣减
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
        // Cmd：IF-TRC-14 剩余码与本库账差分写追溯主子表（库存入账成功后、同事务）
        traceNumberStocktakeGainLossTraceOrchestrator.recordFromAgreedLines(
            agreedLines, operatorId, productByKey, ErrorCodeConstants.WHS_ST_104);
        // Asm：可选医保进销存上报后映射同意响应
        tryUploadMedicareInventoryAfterStocktakeAgree(request, agreedLines);
        return stocktakeMapper.toAgreeStocktakeResponse(
            request.getBusNo().trim(), SupplyStatus.AGREE.getCode());
    }

    /**
     * 盘点 agree 后可选触发医保进销存上报（Extension 开关控制）。
     *
     * @param request     agree 请求
     * @param agreedLines 已同意供应行
     */
    private void tryUploadMedicareInventoryAfterStocktakeAgree(
        AgreeStocktakeRequest request,
        List<SupplyRequestModel> agreedLines
    ) {
        List<Long> lineIds = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line != null && line.getId() != null) {
                lineIds.add(line.getId());
            }
        }
        if (lineIds.isEmpty()) {
            return;
        }
        inventoryUploadExtension.runUploadIfEnabled(() -> {
            InventoryUploadAfterStocktakeExtensionIn command = new InventoryUploadAfterStocktakeExtensionIn()
                .setStocktakeBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setSupplyTypeCode(SupplyType.CABINET_STOCKTAKE.getCode())
                .setContractBusNo(request.getContractBusNo());
            inventoryUploadExtension.uploadAfterStocktakeAgree(command);
        });
    }

    /**
     * 驳回单据并记录原因。
     *
     * @param request 含 busNo 与驳回原因
     * @return 是否驳回成功
     */
    public RejectStocktakeResponse rejectStocktake(RejectStocktakeRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getChangeReason()) && StringUtils.isEmpty(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "驳回原因不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        try {
            // Cmd：领域驳回请求
            supplyRequestDomainService.rejectRequest(stocktakeMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_003);
        }
        // Asm：映射驳回响应
        return stocktakeMapper.toRejectStocktakeResponse(true);
    }

    /**
     * 删除单据（仅允许草稿/可删态）。
     *
     * @param request 含业务单号 busNo
     * @return 是否删除成功
     */
    public RemoveStocktakeResponse removeStocktake(RemoveStocktakeRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_ST_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删
            supplyRequestDomainService.removeByBusNo(stocktakeMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_ST_005);
        }
        // Asm：映射删除响应
        return stocktakeMapper.toRemoveStocktakeResponse(true);
    }

    /**
     * 分页查询单据选品（库存目录可用量）。
     *
     * @param request 含库房、物品表与分页参数
     * @return 选品行与分页元数据
     */
    public PageProductsForStocktakeResponse pageProductsForStocktake(PageProductsForStocktakeRequest request) {
        // Gate：分页窗口、机构与库房门禁
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_ST_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_ST_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForStocktakeSelection(request.getProductTableName());
        // Pre：组装库存目录分页查询
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getPurposeLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            // Cmd：查询可用库存行
            Page<InventoryItemModel> page = inventoryItemQueryService.queryInventoryItemsByPage(invQuery, pageable);
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null || inv.getItemId() == null || StringUtils.isEmpty(inv.getItemTable())) {
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
                    products = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
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
                this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
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
                if (StringUtils.isNotEmpty(inv.getLotNumber())) {
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
                    if (packageAnchor == null && StringUtils.isNotEmpty(inv.getItemTable()) && inv.getItemId() != null) {
                        packageAnchor = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                            inv.getItemTable().trim(), inv.getItemId());
                    }
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, packageAnchor);
                }
                records.add(dto);
            }
            // Asm：映射选品分页并增强价格
            return PagedResultBuilder.build(new PageProductsForStocktakeResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForStocktake failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_ST_102, "盘点选品查询失败");
        }
    }

    /**
     * 列表行回填 purposeLocationName。
     *
     * @param rows 盘点列表行
     */
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
            if (StringUtils.isNotEmpty(loc.getName())) {
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

    /**
     * 列表行 statusEnum → statusEnumText 展示文案。
     *
     * @param rows 盘点列表行
     */
    private void enrichStocktakeListStatusEnumText(List<StocktakeListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getStatusEnum())) {
                continue;
            }
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    /**
     * 列表行回填 applicantName/approverName（按 busNo 聚合单头）。
     *
     * @param rows        列表行
     * @param detailLines 同页明细供应行
     */
    private void enrichStocktakeListDisplayNames(List<StocktakeListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty() || detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
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
            if (pr != null && StringUtils.isNotEmpty(pr.getName())) {
                practitionerNameById.put(pid, pr.getName());
            }
        }
        for (StocktakeListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
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

    /**
     * 详情行 enrich 产品展示字段与账面数量 bookQuantity。
     *
     * @param detailLines 详情 DTO 行
     * @param supplyLines 供应明细
     * @param products    compositeKey → 产品读模型
     */
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
            if (purposeLocationId == null || StringUtils.isEmpty(src.getItemTable()) || src.getItemId() == null) {
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

    /**
     * 详情行 catalog 单位带价格 enrich（持久化供应行价回填）。
     *
     * @param lines 详情 DTO 行
     */
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
            ProductDefinitionCatalogUnitBandsAssembler.enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice(
                cat, dto.getPrice(), dto.getUnitCode(), null);
        }
    }

    /**
     * 盘点目的药库 location 门禁校验。
     *
     * @param purposeLocationId 目的药库 location
     * @throws BusinessException WHS_ST_002
     */
    private void assertStocktakePurposeLocationGate(Long purposeLocationId) {
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(purposeLocationId, warehouses);
    }

    /**
     * 当前用户可操作的药库 location 门禁集合。
     *
     * @return gated 药库 location id 集合
     */
    private Set<Long> resolveGatedWarehouseLocationIdsForWarehouseGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 断言 location 在当前用户药库管辖且为启用药库形态。
     *
     * @param locationId 库位 id
     * @param allowed    门禁集合
     * @throws BusinessException WHS_ST_002
     */
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

    /**
     * 解析当前登录用户对应医务人员 id（制单人 Gate）。
     *
     * @param notLoggedInErrorCode  未登录错误码
     * @param notLoggedInMessage    未登录文案（保留签名兼容）
     * @param noPractitionerMessage 无医务人员文案
     * @return practitionerId
     * @throws BusinessException 未登录或无医务人员
     */
    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 盘点明细字段完整性校验（物品/单位/盈亏/实盘/制单时间/表白名单）。
     *
     * @param lines 盘点明细
     * @throws BusinessException WHS_ST_001
     */
    private void assertStocktakeLinesFields(List<StocktakeLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (StocktakeLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "盘点明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
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

    /**
     * busNo 非空 Gate。
     *
     * @param busNo 供应单号
     * @throws BusinessException busNo 为空
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "busNo 不能为空");
        }
    }

    /**
     * 按 busNo 预加载并断言为药库盘库单（存在性 + 类型）。
     *
     * @param busNo 盘库单号
     * @throws BusinessException WHS_ST_103
     */
    private void preAssertCabinetStocktake(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_103, "盘库单不存在");
        }
        this.assertCabinetStocktake(lines);
    }

    /**
     * 断言供应行均为药库盘库单类型（CABINET_STOCKTAKE）。
     *
     * @param lines 供应行
     * @throws BusinessException WHS_ST_103
     */
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

    /**
     * 按 busNo 加载单头供应状态枚举。
     *
     * @param busNo 供应单号
     * @return SupplyStatus；不存在或无状态则 null
     */
    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        String statusCode = lines.get(0).getStatusEnum();
        if (StringUtils.isEmpty(statusCode)) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, statusCode.trim());
    }

    /**
     * 选品 productTableName 白名单校验（药品/耗材定义表）。
     *
     * @param raw 请求 productTableName
     * @throws BusinessException WHS_ST_001
     */
    private void assertProductTableNameWhitelistForStocktakeSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_001, "productTableName 非法");
        }
    }

    /**
     * 从批量产品读模型解析供应行展示名称。
     *
     * @param line          供应行
     * @param productByKey  compositeKey → 产品读模型
     * @return 产品名称
     * @throws BusinessException WHS_ST_104 产品缺失
     */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isEmpty(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    /**
     * 解析盘点行拆零单位：行 unitCode 优先，否则产品 splitUnitCode。
     *
     * @param line    供应行
     * @param product 产品读模型
     * @return 拆零单位 code
     * @throws BusinessException WHS_ST_104
     */
    private String resolveSplitUnitCode(SupplyRequestModel line, ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.WHS_ST_104, "产品定义缺失或缺少拆零单位编码");
        }
        if (line != null && StringUtils.isNotEmpty(line.getUnitCode())) {
            return line.getUnitCode().trim();
        }
        return product.getSplitUnitCode().trim();
    }

    /**
     * 供应工作流领域异常 → 本场景统一错误码（参数类映射 defaultCode）。
     *
     * @param ex          原异常
     * @param defaultCode 目标错误码
     * @return 映射后异常；其它原样返回
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * agree 阶段领域异常 → WHS_ST_104（重复 agree）或 WHS_ST_003。
     *
     * @param ex 原异常
     * @return 映射后异常
     */
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

    /**
     * 库位相关领域异常 → WHS_ST_002/001。
     *
     * @param ex 原异常
     * @return 映射后异常
     */
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
