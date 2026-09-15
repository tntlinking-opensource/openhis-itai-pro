/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returntosupplier.service;

import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.LinkableGoodsReceiptListRowDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnableInboundLineDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierDetailLineDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierLineDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierListRowDto;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.support.ReturnToSupplierInboundLineHelper;
import com.openhis.health.application.inventory.warehouse.returntosupplier.support.ReturnToSupplierRemainingTracePrefiller;
import com.openhis.health.application.inventory.warehouse.returntosupplier.mapper.InventoryWarehouseReturnToSupplierMapper;
import com.openhis.health.application.inventory.warehouse.returntosupplier.request.*;
import com.openhis.health.application.inventory.warehouse.returntosupplier.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberSupplyAgreeTraceOrchestrator;
import com.openhis.health.application.shared.tracenumber.RecordTraceFromSupplyLineSpec;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnToSupplierCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByOriginalBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterPurchaseReturnExtensionIn;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 药库退货单（{@link SupplyType#CABINET_RETURN_TO_SUPPLIER}）应用编排（AS-INV-WHS-005）。
 *
 * <p>明细须从已同意进货单（{@code original_bus_no}）拉取，禁止自由选品；同一进货单可多次部分退货直至可退余量为 0。
 * 同意后：领域落已同意态 → 退货发放 → 源药库扣减（须源库仍有实物库存；已调拨至药房须先调回）→ 追溯码；可选触发医保 3504 上传。
 * IF-WHS-RT-13 追溯码预填为当前院内剩余（本库优先 / 全院回落），禁止回放进货扫码快照（BR-WHS-RT-008）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseReturnToSupplierAppService {

    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
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
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryWarehouseReturnToSupplierMapper returnToSupplierMapper;
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;
    private final ReturnToSupplierRemainingTracePrefiller returnToSupplierRemainingTracePrefiller;

    /**
     * 加载库房单据页主数据（管辖药库、枚举选项、制单人员等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_RT_102} 主数据加载失败
     */
    public LoadReturnToSupplierFormInitResponse loadReturnToSupplierFormInit(LoadReturnToSupplierFormInitRequest request) {
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
        try {
            // Gate：机构会话上下文
            // Pre：装载管辖范围内启用位置与角色人员
            // Asm：Mapper 映射页初始化响应
            List<Location> warehouses = this.locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel> suppliers =
                this.supplierQueryService.listSuppliersByTypeAndActive(
                    com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
            return this.returnToSupplierMapper.toLoadReturnToSupplierFormInitResponse(
                this.returnToSupplierMapper.toWarehouseOptionDtoList(gatedWarehouses),
                this.returnToSupplierMapper.toSupplierOptionDtoList(suppliers));
        } catch (RuntimeException ex) {
            log.warn("loadReturnToSupplierFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_RT_102, "退货页主数据加载失败");
        }
    }

    /**
     * 分页查询库房单据列表（按 busNo 聚合展示）。
     *
     * @param request 含分页窗口、库房与状态筛选
     * @return 聚合后的列表行与分页元数据
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 分页/位置参数非法；
     *                            {@link ErrorCodeConstants#WHS_RT_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_RT_102} 列表查询失败
     */
    public PageReturnToSuppliersResponse pageReturnToSuppliers(PageReturnToSuppliersRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "请求不能为空");
        }
        // Pre：组装列表查询与库房门禁
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_RT_001, "分页参数不能为空");
        this.validateOptionalReturnTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIds();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode());
        // Pre：sourceLocationId=0 表示当前用户可见的全部药库；否则单库且须在管辖门控内
        if (Long.valueOf(InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID)
            .equals(request.getSourceLocationId())) {
            if (allowed.isEmpty()) {
                // 无可见药库：占位 id 收窄为空页，避免无约束全表扫描
                listQuery.setSourceLocationIds(
                    List.of(InventoryWarehouseConstants.LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID));
            } else {
                // 多药库 IN 查询：下推当前用户可见 id 集合
                listQuery.setSourceLocationIds(new ArrayList<>(allowed));
            }
        } else {
            // 单药库筛选（防横向越权）
            this.assertLocationAllowed(request.getSourceLocationId(), allowed);
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (request.getStatusEnum() != null) {
            SupplyStatus st = EnumUtil.requireFromSortOrder(SupplyStatus.class, request.getStatusEnum().longValue());
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        listQuery.setStartDate(request.getApplyTimeFrom());
        listQuery.setEndDate(request.getApplyTimeTo());
        try {
            // Cmd：按 bus_no 聚合分页查询退货单
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<ReturnToSupplierListRowDto> records =
                this.returnToSupplierMapper.toReturnToSupplierListAggregated(busNos, lines);
            // Pre：侧栏卡片制单人/审核人/供应商展示名批量富化（与进货单 IF-WHS-IN-02 同构）
            this.enrichReturnToSupplierListDisplayNames(records, lines);
            // Pre：历史单未落价时按来源进货落库价回补列表合计（审核中单据不可再保存）
            this.enrichReturnToSupplierListAmountsFromInboundIfMissing(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：映射分页响应
            return this.returnToSupplierMapper.toPageReturnToSuppliersAggregatedResponse(
                mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageReturnToSuppliers failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_RT_102, "退货单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读单据详情（含明细行与展示增强）。
     *
     * @param request 含业务单号 busNo
     * @return 单头与明细
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} busNo 缺失；
     *                            {@link ErrorCodeConstants#WHS_RT_103} 单据不存在或类型不符
     */
    public GetReturnToSupplierDetailResponse getReturnToSupplierDetail(GetReturnToSupplierDetailRequest request) {
        // Gate：busNo 非空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        // Pre：按 busNo 回读供应请求行
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            // 单号不存在则终止，避免向 Mapper 传入空表误判
            throw new BusinessException(ErrorCodeConstants.WHS_RT_103, "退货单不存在");
        }
        this.assertCabinetReturnToSupplier(lines);

        // Pre：批量装载产品读模型，供明细展示名富化
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
            products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for return to supplier detail", ex);
            products = new HashMap<>();
        }
        // Asm：映射详情并增强展示字段
        GetReturnToSupplierDetailResponse response =
            this.returnToSupplierMapper.toGetReturnToSupplierDetailResponse(lines, products);
        // Asm：历史未落价行按进货比例回补单价/行额（只读，不写库）；槽价锚定落库价
        this.enrichReturnToSupplierDetailAmountsFromInboundIfMissing(response, lines);
        this.enrichReturnToSupplierDetailCatalogSlotPrices(response.getLines());
        this.refreshReturnToSupplierDetailHeaderTotals(response);
        return response;
    }

    /**
     * 新建药库退货单草稿（明细须来自关联进货单，IF-WHS-RT-04）。
     *
     * @param request 单头、明细与 {@code originalBusNo}（进货单号）
     * @return 新建后的 busNo
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 参数/可退余量/源库不足；
     *                            {@link ErrorCodeConstants#WHS_RT_101} 会话或制单人员缺失
     */
    public CreateReturnToSupplierResponse createReturnToSupplier(CreateReturnToSupplierRequest request) {
        // Gate：请求体、originalBusNo、明细与库房门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "请求不能为空");
        }
        if (StringUtils.isEmpty(request.getOriginalBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "originalBusNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIds();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        this.validateSupplierOptional(request.getSupplierId());
        this.assertReturnToSupplierTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_RT_001);
        // Pre：校验明细 ⊆ 进货行、可退余量（BR-SR-009）与源库可用量
        this.assertReturnLinesBoundToInboundAndWithinReturnable(
            request.getOriginalBusNo().trim(), request.getSourceLocationId(),
            this.toDraftReturnLinesFromReturnLineDtos(request.getLines()), null);
        this.assertReturnToSupplierDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnToSupplierLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            CreateReturnToSupplierCommand cmd = this.returnToSupplierMapper.toCreateReturnToSupplierCommand(
                request, operatorId, applicantId, null);
            // Pre：从来源进货行回填包装单价，供领域写价门禁重算行额（BR-PRICE-SRC-001）
            this.fillReturnToSupplierCommandPricesFromInbound(cmd);
            String busNo = this.supplyRequestDomainService.createReturnToSupplier(cmd);
            // Asm：返回 busNo
            return this.returnToSupplierMapper.toCreateReturnToSupplierResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 更新可编辑态药库退供应商单（全量明细覆盖，IF-WHS-RT-05）。
     *
     * @param request 含 busNo、明细；{@code originalBusNo} 由服务端从存量首行快照保留
     * @return 更新后的 busNo
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 参数/可退余量/源库不足；
     *                            {@link ErrorCodeConstants#WHS_RT_004} 不可编辑态；
     */
    public UpdateReturnToSupplierResponse updateReturnToSupplier(UpdateReturnToSupplierRequest request) {
        // Gate：busNo、明细与可编辑态校验
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIds();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            // 单号不存在则终止修订，避免误写空单
            throw new BusinessException(ErrorCodeConstants.WHS_RT_103, "退货单不存在");
        }
        this.assertCabinetReturnToSupplier(existing);
        SupplyRequestModel head = existing.get(0);
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = this.supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            // 审中/已同意等态禁止全量覆盖，防止绕过审批流改明细
            throw new BusinessException(ErrorCodeConstants.WHS_RT_004, "当前状态不允许编辑退货单");
        }

        // —— 修订须保留原进货引用快照：从存量首行读取，避免客户端篡改 originalBusNo。——
        String originalSnapshot = head.getOriginalBusNo();
        this.validateSupplierOptional(request.getSupplierId());
        this.assertReturnToSupplierTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_RT_004);
        this.assertReturnLinesBoundToInboundAndWithinReturnable(
            originalSnapshot, request.getSourceLocationId(),
            this.toDraftReturnLinesFromReturnLineDtos(request.getLines()), request.getBusNo().trim());
        this.assertReturnToSupplierDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnToSupplierLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：同号全量对齐修订（领域仍走 createReturnToSupplier 写路径）
            CreateReturnToSupplierCommand cmd = this.returnToSupplierMapper.toCreateReturnToSupplierCommandForUpdate(
                request, operatorId, applicantId, originalSnapshot);
            // Pre：从来源进货行回填包装单价，供领域写价门禁重算行额（BR-PRICE-SRC-001）
            this.fillReturnToSupplierCommandPricesFromInbound(cmd);
            String busNo = this.supplyRequestDomainService.createReturnToSupplier(cmd);
            // Asm：返回 busNo
            return this.returnToSupplierMapper.toUpdateReturnToSupplierResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_105, "退货单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交单据进入待审批态。
     *
     * @param request 含业务单号 busNo
     * @return 提交后的展示状态
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 请求或单号非法；
     *                            {@link ErrorCodeConstants#WHS_RT_003} 状态机不允许提交；
     *                            {@link ErrorCodeConstants#WHS_RT_101} 未登录或未绑定医务人员
     */
    public SubmitReturnToSupplierApprovalResponse submitReturnToSupplierApproval(
        SubmitReturnToSupplierApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批
            this.supplyRequestDomainService.submitApproval(
                this.returnToSupplierMapper.toSubmitApprovalCommand(
                    request,
                    this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_RT_003);
        }
        // Asm：映射提交响应
        return this.returnToSupplierMapper.toSubmitReturnToSupplierApprovalResponse();
    }

    /**
     * 撤回审批（回到可编辑草稿态）。
     *
     * @param request 含业务单号 busNo
     * @return 撤回后的展示状态
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 请求或单号非法；
     *                            {@link ErrorCodeConstants#WHS_RT_003} 状态机不允许撤回；
     *                            {@link ErrorCodeConstants#WHS_RT_101} 未登录
     */
    public WithdrawReturnToSupplierApprovalResponse withdrawReturnToSupplierApproval(
        WithdrawReturnToSupplierApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            this.supplyRequestDomainService.withdrawApproval(
                this.returnToSupplierMapper.toWithdrawApprovalCommand(
                    request,
                    this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_RT_003);
        }
        // Asm：映射撤回响应
        return this.returnToSupplierMapper.toWithdrawReturnToSupplierApprovalResponse();
    }

    /**
     * 药库退供应商单同意编排（IF-WHS-RT-08）：领域落已同意态 → 退货发放 → 源药库扣减 → 追溯出库。
     *
     * @param request 含 busNo；须未重复同意
     * @return 是否同意成功
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_104} 重复同意、可退余量/源库不足或扣库失败
     */
    public AgreeReturnToSupplierResponse agreeReturnToSupplier(AgreeReturnToSupplierRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录");

        // Pre：回读退供应商单并校验未重复同意
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_103, "退货单不存在");
        }
        this.assertCabinetReturnToSupplier(beforeLines);
        SupplyRequestValidationModel validation = this.supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "退货单已同意，禁止重复同意");
        }
        SupplyRequestModel agreeHead = beforeLines.get(0);
        // Pre：同意前再次校验可退余量（排除本单占用）与源库可用量，防草稿至同意间被抢库存
        this.assertReturnLinesBoundToInboundAndWithinReturnable(
            agreeHead.getOriginalBusNo(),
            agreeHead.getSourceLocationId(),
            this.toDraftReturnLinesFromSupplyModels(beforeLines),
            request.getBusNo().trim());
        this.assertReturnToSupplierDraftLinesWithinSourceAvailableInventory(
            agreeHead.getSourceLocationId(),
            this.toReturnLineDtosFromSupplyModels(beforeLines));

        // Cmd：领域状态机同意退供应商单
        try {
            this.supplyRequestDomainService.agreeRequest(
                this.returnToSupplierMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_105, "退货单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertCabinetReturnToSupplier(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "同意后回读状态异常，中止退货编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "退货明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "退货明细数量无效，无法完成编排");
            }
        }

        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        Set<Long> duplicateCompletedRequestIds =
            this.supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            // 发放幂等：已存在完成态发放则禁止重复同意，防止双扣库存
            throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "供应请求行已存在完成态发放，禁止重复同意退货");
        }

        // Cmd：退货发放落库
        try {
            this.supplyDeliveryDomainService.createSupplyDelivery(
                this.returnToSupplierMapper.toCreateSupplyDeliveryCommandForReturnToSupplier(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "退货发放落库失败：" + ex.getMessage());
        }

        // Pre：批量产品读模型，供拆零量与追溯出库使用
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
                productByKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during return to supplier inventory assemble", ex);
            }
        }

        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        List<RecordTraceFromSupplyLineSpec> traceCommands = new ArrayList<>();
        Map<Long, BigDecimal> decreaseMerged = new HashMap<>();

        // Cmd：组装源药库扣减与追溯出库项
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long sourceLocationId = line.getSourceLocationId();
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.WHS_RT_104, "供应请求行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());

            InventoryMatchKey srcKey = new InventoryMatchKey();
            srcKey.setSupplierId(line.getSupplierId());
            srcKey.setLotNumber(resolvedLot);
            srcKey.setLocationId(sourceLocationId);
            srcKey.setItemTable(line.getItemTable());
            srcKey.setItemId(line.getItemId());
            Long sourceInventoryId = this.inventoryItemQueryService.findFirstIdByInventoryMatchKey(srcKey).orElse(null);
            if (sourceInventoryId == null) {
                throw new BusinessException(
                    ErrorCodeConstants.WHS_RT_104,
                    "源库未找到匹配库存行，无法扣减；若货物已调拨至药房，须先调拨回进货药库后再同意退货");
            }
            decreaseMerged.merge(sourceInventoryId, splitQty, BigDecimal::add);

            if (StringUtils.isNotEmpty(line.getTraceNo())) {
                String lt = this.resolveSourceLocationTypeCode(line, head);
                RecordTraceFromSupplyLineSpec traceCmd = new RecordTraceFromSupplyLineSpec();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setSupplyTypeCode(SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode());
                traceCmd.setBusNo(busNoTrim);
                traceCmd.setItemTable(line.getItemTable().trim());
                traceCmd.setItemId(line.getItemId());
                traceCmd.setLotNumber(resolvedLot);
                traceCmd.setTraceNos(line.getTraceNo());
                traceCmd.setQuantity(splitQty);
                traceCmd.setUnitCode(splitUnitCode);
                traceCmd.setUnitConversionSource(ProductDefinitionDisplaySupport.toUnitConversionSource(product));
                traceCmd.setLocationId(sourceLocationId);
                traceCmd.setLocationTypeEnum(lt);
                traceCmd.setRecordDirectionEnum(TraceDirection.OUT.getCode());
                traceCommands.add(traceCmd);
            }
        }

        try {
            this.inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseMerged);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "源库库存扣减失败：" + ex.getMessage());
        }

        if (!traceCommands.isEmpty()) {
            try {
                this.traceNumberSupplyAgreeTraceOrchestrator.recordSingleEndLines(traceCommands);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "追溯码出库记录失败：" + ex.getMessage());
            }
        }

        this.tryUploadMedicareInventoryAfterReturnAgree(request, agreedLines);
        return this.returnToSupplierMapper.toAgreeReturnToSupplierResponse(true);
    }

    /**
     * 驳回单据并记录原因。
     *
     * @param request 含 busNo 与驳回原因
     * @return 是否驳回成功
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 单号或驳回原因缺失；
     *                            {@link ErrorCodeConstants#WHS_RT_003} 状态机不允许驳回；
     *                            {@link ErrorCodeConstants#WHS_RT_101} 未登录
     */
    public RejectReturnToSupplierResponse rejectReturnToSupplier(RejectReturnToSupplierRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "驳回原因不能为空");
        }
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录");
        try {
            this.supplyRequestDomainService.rejectRequest(
                this.returnToSupplierMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_RT_003);
        }
        // Asm：映射驳回响应
        return this.returnToSupplierMapper.toRejectReturnToSupplierResponse(true);
    }

    /**
     * 删除单据（仅允许草稿/可删态）。
     *
     * @param request 含业务单号 busNo
     * @return 是否删除成功
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 请求或单号非法；
     *                            {@link ErrorCodeConstants#WHS_RT_005} 当前状态不允许删除；
     *                            {@link ErrorCodeConstants#WHS_RT_101} 未登录
     */
    public RemoveReturnToSupplierResponse removeReturnToSupplier(RemoveReturnToSupplierRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删
            this.supplyRequestDomainService.removeByBusNo(
                this.returnToSupplierMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_RT_005);
        }
        // Asm：映射删除响应
        return this.returnToSupplierMapper.toRemoveReturnToSupplierResponse(true);
    }

    /**
     * 分页查询可关联进货单（已同意且仍有可退余量，IF-WHS-RT-12）。
     *
     * @param request 含 {@code sourceLocationId} 与分页参数
     * @return 按 {@code bus_no} 聚合的进货单候选行
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 参数或库位越权；
     *                            {@link ErrorCodeConstants#WHS_RT_102} 列表查询失败
     */
    public PageLinkableGoodsReceiptsResponse pageLinkableGoodsReceipts(PageLinkableGoodsReceiptsRequest request) {
        if (request == null || request.getSourceLocationId() == null) {
            // Gate：来源位置与分页窗口
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_RT_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIds();

        // Pre：组装已同意进货单聚合列表查询
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_INBOUND_RECEIPT.getCode());
        listQuery.setStatusEnum(SupplyStatus.AGREE.getCode());
        // Pre：sourceLocationId=0 表示当前用户可见的全部药库（与 pageReturnToSuppliers / 进货关联订货单同构）
        if (Long.valueOf(InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID)
            .equals(request.getSourceLocationId())) {
            if (allowed.isEmpty()) {
                listQuery.setPurposeLocationIds(
                    List.of(InventoryWarehouseConstants.LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowed));
            }
        } else {
            this.assertLocationAllowed(request.getSourceLocationId(), allowed);
            listQuery.setPurposeLocationId(request.getSourceLocationId());
        }
        PageQueryBinder.bind(request, listQuery);
        try {
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<LinkableGoodsReceiptListRowDto> filtered = new ArrayList<>();
            for (String busNo : busNos) {
                if (StringUtils.isEmpty(busNo)) {
                    continue;
                }
                LinkableGoodsReceiptListRowDto row = this.buildLinkableGoodsReceiptListRow(
                    busNo.trim(), lines, request.getSourceLocationId());
                if (row == null) {
                    continue;
                }
                if (!Boolean.TRUE.equals(row.getReturnable())) {
                    // 可退余量已为 0 的进货单不进入候选列表
                    continue;
                }
                filtered.add(row);
            }
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Pre：批量解析供应商/制单人/目的库展示名，供选单列表展示（对偶 IF-WHS-IN-12）
            this.enrichLinkableGoodsReceiptDisplayNames(filtered);
            // Asm：映射分页响应（total 仍按进货单去重计数；本页再筛可退）
            return this.returnToSupplierMapper.toPageLinkableGoodsReceiptsAggregatedResponse(
                mergedTotal, pageNo, pageSize, filtered);
        } catch (RuntimeException ex) {
            log.warn("pageLinkableGoodsReceipts failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_RT_102, "可关联进货单查询失败");
        }
    }

    /**
     * 按进货单号拉取退货草稿预填（IF-WHS-RT-13，对偶 IF-WHS-IN-13）。
     *
     * @param request 须含 {@code busNo}；可选 {@code sourceLocationId} 与进货目的库一致；改单时可传 {@code excludeReturnBusNo}
     * @return 退货表单预填（含可退余量、源库可用量；追溯码为当前院内剩余，见 BR-WHS-RT-008）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_001} 参数非法；
     *                            {@link ErrorCodeConstants#WHS_RT_002} 进货单不可引用
     */
    public GetLinkableGoodsReceiptDetailResponse getLinkableGoodsReceiptDetail(
        GetLinkableGoodsReceiptDetailRequest request) {
        // Gate：busNo 与机构/库房门禁
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIds();
        String busNoTrim = request.getBusNo().trim();
        this.validateOriginalInboundReceiptForReturn(busNoTrim, request.getSourceLocationId());

        List<SupplyRequestModel> inboundLines = this.loadInboundLinesByBusNo(busNoTrim);
        if (inboundLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源进货单不存在");
        }
        SupplyRequestModel head = inboundLines.get(0);
        Long inboundWarehouseId = head.getPurposeLocationId();
        if (inboundWarehouseId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "进货单缺少目的药库，无法生成退货预填");
        }
        this.assertLocationAllowed(inboundWarehouseId, allowed);
        if (request.getSourceLocationId() != null) {
            // —— 前端传入源药库时须与进货目的库一致，防止跨库错引。——
            if (!request.getSourceLocationId().equals(inboundWarehouseId)) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "进货单目的药库与请求不一致");
            }
            this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        }
        List<SupplyRequestModel> returnsReferencingInbound = this.loadReturnsReferencingInbound(busNoTrim);
        String excludeReturnBusNo = request.getExcludeReturnBusNo();
        Map<String, BigDecimal> returnableByKey = ReturnToSupplierInboundLineHelper.computeReturnableQuantityByLineKey(
            inboundLines, returnsReferencingInbound, excludeReturnBusNo);
        Map<String, BigDecimal> inboundByKey =
            ReturnToSupplierInboundLineHelper.sumSplitQuantitiesByLineKey(inboundLines);
        Map<String, BigDecimal> returnedByKey = new HashMap<>();
        for (Map.Entry<String, BigDecimal> inboundEntry : inboundByKey.entrySet()) {
            BigDecimal returnable = returnableByKey.getOrDefault(inboundEntry.getKey(), BigDecimal.ZERO);
            BigDecimal returned = inboundEntry.getValue().subtract(returnable);
            if (returned.compareTo(BigDecimal.ZERO) < 0) {
                returned = BigDecimal.ZERO;
            }
            returnedByKey.put(inboundEntry.getKey(), returned);
        }
        Map<String, BigDecimal> availableByKey =
            this.loadSourceAvailableQuantityByLineKey(inboundWarehouseId, inboundLines);

        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : inboundLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds degraded for linkable goods receipt detail", ex);
            }
        }
        // Asm：映射退货预填响应
        GetLinkableGoodsReceiptDetailResponse res = this.returnToSupplierMapper.toGetLinkableGoodsReceiptDetailResponse(
            inboundLines, returnableByKey, returnedByKey, availableByKey, products);
        // Asm：可退行槽价锚定进货落库价；行合计按可退拆零量比例回算（供前端草稿预览）
        this.enrichLinkableReturnableLinePricesForPreview(res.getLines());
        // Asm：覆盖追溯码为当前院内剩余（本库优先 / 全院回落；禁止回放进货历史码串 · BR-WHS-RT-008）
        this.returnToSupplierRemainingTracePrefiller.fillRemainingTraceNos(res.getLines(), inboundWarehouseId);
        if (head.getSupplierId() != null) {
            // —— 表头供应商名称富化，与列表选单展示口径一致。——
            this.supplierQueryService.findSupplierNameIfPresent(head.getSupplierId())
                .ifPresent(res::setSupplierName);
        }
        return res;
    }

    /**
     * 同意成功后可选触发医保 3504 采购退货上传（集成层降级，失败不阻断主流程）。
     *
     * @param request     同意请求（含 contractBusNo）
     * @param agreedLines 已同意退货明细行
     */
    private void tryUploadMedicareInventoryAfterReturnAgree(
        AgreeReturnToSupplierRequest request,
        List<SupplyRequestModel> agreedLines) {
        List<Long> lineIds = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line != null && line.getId() != null) {
                lineIds.add(line.getId());
            }
        }
        if (lineIds.isEmpty()) {
            // 无持久化行则跳过上传，避免空载荷调用 CHS
            return;
        }
        this.inventoryUploadExtension.runUploadIfEnabled(() -> {
            InventoryUploadAfterPurchaseReturnExtensionIn command = new InventoryUploadAfterPurchaseReturnExtensionIn()
                .setReturnBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setContractBusNo(request.getContractBusNo());
            this.inventoryUploadExtension.uploadAfterPurchaseReturn(command);
        });
    }

    /**
     * 校验 {@code originalBusNo} 指向的进货单可被退货引用（BR-SR-008）。
     *
     * @param originalBusNo     来源进货单号
     * @param sourceWarehouseId 退货源药库；非空时须与进货目的库一致
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RT_002} 进货单不存在、未同意、类型不符或库位不匹配
     */
    private void validateOriginalInboundReceiptForReturn(String originalBusNo, Long sourceWarehouseId) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> inboundLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (inboundLines.isEmpty()) {
            // 引用不存在的单号会导致后续可退余量计算无真源
            throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源进货单不存在");
        }
        boolean matchedWarehouse = false;
        for (SupplyRequestModel line : inboundLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(line.getTypeEnum())) {
                // 订货单/其它类型不可作为退货来源，防止错引单据类型
                throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源单据类型不是药库进货单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                // 未同意进货尚未形成可退库存事实
                throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源进货单未同意，不可引用");
            }
            if (Objects.equals(sourceWarehouseId, line.getPurposeLocationId())) {
                matchedWarehouse = true;
            }
        }
        if (sourceWarehouseId != null && !matchedWarehouse) {
            // 跨库引用会导致源库扣减落在错误库位
            throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源进货单与当前源药库不匹配");
        }
    }

    /** 按 busNo 加载进货单全部明细行。
     *
     * @param inboundBusNo 进货单号
     * @return 供应请求明细列表
     */
    private List<SupplyRequestModel> loadInboundLinesByBusNo(String inboundBusNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(inboundBusNo);
        return this.supplyRequestQueryService.getSupplyByBusNo(q);
    }

    /**
     * 加载 {@code original_bus_no} 指向该进货单的全部退货明细（含各状态）。
     *
     * @param inboundBusNo 进货单号
     * @return 引用该进货单的退货明细
     */
    private List<SupplyRequestModel> loadReturnsReferencingInbound(String inboundBusNo) {
        SupplyByOriginalBusNoQuery q = new SupplyByOriginalBusNoQuery();
        q.setOriginalBusNo(inboundBusNo);
        return this.supplyRequestQueryService.getSupplyByOriginalBusNo(q);
    }

    /**
     * 门禁 + 可退余量校验：明细须来自进货行且合计不超 BR-SR-009 可退量。
     *
     * @param originalBusNo      来源进货单号
     * @param sourceLocationId   退货源药库
     * @param draftLines         待保存退货行
     * @param excludeReturnBusNo 修订/同意复检时排除的本单号
     */
    private void assertReturnLinesBoundToInboundAndWithinReturnable(
        String originalBusNo,
        Long sourceLocationId,
        List<ReturnToSupplierInboundLineHelper.DraftReturnLine> draftLines,
        String excludeReturnBusNo) {
        if (StringUtils.isEmpty(originalBusNo)) {
            // 无来源进货单即视为自由选品路径，整单拒绝
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "originalBusNo 不能为空");
        }
        this.validateOriginalInboundReceiptForReturn(originalBusNo.trim(), sourceLocationId);
        List<SupplyRequestModel> inboundLines = this.loadInboundLinesByBusNo(originalBusNo.trim());
        List<SupplyRequestModel> returnsReferencingInbound = this.loadReturnsReferencingInbound(originalBusNo.trim());
        ReturnToSupplierInboundLineHelper.assertDraftLinesWithinInboundReturnable(
            inboundLines,
            returnsReferencingInbound,
            draftLines,
            excludeReturnBusNo,
            ErrorCodeConstants.WHS_RT_001);
    }

    /**
     * 将 HTTP 明细 DTO 转为 support 校验用的草稿行。
     *
     * @param lines HTTP 明细 DTO
     * @return support 草稿行列表
     */
    private List<ReturnToSupplierInboundLineHelper.DraftReturnLine> toDraftReturnLinesFromReturnLineDtos(
        List<ReturnToSupplierLineDto> lines) {
        List<ReturnToSupplierInboundLineHelper.DraftReturnLine> draftLines = new ArrayList<>();
        if (lines == null) {
            return draftLines;
        }
        for (ReturnToSupplierLineDto line : lines) {
            if (line == null) {
                continue;
            }
            draftLines.add(new ReturnToSupplierInboundLineHelper.DraftReturnLine(
                line.getItemTable(),
                line.getItemId(),
                line.getItemQuantity(),
                line.getUnitCode(),
                line.getLotNumber()));
        }
        return draftLines;
    }

    /**
     * 将已持久化供应请求行转为 support 校验用的草稿行（同意前复检路径）。
     *
     * @param lines 供应请求明细
     * @return support 草稿行列表
     */
    private List<ReturnToSupplierInboundLineHelper.DraftReturnLine> toDraftReturnLinesFromSupplyModels(
        List<SupplyRequestModel> lines) {
        List<ReturnToSupplierInboundLineHelper.DraftReturnLine> draftLines = new ArrayList<>();
        if (lines == null) {
            return draftLines;
        }
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            draftLines.add(new ReturnToSupplierInboundLineHelper.DraftReturnLine(
                line.getItemTable(),
                line.getItemId(),
                line.getItemQuantity(),
                line.getUnitCode(),
                line.getLotNumber()));
        }
        return draftLines;
    }

    /**
     * 将供应请求行转为源库可用量校验用的 HTTP 明细 DTO（同意前复检路径）。
     *
     * @param lines 供应请求明细
     * @return HTTP 明细 DTO 列表
     */
    private List<ReturnToSupplierLineDto> toReturnLineDtosFromSupplyModels(List<SupplyRequestModel> lines) {
        List<ReturnToSupplierLineDto> dtos = new ArrayList<>();
        if (lines == null) {
            return dtos;
        }
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            ReturnToSupplierLineDto dto = new ReturnToSupplierLineDto();
            dto.setItemTable(line.getItemTable());
            dto.setItemId(line.getItemId());
            dto.setItemQuantity(line.getItemQuantity());
            dto.setUnitCode(line.getUnitCode());
            dto.setLotNumber(line.getLotNumber());
            dto.setTraceNo(line.getTraceNo());
            dto.setOccurrenceTime(line.getOccurrenceTime());
            dto.setReason(line.getReason());
            dto.setRemake(line.getRemake());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * 按进货行键批量查询源药库各批号可用量（供预填展示与扣减前门禁）。
     *
     * @param sourceLocationId 退货源药库
     * @param inboundLines     来源进货明细
     * @return 行键 → 源库可用拆零量
     */
    private Map<String, BigDecimal> loadSourceAvailableQuantityByLineKey(
        Long sourceLocationId,
        List<SupplyRequestModel> inboundLines) {
        Map<String, BigDecimal> availableByKey = new LinkedHashMap<>();
        if (sourceLocationId == null || inboundLines == null || inboundLines.isEmpty()) {
            // 缺库位或进货行时无法比对实物库存
            return availableByKey;
        }
        Map<String, Map<Long, Map<String, BigDecimal>>> wantedByLotByTable = new HashMap<>();
        for (SupplyRequestModel line : inboundLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            wantedByLotByTable
                .computeIfAbsent(table, t -> new HashMap<>())
                .computeIfAbsent(itemId, i -> new HashMap<>())
                .merge(lotKeyPart, BigDecimal.ONE, BigDecimal::add);
        }
        for (Map.Entry<String, Map<Long, Map<String, BigDecimal>>> tableEntry : wantedByLotByTable.entrySet()) {
            String itemTable = tableEntry.getKey();
            Set<Long> itemIds = tableEntry.getValue().keySet();
            if (itemIds.isEmpty()) {
                continue;
            }
            // 按物品表批量查源库可用 catalog 行，避免 N+1
            List<InventoryCatalogAvailabilityRowModel> rows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    itemTable, new ArrayList<>(itemIds), List.of(sourceLocationId));
            Map<Long, Map<String, BigDecimal>> availableByItemLot = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel row : rows) {
                if (row == null || row.getItemId() == null || row.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    continue;
                }
                String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                availableByItemLot
                    .computeIfAbsent(row.getItemId(), k -> new HashMap<>())
                    .merge(normLot, row.getQuantity(), BigDecimal::add);
            }
            for (SupplyRequestModel line : inboundLines) {
                if (line == null || !itemTable.equals(StringUtils.trimToEmpty(line.getItemTable()))) {
                    continue;
                }
                String key = ReturnToSupplierInboundLineHelper.buildInboundLineKey(
                    line.getItemTable(), line.getItemId(), line.getLotNumber());
                if (key.isEmpty()) {
                    continue;
                }
                Map<String, BigDecimal> lots = availableByItemLot.getOrDefault(line.getItemId(), Collections.emptyMap());
                String lotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
                availableByKey.put(key, lots.getOrDefault(lotKey, BigDecimal.ZERO));
            }
        }
        return availableByKey;
    }

    /**
     * 将分页中的同一进货单明细聚合为可关联列表行，并计算是否仍有可退余量。
     *
     * @param inboundBusNo      进货单号
     * @param pageLines         当前页全部供应请求明细
     * @param sourceLocationId  退货源药库（与进货目的库一致）
     * @return 列表行；无可退余量或页内无匹配行时返回 null
     */
    private LinkableGoodsReceiptListRowDto buildLinkableGoodsReceiptListRow(
        String inboundBusNo,
        List<SupplyRequestModel> pageLines,
        Long sourceLocationId) {
        List<SupplyRequestModel> inboundLines = new ArrayList<>();
        for (SupplyRequestModel line : pageLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            if (!inboundBusNo.equals(line.getBusNo().trim())) {
                continue;
            }
            inboundLines.add(line);
        }
        if (inboundLines.isEmpty()) {
            // 当前页未含该单号明细，跳过本候选
            return null;
        }
        List<SupplyRequestModel> returnsReferencingInbound = this.loadReturnsReferencingInbound(inboundBusNo);
        Map<String, BigDecimal> returnableByKey = ReturnToSupplierInboundLineHelper.computeReturnableQuantityByLineKey(
            inboundLines, returnsReferencingInbound, null);
        boolean returnable = ReturnToSupplierInboundLineHelper.inboundHasAnyReturnableLine(returnableByKey);
        SupplyRequestModel head = inboundLines.get(0);
        BigDecimal totalAmount = BigDecimal.ZERO;
        LocalDateTime approvalTime = null;
        for (SupplyRequestModel line : inboundLines) {
            if (line == null) {
                continue;
            }
            if (line.getTotalPrice() != null) {
                totalAmount = totalAmount.add(line.getTotalPrice());
            }
            if (line.getApprovalTime() != null) {
                approvalTime = line.getApprovalTime();
            }
        }
        LinkableGoodsReceiptListRowDto dto = new LinkableGoodsReceiptListRowDto();
        dto.setBusNo(inboundBusNo);
        // —— 列表「库房」列展示进货目的库；全部库房筛选时不可回填 0。——
        if (head.getPurposeLocationId() != null) {
            dto.setPurposeLocationId(head.getPurposeLocationId());
        } else {
            dto.setPurposeLocationId(sourceLocationId);
        }
        dto.setSupplierId(head.getSupplierId());
        // —— 制单人取进货单头行申请人，展示名由 enrichLinkableGoodsReceiptDisplayNames 回填。——
        dto.setApplicantId(head.getApplicantId());
        dto.setTotalAmount(totalAmount);
        dto.setApprovalTime(approvalTime);
        // —— 选单「制单时间」优先业务发生时刻，其次申请时刻，最后回落审批时刻。——
        LocalDateTime applyTime = head.getOccurrenceTime();
        if (applyTime == null) {
            applyTime = head.getApplyTime();
        }
        if (applyTime == null) {
            applyTime = approvalTime;
        }
        dto.setApplyTime(applyTime);
        dto.setReturnable(returnable);
        return dto;
    }

    /**
     * IF-WHS-RT-03：详情行槽价锚定供应请求落库单价（BR-PRICE-SRC-001），禁止当前目录价覆盖。
     *
     * @param lines 详情明细行
     */
    private void enrichReturnToSupplierDetailCatalogSlotPrices(List<ReturnToSupplierDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (ReturnToSupplierDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            // —— 历史单据：槽价仅锚 SupplyRequest 持久化行价（BR-PRICE-SRC-001）。——
            ProductDefinitionCatalogUnitBandsAssembler.enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice(
                cat, dto.getPrice(), dto.getUnitCode(), null);
        }
    }

    /**
     * IF-WHS-RT-03：历史未落价明细按来源进货行单价/总额比例回补展示（不写库）。
     *
     * @param response 详情响应
     * @param returnLines 退货供应请求明细
     */
    private void enrichReturnToSupplierDetailAmountsFromInboundIfMissing(
        GetReturnToSupplierDetailResponse response,
        List<SupplyRequestModel> returnLines) {
        if (response == null || response.getLines() == null || response.getLines().isEmpty()) {
            return;
        }
        if (returnLines == null || returnLines.isEmpty()) {
            return;
        }
        String originalBusNo = response.getOriginalBusNo();
        if (StringUtils.isEmpty(originalBusNo)) {
            originalBusNo = returnLines.get(0).getOriginalBusNo();
        }
        if (StringUtils.isEmpty(originalBusNo)) {
            return;
        }
        Map<String, SupplyRequestModel> inboundByKey =
            ReturnToSupplierInboundLineHelper.indexInboundLinesByKey(
                this.loadInboundLinesByBusNo(originalBusNo.trim()));
        Map<String, SupplyRequestModel> returnByKey = new HashMap<>();
        for (SupplyRequestModel returnLine : returnLines) {
            if (returnLine == null || returnLine.getItemId() == null
                || StringUtils.isEmpty(returnLine.getItemTable())) {
                continue;
            }
            String key = ReturnToSupplierInboundLineHelper.buildInboundLineKey(
                returnLine.getItemTable(), returnLine.getItemId(), returnLine.getLotNumber());
            if (!key.isEmpty()) {
                returnByKey.put(key, returnLine);
            }
        }
        for (ReturnToSupplierDetailLineDto dto : response.getLines()) {
            if (dto == null || dto.getItemId() == null || StringUtils.isEmpty(dto.getItemTable())) {
                continue;
            }
            boolean needPrice = dto.getPrice() == null;
            boolean needTotal = dto.getTotalPrice() == null;
            if (!needPrice && !needTotal) {
                // 已有落库价：以快照为准
                continue;
            }
            String lineKey = ReturnToSupplierInboundLineHelper.buildInboundLineKey(
                dto.getItemTable(), dto.getItemId(), dto.getLotNumber());
            SupplyRequestModel inboundLine = inboundByKey.get(lineKey);
            if (inboundLine == null) {
                continue;
            }
            if (needPrice && inboundLine.getPrice() != null) {
                // —— 单价回补：优先进货落库包装进价（同源批号进价快照）。——
                dto.setPrice(inboundLine.getPrice());
            }
            if (needTotal) {
                SupplyRequestModel returnLine = returnByKey.get(lineKey);
                BigDecimal returnQty = null;
                if (returnLine != null) {
                    returnQty = returnLine.getItemQuantity();
                }
                if (returnQty == null) {
                    returnQty = dto.getItemQuantity();
                }
                BigDecimal inboundQty = inboundLine.getItemQuantity();
                BigDecimal inboundTotal = inboundLine.getTotalPrice();
                if (returnQty == null || inboundQty == null || inboundTotal == null
                    || inboundQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                // —— 按进货行落库总额比例分摊：退货拆零量 / 进货拆零量 × 进货行总额。——
                BigDecimal lineAmt = inboundTotal.multiply(returnQty)
                    .divide(
                        inboundQty,
                        ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                        MonetaryAmountSupport.MONETARY_ROUNDING);
                dto.setTotalPrice(MonetaryAmountSupport.scaleCalc(lineAmt));
            }
        }
    }

    /**
     * IF-WHS-RT-03：按明细行重算整单合计写入 {@code totalAmount}。
     *
     * @param response 详情响应
     */
    private void refreshReturnToSupplierDetailHeaderTotals(GetReturnToSupplierDetailResponse response) {
        if (response == null || response.getLines() == null) {
            return;
        }
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (ReturnToSupplierDetailLineDto line : response.getLines()) {
            if (line == null || line.getTotalPrice() == null) {
                continue;
            }
            totalAmount = totalAmount.add(line.getTotalPrice());
        }
        response.setTotalAmount(totalAmount);
    }

    /**
     * IF-WHS-RT-13：可退预填行槽价锚定进货落库价；行合计按可退量比例回算（草稿预览，权威仍在写价门禁）。
     *
     * @param lines 可退进货明细行
     */
    private void enrichLinkableReturnableLinePricesForPreview(List<ReturnableInboundLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (ReturnableInboundLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat != null) {
                // —— 预填草稿：槽价锚进货落库价，避免目录现价污染展示。——
                ProductDefinitionCatalogUnitBandsAssembler.enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice(
                    cat, dto.getPrice(), dto.getUnitCode(), null);
            }
            BigDecimal returnableQty = dto.getReturnableQuantity();
            BigDecimal inboundQty = dto.getInboundQuantity();
            BigDecimal inboundTotal = dto.getTotalPrice();
            if (returnableQty == null || inboundQty == null || inboundTotal == null
                || inboundQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            // —— 可退预览行额：可退拆零量 / 进货拆零量 × 进货行总额（与详情历史回补同口径）。——
            BigDecimal lineAmt = inboundTotal.multiply(returnableQty)
                .divide(
                    inboundQty,
                    ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                    MonetaryAmountSupport.MONETARY_ROUNDING);
            dto.setTotalPrice(MonetaryAmountSupport.scaleCalc(lineAmt));
        }
    }

    /**
     * 创建/修订前：按行键从来源进货单回填包装单价到命令行（BR-PRICE-SRC-001）。
     *
     * <p>领域 {@code SupplyRequestWritePricingSupport#applyReturnToSupplierCommand} 再据此重算
     * {@code totalPrice}；进货行缺价时由写价门禁回落批号进价（禁止目录药典价）。</p>
     *
     * @param command 待写领域命令
     */
    private void fillReturnToSupplierCommandPricesFromInbound(CreateReturnToSupplierCommand command) {
        if (command == null) {
            return;
        }
        List<CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand> items = command.resolveItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand head = items.get(0);
        if (head == null || StringUtils.isEmpty(head.getOriginalBusNo())) {
            return;
        }
        List<SupplyRequestModel> inboundLines = this.loadInboundLinesByBusNo(head.getOriginalBusNo().trim());
        Map<String, SupplyRequestModel> inboundByKey =
            ReturnToSupplierInboundLineHelper.indexInboundLinesByKey(inboundLines);
        for (CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand item : items) {
            if (item == null || item.getItemId() == null || StringUtils.isEmpty(item.getItemTable())) {
                continue;
            }
            String key = ReturnToSupplierInboundLineHelper.buildInboundLineKey(
                item.getItemTable(), item.getItemId(), item.getLotNumber());
            if (key.isEmpty()) {
                continue;
            }
            SupplyRequestModel inboundLine = inboundByKey.get(key);
            if (inboundLine == null || inboundLine.getPrice() == null) {
                // 进货行无落库价：交由领域写价回落批号/目录
                continue;
            }
            item.setPrice(inboundLine.getPrice());
        }
    }

    /**
     * 列表合计为 0/空时，按来源进货落库价 × 退货拆零量回补展示金额（修复历史未落价单据）。
     *
     * <p>仅影响列表读出参，不写回库；新单/可修订单经写价门禁落库后本路径不再触发。</p>
     *
     * @param rows        当前页聚合列表行
     * @param detailLines 当前页供应请求明细
     */
    private void enrichReturnToSupplierListAmountsFromInboundIfMissing(
        List<ReturnToSupplierListRowDto> rows,
        List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty() || detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, List<SupplyRequestModel>> linesByBusNo = new LinkedHashMap<>();
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            linesByBusNo.computeIfAbsent(line.getBusNo().trim(), k -> new ArrayList<>()).add(line);
        }
        Map<String, Map<String, SupplyRequestModel>> inboundIndexByOriginal = new HashMap<>();
        for (ReturnToSupplierListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            // 聚合列表仅认 totalAmount（已去掉列表行 totalPrice 双写）
            BigDecimal existing = row.getTotalAmount();
            if (existing != null && existing.compareTo(BigDecimal.ZERO) != 0) {
                // 已有非零合计：以落库快照为准，不覆盖
                continue;
            }
            List<SupplyRequestModel> returnLines = linesByBusNo.get(row.getBusNo().trim());
            if (returnLines == null || returnLines.isEmpty()) {
                continue;
            }
            String originalBusNo = row.getOriginalBusNo();
            if (StringUtils.isEmpty(originalBusNo)) {
                originalBusNo = returnLines.get(0).getOriginalBusNo();
            }
            if (StringUtils.isEmpty(originalBusNo)) {
                continue;
            }
            String originalKey = originalBusNo.trim();
            Map<String, SupplyRequestModel> inboundByKey = inboundIndexByOriginal.get(originalKey);
            if (inboundByKey == null) {
                List<SupplyRequestModel> inboundLines = this.loadInboundLinesByBusNo(originalKey);
                inboundByKey = ReturnToSupplierInboundLineHelper.indexInboundLinesByKey(inboundLines);
                inboundIndexByOriginal.put(originalKey, inboundByKey);
            }
            BigDecimal sum = BigDecimal.ZERO;
            boolean anyPriced = false;
            for (SupplyRequestModel returnLine : returnLines) {
                if (returnLine == null || returnLine.getItemId() == null
                    || StringUtils.isEmpty(returnLine.getItemTable())) {
                    continue;
                }
                if (returnLine.getTotalPrice() != null) {
                    sum = sum.add(returnLine.getTotalPrice());
                    anyPriced = true;
                    continue;
                }
                String lineKey = ReturnToSupplierInboundLineHelper.buildInboundLineKey(
                    returnLine.getItemTable(), returnLine.getItemId(), returnLine.getLotNumber());
                SupplyRequestModel inboundLine = inboundByKey.get(lineKey);
                if (inboundLine == null || returnLine.getItemQuantity() == null) {
                    continue;
                }
                BigDecimal inboundQty = inboundLine.getItemQuantity();
                BigDecimal inboundTotal = inboundLine.getTotalPrice();
                if (inboundTotal == null || inboundQty == null
                    || inboundQty.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                // —— 按进货行落库总额比例分摊：退货拆零量 / 进货拆零量 × 进货行总额（保留 BR-INV-DTL-007）。——
                BigDecimal lineAmt = inboundTotal.multiply(returnLine.getItemQuantity())
                    .divide(
                        inboundQty,
                        ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                        MonetaryAmountSupport.MONETARY_ROUNDING);
                sum = sum.add(MonetaryAmountSupport.scaleCalc(lineAmt));
                anyPriced = true;
            }
            if (!anyPriced) {
                continue;
            }
            row.setTotalAmount(sum);
        }
    }

    /**
     * 为退货单列表行批量填充供应商名、制单人名、审批人名（IF-WHS-RT-02 侧栏卡片展示）。
     *
     * <p>主键取自聚合行（头行口径）；未命中主数据时保持 null，由前端降级为「—」。</p>
     *
     * @param rows         当前页聚合列表行
     * @param detailLines  当前页供应请求明细（本方法仅作非空门禁，主键以 rows 为准）
     */
    private void enrichReturnToSupplierListDisplayNames(
        List<ReturnToSupplierListRowDto> rows,
        List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (detailLines == null || detailLines.isEmpty()) {
            // 无明细时无法保证列表主键与单据一致，跳过富化
            return;
        }
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> practitionerIds = new HashSet<>();
        for (ReturnToSupplierListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getSupplierId() != null) {
                supplierIds.add(row.getSupplierId());
            }
            if (row.getApplicantId() != null) {
                practitionerIds.add(row.getApplicantId());
            }
            if (row.getApproverId() != null) {
                practitionerIds.add(row.getApproverId());
            }
        }
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : supplierIds) {
            // B.6.4：跨聚合读 SupplierQueryService，仅取展示名
            this.supplierQueryService.findSupplierNameIfPresent(supplierId)
                .ifPresent(name -> supplierNameById.put(supplierId, name));
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long pid : practitionerIds) {
            if (pid == null) {
                continue;
            }
            // B.6.4：跨聚合读 PractitionerQueryService，仅取展示名
            Practitioner practitioner = this.practitionerQueryService.findById(pid);
            if (practitioner != null && StringUtils.isNotEmpty(practitioner.getName())) {
                practitionerNameById.put(pid, practitioner.getName().trim());
            }
        }
        for (ReturnToSupplierListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getSupplierId() != null) {
                String supplierName = supplierNameById.get(row.getSupplierId());
                if (supplierName != null) {
                    row.setSupplierName(supplierName);
                }
            }
            if (row.getApplicantId() != null) {
                String creatorName = practitionerNameById.get(row.getApplicantId());
                if (creatorName != null) {
                    row.setCreatorName(creatorName);
                }
            }
            if (row.getApproverId() != null) {
                String approverName = practitionerNameById.get(row.getApproverId());
                if (approverName != null) {
                    row.setApproverName(approverName);
                }
            }
        }
    }

    /**
     * 为可关联进货单列表行批量填充供应商名、制单人名、目的库名（IF-WHS-RT-12 展示富化）。
     *
     * @param rows 列表行
     */
    private void enrichLinkableGoodsReceiptDisplayNames(List<LinkableGoodsReceiptListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> applicantIds = new HashSet<>();
        Set<Long> purposeLocationIds = new HashSet<>();
        for (LinkableGoodsReceiptListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            // 收集本页需解析的主键，避免逐行查主数据
            if (row.getSupplierId() != null) {
                supplierIds.add(row.getSupplierId());
            }
            if (row.getApplicantId() != null) {
                applicantIds.add(row.getApplicantId());
            }
            if (row.getPurposeLocationId() != null) {
                purposeLocationIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : supplierIds) {
            this.supplierQueryService.findSupplierNameIfPresent(supplierId)
                .ifPresent(name -> supplierNameById.put(supplierId, name));
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long applicantId : applicantIds) {
            // B.6.4：跨聚合读 PractitionerQueryService，仅取展示名
            Practitioner practitioner = this.practitionerQueryService.findById(applicantId);
            if (practitioner != null && StringUtils.isNotEmpty(practitioner.getName())) {
                practitionerNameById.put(applicantId, practitioner.getName().trim());
            }
        }
        Map<Long, String> purposeLocationNameById = new HashMap<>();
        for (Long locationId : purposeLocationIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
            locOpt.ifPresent(loc -> {
                if (StringUtils.isNotEmpty(loc.getName())) {
                    purposeLocationNameById.put(locationId, loc.getName().trim());
                }
            });
        }
        for (LinkableGoodsReceiptListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getSupplierId() != null) {
                String supplierName = supplierNameById.get(row.getSupplierId());
                if (supplierName != null) {
                    row.setSupplierName(supplierName);
                }
            }
            if (row.getApplicantId() != null) {
                String creatorName = practitionerNameById.get(row.getApplicantId());
                if (creatorName != null) {
                    row.setCreatorName(creatorName);
                }
            }
            if (row.getPurposeLocationId() != null) {
                String purposeLocationName = purposeLocationNameById.get(row.getPurposeLocationId());
                if (purposeLocationName != null) {
                    row.setPurposeLocationName(purposeLocationName);
                }
            }
        }
    }

    /**
     * 校验草稿明细在源药库仍有足够实物库存（同意/保存前门禁）。
     *
     * <p>若货物已调拨至药房导致源库为 0，须先调回再退货。</p>
     *
     * @param sourceLocationId 退货源药库
     * @param lines            待校验明细
     */
    private void assertReturnToSupplierDraftLinesWithinSourceAvailableInventory(
        Long sourceLocationId,
        List<ReturnToSupplierLineDto> lines) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            // 无明细或无库位时不做库存比对
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (ReturnToSupplierLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.WHS_RT_001, "退货明细数量须为正数");
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            if (lotKeyPart.isEmpty()) {
                // 无批号时按物品汇总请求量，与源库该物品总可用量比
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
            Map<Long, Map<String, BigDecimal>> wantedByItemLot =
                requestedByLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Map<Long, BigDecimal> wantedWithoutLot = requestedWithoutLotByTable.getOrDefault(itemTable, Collections.emptyMap());
            Set<Long> itemIds = new LinkedHashSet<>();
            itemIds.addAll(wantedByItemLot.keySet());
            itemIds.addAll(wantedWithoutLot.keySet());
            if (itemIds.isEmpty()) {
                continue;
            }
            List<InventoryCatalogAvailabilityRowModel> rows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    itemTable, new ArrayList<>(itemIds), locationIds);
            Map<Long, Map<String, BigDecimal>> availableByItemLot = new HashMap<>();
            Map<Long, BigDecimal> availableByItemId = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel row : rows) {
                if (row == null || row.getItemId() == null || row.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    // 只统计源药库库存，忽略其它库位
                    continue;
                }
                Long itemId = row.getItemId();
                String normLot = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                availableByItemLot
                    .computeIfAbsent(itemId, k -> new HashMap<>())
                    .merge(normLot, row.getQuantity(), BigDecimal::add);
                availableByItemId.merge(itemId, row.getQuantity(), BigDecimal::add);
            }
            for (Map.Entry<Long, Map<String, BigDecimal>> wantItem : wantedByItemLot.entrySet()) {
                Long itemId = wantItem.getKey();
                Map<String, BigDecimal> wantedLots = wantItem.getValue();
                Map<String, BigDecimal> availLots = availableByItemLot.getOrDefault(itemId, Collections.emptyMap());
                for (Map.Entry<String, BigDecimal> wantLot : wantedLots.entrySet()) {
                    BigDecimal requested = wantLot.getValue();
                    BigDecimal available = availLots.getOrDefault(wantLot.getKey(), BigDecimal.ZERO);
                    if (requested.compareTo(available) > 0) {
                        // 源库该批号不足，常见原因为已调拨至药房
                        throw new BusinessException(
                            ErrorCodeConstants.WHS_RT_001,
                            "源库该批号可用量不足；若货物已调拨至药房，须先调拨回进货药库后再退货");
                    }
                }
            }
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RT_001,
                        "源库可用量不足；若货物已调拨至药房，须先调拨回进货药库后再退货");
                }
            }
        }
    }

    /**
     * 解析当前用户可见的药库 locationId 集合（库房门禁真源）。
     *
     * @return 药库 locationId 集合
     */
    private Set<Long> resolveGatedWarehouseLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 断言库位落在当前用户管辖药库集合内。
     *
     * @param locationId 待校验库位
     * @param allowed    可见药库 id 集合
     */
    private void assertLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_RT_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null || !allowed.contains(locationId)) {
            // 二次断言防止 Resolver 与集合不一致时漏拦
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "药库位置不在当前用户可选药库范围");
        }
    }

    /**
     * 可选 typeEnum 须为药库退货单排序号，防止误传其它供应类型。
     *
     * @param typeEnum 供应类型排序号；null 时跳过
     */
    private void validateOptionalReturnTypeEnum(Integer typeEnum) {
        if (typeEnum == null) {
            return;
        }
        int expectedSort = SupplyType.CABINET_RETURN_TO_SUPPLIER.getSortOrder().intValue();
        if (typeEnum.intValue() != expectedSort) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "typeEnum 必须为药库退货单类型");
        }
    }

    /**
     * 可选供应商须存在且为启用态。
     *
     * @param supplierId 供应商主键；null 时跳过
     */
    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = this.supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                // 统一映射为本场景参数错误码
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "供应商不存在或不可用");
            }
            throw ex;
        }
    }

    /**
     * 断言供应请求行均为药库退供应商类型。
     *
     * @param lines 同 busNo 供应请求明细
     */
    private void assertCabinetReturnToSupplier(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(line.getTypeEnum())) {
                // 防止按 busNo 误读其它类型供应单
                throw new BusinessException(ErrorCodeConstants.WHS_RT_103, "单据类型非药库退货单");
            }
        }
    }

    /**
     * busNo 非空门禁。
     *
     * @param busNo 业务单号
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
    }

    /**
     * 明细制单时间必填（领域落库 occurrence_time）。
     *
     * @param lines 退货明细 DTO
     */
    private void assertReturnToSupplierLinesOccurrencePresent(List<ReturnToSupplierLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (ReturnToSupplierLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货明细制单时间不能为空");
            }
        }
    }

    /**
     * 追溯码总长与逗号分隔片段均受宽限约束。
     *
     * @param lines     明细行
     * @param errorCode 业务错误码
     */
    private void assertReturnToSupplierTraceNosWithinWideLimit(List<ReturnToSupplierLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (ReturnToSupplierLineDto line : lines) {
            if (line == null || StringUtils.isEmpty(line.getTraceNo())) {
                continue;
            }
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
                    // 单片段超长同样拒绝，防止拼接绕过总长校验
                    throw new BusinessException(errorCode, "追溯码片段长度超过允许上限（" + maxLen + "）");
                }
            }
        }
    }

    /**
     * 库存匹配用批号：blank 归一为空串。
     *
     * @param lotNumber 原始批号
     * @return 归一化批号
     */
    private String resolveLotNumberForInventory(String lotNumber) {
        if (StringUtils.isEmpty(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    /**
     * 解析源库位类型 code，供追溯出库记录 location_type_enum。
     *
     * @param line 当前明细行
     * @param head 同号表头行
     * @return 源库位类型 code
     */
    private String resolveSourceLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotEmpty(line.getSourceTypeEnum())) {
            return line.getSourceTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotEmpty(head.getSourceTypeEnum())) {
            return head.getSourceTypeEnum().trim();
        }
        // 缺省按药库类型落追溯
        return com.openhis.health.domain.share.enums.SupplyRequestType.CABINET.getCode();
    }

    /**
     * 要求当前登录用户绑定医务人员档案（制单/提交路径）。
     *
     * @param notLoggedInErrorCode  未登录错误码
     * @param notLoggedInMessage    未登录提示（保留签名兼容）
     * @param noPractitionerMessage 未绑定医务人员提示
     * @return 医务人员主键
     */
    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 将领域供应工作流异常映射为本场景错误码（提交/撤回/驳回/删除）。
     *
     * @param ex          领域异常
     * @param defaultCode 默认错误码
     * @return 映射后的异常
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * 将领域同意异常映射为 WHS_RT_104（重复同意）或 WHS_RT_003（其它状态机失败）。
     *
     * @param ex 领域 agree 异常
     * @return 映射后的异常
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.WHS_RT_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_RT_003, msg);
        }
        return ex;
    }
}
