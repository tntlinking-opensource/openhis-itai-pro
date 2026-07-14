/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.purchaseorder.service;

import com.openhis.health.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderDetailLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.AgreePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.CreatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.GetPurchaseOrderDetailRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.LoadPurchaseOrderFormInitRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PageProductsForPurchaseRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PagePurchaseOrdersRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RejectPurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RemovePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.SubmitPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.UpdatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.WithdrawPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.AgreePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.CreatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.GetPurchaseOrderDetailResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.LoadPurchaseOrderFormInitResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageProductsForPurchaseResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PagePurchaseOrdersResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RejectPurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.RemovePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.SubmitPurchaseOrderApprovalResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.UpdatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.WithdrawPurchaseOrderApprovalResponse;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.ProductDefinitionItemDto;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.application.inventory.warehouse.support.ProductDefinitionPurchasePageLoader;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseConstants;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.application.inventory.support.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 药库订货单应用编排（AS-INV-WHS-001，供应类型 {@link SupplyType#CABINET_PURCHASE_ORDER}）。
 *
 * <p>与进货单（inbound receipt）同构：init 下拉、选品 enrich、列表按 {@code busNo} 聚合、详情 {@code rowVersion} 乐观锁修订；
 * 差异仅在审批链——订货同意<strong>不</strong>触发发放与库存。</p>
 *
 * <p><b>业务边界（勿与进货混淆）</b>：{@link #agreePurchaseOrder} 仅调用领域 {@code agreeRequest}；
 * 不改 {@code InventoryItem}、不 {@code createSupplyDelivery}、不自动生成进货单。
 * 已同意订货的进货入口见
 * {@link com.openhis.health.application.inventory.warehouse.inboundreceipt.service.InventoryWarehouseInboundReceiptAppService}。</p>
 *
 * <p>列表与库房门禁以 {@code purpose_location_id} 为锚；「全部药库」占位见
 * {@link InventoryWarehouseConstants#ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID}（init 中 {@code warehouseLocationOptions} 首项 {@code value="0"}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehousePurchaseOrderAppService {

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestPricedWriteOrchestrator supplyRequestPricedWriteOrchestrator;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final LocationQueryService locationQueryService;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final InventoryWarehousePurchaseOrderMapper purchaseOrderMapper;

    /**
     * 加载订货页主数据（IF-WHS-PO-01，对齐进货 init 字段集）。
     *
     * @param request 页初始化请求；机构上下文由服务端 Resolver 解析，请求体可空
     * @return 药库/供应商/库管员/状态/物品表下拉（统一 {@link OptionDto}，B.10.5）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_101} 机构上下文缺失
     */
    public LoadPurchaseOrderFormInitResponse loadPurchaseOrderFormInit(LoadPurchaseOrderFormInitRequest request) {
        // Gate：机构会话上下文，无机构则无法收窄管辖药库与库管员
        Long resolvedOrg = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        // Pre：管辖内启用药库、启用供应商、本机构库管员角色人员
        List<Location> warehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(
            this.locationQueryService.listAllActiveWarehouses());
        List<com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel> suppliers =
            supplierQueryService.listSuppliersByTypeAndActive(
                com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
        List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
            WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
        List<OptionDto> warehouseOpts = purchaseOrderMapper.toOptionDtoListFromLocations(warehouses);
        List<OptionDto> warehouseOptsWithAll = new ArrayList<>();
        // —— 列表筛「全部药库」：value=0 与 pagePurchaseOrders.purposeLocationId 约定一致，非 Location 主键
        warehouseOptsWithAll.add(new OptionDto(0L, "0", "全部"));
        warehouseOptsWithAll.addAll(warehouseOpts);
        // Asm：挂接各下拉并返回页初始化响应
        return purchaseOrderMapper.toLoadPurchaseOrderFormInitResponse(
            warehouseOptsWithAll,
            purchaseOrderMapper.toOptionDtoListFromSuppliers(suppliers),
            purchaseOrderMapper.toOptionDtoListFromPractitioners(warehouseKeepers),
            purchaseOrderMapper.toOptionDtoListSupplyStatuses(),
            purchaseOrderMapper.toOptionDtoListPurchaseItemTableTypes());
    }

    /**
     * 分页查询订货单列表（IF-WHS-PO-02；按 {@code busNo} 聚合，对齐进货列表路径）。
     *
     * @param request 须含 {@code purposeLocationId}、{@code pageNo}、{@code pageSize}；可选 {@code statusEnum}、申请时段
     * @return 聚合行 {@link PurchaseOrderListRowDto} 与分页元数据（B.10.11）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 参数非法或药库越权；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_PO_102} 列表聚合查询失败
     */
    public PagePurchaseOrdersResponse pagePurchaseOrders(PagePurchaseOrdersRequest request) {
        // Gate：请求体、目的药库、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_PO_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForPurchaseOrderGate();

        // Pre：组装 SupplyRequestDocumentListQuery（固定订货类型，按目的药库门控）
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_PURCHASE_ORDER.getCode());
        Long filterLocationId = request.getPurposeLocationId();
        if (filterLocationId != null
            && filterLocationId.longValue() == InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID) {
            // —— 「全部药库」：展开为当前用户可见 id 集合；无可见库时用 -1 占位避免空 IN 全表扫描
            if (!allowed.isEmpty()) {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowed));
            } else {
                listQuery.setPurposeLocationIds(List.of(-1L));
            }
        } else {
            // —— 单库筛选：须落在管辖门控内，防横向窥探其它药库单据
            this.assertLocationAllowed(filterLocationId, allowed);
            listQuery.setPurposeLocationId(filterLocationId);
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            // —— 状态须为 SupplyStatus 持久化 code（B.10.8.1），禁止排序号中转
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        // —— 申请时刻窗：兼容 applyTimeFrom/To 与 AbstractPageQuery 的 startDate/endDate
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
            // Cmd：countDistinctBusNo → 分页单号 → 批量明细 → Mapper 聚合
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<PurchaseOrderListRowDto> records = this.purchaseOrderMapper.toPurchaseOrderListAggregated(busNos, lines);
            // —— 展示名依赖主数据，在应用层批量解析，避免 Mapper 注入 QueryService（B.6.1）
            this.enrichPurchaseOrderListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：PagedResultBuilder 统一分页响应
            return this.purchaseOrderMapper.toPagePurchaseOrdersAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pagePurchaseOrders failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_PO_102, "订货单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读订货单详情（IF-WHS-PO-03；含 {@code rowVersion} 供修订带回）。
     *
     * @param request 须含非空 {@code busNo}
     * @return 头字段为持久化 code（B.10.8.1）；明细含产品读模型展示扩展
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 单号缺失；
     *                            {@link ErrorCodeConstants#WHS_PO_103} 单据不存在或类型非订货单
     */
    public GetPurchaseOrderDetailResponse getPurchaseOrderDetail(GetPurchaseOrderDetailRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }
        // Pre：按单号拉取同号全部供应请求写模型行
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "订货单不存在");
        }
        // —— 类型守卫：防止借详情接口读取其它供应类型单据
        this.assertCabinetPurchaseOrder(lines);

        // —— 按物品表+主键去重后批量拉产品定义，避免 N+1（与进货详情同构）
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
        Map<String, ProductDefinitionDisplayDto> products = productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        // Asm：Mapper 映射详情；应用层 enrich 单位槽与槽价（BR-PRICE-SRC-001）
        GetPurchaseOrderDetailResponse res = purchaseOrderMapper.toGetPurchaseOrderDetailResponse(lines, products);
        this.enrichPurchaseOrderDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichPurchaseOrderDetailLines(res.getLines());
        return res;
    }

    /**
     * 新建订货单草稿并落库供应请求（IF-WHS-PO-04）。
     *
     * @param request 须含目的药库、经手人、非空明细；{@code typeEnum} 由后端固定为订货类型
     * @return 新建 {@code busNo} 与待审批状态 code
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 参数非法或药库越权；
     *                            {@link ErrorCodeConstants#WHS_PO_002} 供应商不可用；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 未登录或机构上下文缺失
     */
    public CreatePurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request) {
        // Gate：请求体、明细、目的药库、机构/库房门禁与经手人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "订货明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "入库目的位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForPurchaseOrderGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        this.validateSupplierOptional(request.getSupplierId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "经手人不能为空");
        }
        // —— 明细制单时刻 occurrence_time 由前端录入，领域落库供应请求行，缺则拒绝保存
        this.assertPurchaseOrderLinesOccurrencePresent(request.getLines());

        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_PO_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");

        try {
            // Cmd：组装 Create 命令并调用领域写服务
            com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand cmd =
                purchaseOrderMapper.toCreatePurchaseOrderCommand(request, operatorId, applicantId);
            String busNo = supplyRequestPricedWriteOrchestrator.createPurchaseOrder(cmd);
            // Asm：映射新建响应
            return purchaseOrderMapper.toCreatePurchaseOrderResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "订货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 修订订货单（IF-WHS-PO-05；同号全量明细替换，须携带 {@code rowVersion} 乐观锁）。
     *
     * @param request 须含 {@code busNo}、{@code rowVersion}、非空明细及与新建相同的头字段约束
     * @return 修订后 {@code busNo} 与待审批状态 code
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 参数非法；{@link ErrorCodeConstants#WHS_PO_103} 单据不存在；
     *                            {@link ErrorCodeConstants#WHS_PO_004} 当前状态不可编辑；{@link ErrorCodeConstants#WHS_PO_105} 并发/版本冲突；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 未登录或机构上下文缺失
     */
    public UpdatePurchaseOrderResponse updatePurchaseOrder(UpdatePurchaseOrderRequest request) {
        // Gate：busNo、rowVersion、明细、目的药库与机构门禁
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "订货明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "入库目的位置不能为空");
        }
        if (request.getRowVersion() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "rowVersion 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForPurchaseOrderGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "订货单不存在");
        }
        this.assertCabinetPurchaseOrder(existing);

        // Pre：读侧 verifyAbleEdit——已提交/已同意等态禁止客户端强行覆盖明细
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_004, "当前状态不允许编辑订货单");
        }

        this.validateSupplierOptional(request.getSupplierId());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "经手人不能为空");
        }
        this.assertPurchaseOrderLinesOccurrencePresent(request.getLines());

        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_PO_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");

        try {
            // Cmd：组装 Create 命令并调用领域写服务（同号修订）
            com.openhis.health.domain.workflow.supplyrequest.command.CreatePurchaseOrderCommand cmd =
                purchaseOrderMapper.toCreatePurchaseOrderCommandForUpdate(request, operatorId, applicantId);
            String busNo = supplyRequestPricedWriteOrchestrator.createPurchaseOrder(cmd);
            // Asm：映射修订响应
            return purchaseOrderMapper.toUpdatePurchaseOrderResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "订货参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.WHS_PO_105.equals(ex.getErrorCode())) {
                // —— 领域乐观锁版本不匹配：提示刷新后重带 rowVersion 再保存
                throw new BusinessException(ErrorCodeConstants.WHS_PO_105, "订货单已被修改或版本不匹配，请刷新后重试");
            }
            throw ex;
        } catch (OptimisticLockingFailureException ex) {
            // —— JPA @Version 冲突：与进货修订同口径，避免静默丢更新
            throw new BusinessException(ErrorCodeConstants.WHS_PO_105, "订货单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交订货单进入待审批态（IF-WHS-PO-06）。
     *
     * @param request 须含 {@code busNo}
     * @return 提交成功标志及提交后头行状态 code
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 单号非法；{@link ErrorCodeConstants#WHS_PO_003} 状态不允许提交；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 未登录或无制单人员档案
     */
    public SubmitPurchaseOrderApprovalResponse submitPurchaseOrderApproval(SubmitPurchaseOrderApprovalRequest request) {
        // Gate：busNo 与制单人员档案（申请人须绑定医务人员）
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_PO_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域 submitApproval——仅驱动供应请求状态机，不涉及库存
            supplyRequestDomainService.submitApproval(purchaseOrderMapper.toSubmitApprovalCommand(request,
                currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录"),
                applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_PO_003);
        }
        // Asm：回读真实头行状态后映射响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return purchaseOrderMapper.toSubmitPurchaseOrderApprovalResponse(st);
    }

    /**
     * 撤回订货单审批，回到可编辑草稿态（IF-WHS-PO-07）。
     *
     * @param request 须含 {@code busNo}
     * @return 撤回成功标志及撤回后状态 code
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 单号非法；{@link ErrorCodeConstants#WHS_PO_003} 状态不允许撤回；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 未登录
     */
    public WithdrawPurchaseOrderApprovalResponse withdrawPurchaseOrderApproval(WithdrawPurchaseOrderApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(purchaseOrderMapper.toWithdrawApprovalCommand(request, currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_PO_003);
        }
        // Asm：映射撤回响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return purchaseOrderMapper.toWithdrawPurchaseOrderApprovalResponse(st);
    }

    /**
     * 同意订货单（IF-WHS-PO-08）。
     *
     * <p><b>业务边界</b>：仅 {@code agreeRequest} 更新审批态；<strong>不</strong>创建进货单、不改库存。
     * 同意后进货由 {@code inboundreceipt} 模块 {@code pageLinkablePurchaseOrders} / {@code getLinkablePurchaseOrderDetail} 拉取。</p>
     *
     * @param request 须含 {@code busNo}；须未重复同意
     * @return 同意成功标志
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 单号非法；{@link ErrorCodeConstants#WHS_PO_103} 非订货单；
     *                            {@link ErrorCodeConstants#WHS_PO_104} 重复同意；{@link ErrorCodeConstants#WHS_PO_003} 状态不允许；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 未登录
     */
    public AgreePurchaseOrderResponse agreePurchaseOrder(AgreePurchaseOrderRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }

        // Pre：类型守卫 + 读侧校验未重复同意（与进货 agree 前置校验同构，但无后续发放链）
        this.preAssertCabinetPurchaseOrder(request.getBusNo().trim());
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_104, "订货单已同意，禁止重复同意");
        }

        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录");

        try {
            // Cmd：领域 agreeRequest——订货同意终点，禁止在此扩展 createInboundReceipt
            supplyRequestDomainService.agreeRequest(purchaseOrderMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        }
        // Asm：映射同意响应
        return purchaseOrderMapper.toAgreePurchaseOrderResponse();
    }

    /**
     * 驳回订货单并记录原因（IF-WHS-PO-09）。
     *
     * @param request 须含 {@code busNo} 与非空 {@code rejectReason}
     * @return 驳回成功标志
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 参数非法；{@link ErrorCodeConstants#WHS_PO_003} 状态不允许；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 未登录
     */
    public RejectPurchaseOrderResponse rejectPurchaseOrder(RejectPurchaseOrderRequest request) {
        // Gate：busNo 与驳回原因（审计留痕）
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "驳回原因不能为空");
        }

        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录");

        try {
            // Cmd：领域驳回请求
            supplyRequestDomainService.rejectRequest(purchaseOrderMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_PO_003);
        }
        // Asm：映射驳回响应
        return purchaseOrderMapper.toRejectPurchaseOrderResponse();
    }

    /**
     * 删除订货单（IF-WHS-PO-10；软删，仅允许可删态）。
     *
     * @param request 须含 {@code busNo}
     * @return 删除成功标志
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 单号非法；{@link ErrorCodeConstants#WHS_PO_005} 删除被阻断；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 未登录
     */
    public RemovePurchaseOrderResponse removePurchaseOrder(RemovePurchaseOrderRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域按 busNo 软删
            supplyRequestDomainService.removeByBusNo(purchaseOrderMapper.toRemoveByBusNoCommand(request, currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_PO_005);
        }
        // Asm：映射删除响应
        return purchaseOrderMapper.toRemovePurchaseOrderResponse();
    }

    /**
     * 订货单制单人（{@code wkf_supply_request.applicant_id}）：取当前登录用户绑定医务人员主键。
     *
     * @param notLoggedInErrorCode  未登录或缺用户主键时错误码
     * @param notLoggedInMessage    未登录提示（保留参数位，与进货同签名）
     * @param noPractitionerMessage 已登录但未绑定医务人员时的提示
     * @return 非空医务人员主键
     * @throws BusinessException 无登录用户或未绑定医务人员
     */
    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 订货明细行须携带制单时间 {@code occurrence_time}（由前端传入）；草稿阶段不写申请时间。
     *
     * @param lines 新建/修订请求明细
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 任一行缺 occurrenceTime
     */
    private void assertPurchaseOrderLinesOccurrencePresent(List<PurchaseOrderLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (PurchaseOrderLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "订货明细制单时间不能为空");
            }
        }
    }

    /**
     * 分页查询订货选品（IF-WHS-PO-11；与进货选品同构，写 PURCHASE 进价与单位槽）。
     *
     * @param request 须含 {@code purposeLocationId} 与分页窗口；{@code productTableName} 须在白名单内
     * @return 产品定义展示行分页（{@code availableQuantity} 固定 null，订货不汇总库存）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 参数非法或药库越权；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_PO_102} 选品查询失败
     */
    public PageProductsForPurchaseResponse pageProductsForPurchase(PageProductsForPurchaseRequest request) {
        // Gate：分页窗口、目的药库、机构门禁与物品表白名单
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_PO_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForPurchaseOrderGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForPurchaseSelection(request.getProductTableName());
        // Pre：组装产品定义分页查询
        ProductDefinitionPageQuery query = purchaseOrderMapper.toProductDefinitionPageQuery(request);
        try {
            // Cmd：分页查询采购选品
            Page<ProductDefinitionDisplayDto> page = productDefinitionPurchasePageLoader.pageForPurchase(query);
            PageProductsForPurchaseResponse response = purchaseOrderMapper.toPageProductsForPurchaseResponse(page);
            this.enrichPurchaseProductSelectionRows(response.getContent());
            // Asm：返回富化后的选品分页
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageProductForPurchase failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_PO_102, "采购选品查询失败");
        }
    }

    /**
     * 订货详情：补齐行单位展示名与 catalog 单位槽（与进货详情 enrich 同构，§2.2.3 读价不在此写库）。
     *
     * @param lines           详情明细行
     * @param products        批量产品读模型
     * @param terminologyMaps 单位/术语映射
     */
    private void enrichPurchaseOrderDetailProductDisplay(
        List<PurchaseOrderDetailLineDto> lines,
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
        // —— 逐行匹配读模型 compositeKey，补齐单位展示与 catalog 编辑提示
        for (PurchaseOrderDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
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
     * 详情行槽价 enrich：锚供应请求行持久化 {@code price}/{@code unitCode}（BR-PRICE-SRC-001），不读当前价覆盖写库值。
     *
     * @param lines 已带 productCatalog 的详情行
     */
    private void enrichPurchaseOrderDetailLines(List<PurchaseOrderDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (PurchaseOrderDetailLineDto dto : lines) {
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
     * 采购选品行富化：读 PURCHASE 进价写 {@code purchasePrice} 与单位槽；订货场景不汇总 {@code availableQuantity}。
     *
     * @param rows 选品分页 content
     */
    private void enrichPurchaseProductSelectionRows(List<ProductDefinitionItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 先收集本页涉及的 productTableName+id，批量拉读模型
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ProductDefinitionItemDto row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(row.getProductDefinitionId());
                lookup.setProductTableName(row.getProductTableName().trim());
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> products = new java.util.HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed for purchase product selection rows", ex);
            }
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();
        // —— 逐行写进价槽价；无 table/id 的行清空 purchasePrice
        for (ProductDefinitionItemDto row : rows) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                String table = row.getProductTableName().trim();
                Long defId = row.getProductDefinitionId();
                BigDecimal purchasePerPackage = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(table, defId);
                row.setPurchasePrice(purchasePerPackage);
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
                }
            } else {
                row.setPurchasePrice(null);
            }
            // —— 订货选品不展示可用库存，与进货选品字段同构但语义不同
            row.setAvailableQuantity(null);
        }
    }

    /**
     * 选品 {@code productTableName} 白名单：仅药品/耗材定义表，防借选品接口探测其它表。
     *
     * @param raw 请求传入的表名，可 blank（表示不限表）
     */
    private void assertProductTableNameWhitelistForPurchaseSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return;
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "productTableName 非法");
        }
    }

    /**
     * 提交/撤回审批后回读头行状态，用于响应 {@code statusEnum}（持久化 code）。
     *
     * @param busNo 业务单号
     * @return 头行 {@link SupplyStatus}；单据不存在时 null
     */
    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        SupplyRequestModel head = lines.get(0);
        return EnumUtil.fromCode(SupplyStatus.class, head.getStatusEnum());
    }

    /**
     * 业务单号非空门控：首尾空白视为非法，由调用方在 trim 前拦截。
     *
     * @param busNo 业务单号
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} busNo 为空
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }
    }

    /**
     * 同意/详情等路径前置校验：单据须存在且类型为药库订货单。
     *
     * @param busNo 业务单号（会 trim）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_103} 不存在或类型不符
     */
    private void preAssertCabinetPurchaseOrder(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "订货单不存在");
        }
        this.assertCabinetPurchaseOrder(lines);
    }

    /**
     * 列表聚合行展示名批量富化（供应商/制单人/审批人/药库/状态文案）。
     *
     * <p>展示名依赖 Supplier/Practitioner/Location 主数据，须在 AppService 批量解析（B.6.1），Mapper 不注入 QueryService。</p>
     *
     * @param rows         当前页聚合行
     * @param detailLines  同页 busNo 对应的供应请求明细（用于取头字段 id）
     */
    private void enrichPurchaseOrderListDisplayNames(List<PurchaseOrderListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty() || detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToSupplierId = new HashMap<>();
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = this.buildPurchaseOrderHeadLineByBusNo(detailLines);
        // —— 从各单号头行提取 supplierId/applicantId/approverId，供后续批量查名
        for (Map.Entry<String, SupplyRequestModel> e : headByBusNo.entrySet()) {
            SupplyRequestModel head = e.getValue();
            if (head == null) {
                continue;
            }
            String bn = e.getKey();
            if (head.getSupplierId() != null) {
                busNoToSupplierId.put(bn, head.getSupplierId());
            }
            if (head.getApplicantId() != null) {
                busNoToApplicantId.put(bn, head.getApplicantId());
            }
            if (head.getApproverId() != null) {
                busNoToApproverId.put(bn, head.getApproverId());
            }
        }
        Map<Long, String> supplierNameById = new HashMap<>();
        // —— 供应商名称按主键去重后逐条解析；页大小有上界，N+1 可接受
        for (Long supplierId : new HashSet<>(busNoToSupplierId.values())) {
            if (supplierId == null) {
                continue;
            }
            Optional<String> nameOpt = this.supplierQueryService.findSupplierNameIfPresent(supplierId);
            nameOpt.ifPresent(name -> supplierNameById.put(supplierId, name));
        }
        Set<Long> practitionerIds = new HashSet<>();
        practitionerIds.addAll(busNoToApplicantId.values());
        practitionerIds.addAll(busNoToApproverId.values());
        Map<Long, String> practitionerNameById = new HashMap<>();
        // —— 申请人/审批人可能同一人，合并主键再查，减少重复 IO
        for (Long pid : practitionerIds) {
            if (pid == null) {
                continue;
            }
            Practitioner pr = this.practitionerQueryService.findById(pid);
            if (pr != null && StringUtils.isNotEmpty(pr.getName())) {
                practitionerNameById.put(pid, pr.getName());
            }
        }
        Set<Long> purposeLocationIds = new HashSet<>();
        for (PurchaseOrderListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeLocationIds.add(row.getPurposeLocationId());
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
        // —— 将解析到的展示名写回聚合行；未命中主数据时保持 null，由前端降级
        for (PurchaseOrderListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            Long sid = busNoToSupplierId.get(bn);
            if (sid != null) {
                String sn = supplierNameById.get(sid);
                if (sn != null) {
                    row.setSupplierName(sn);
                }
            }
            Long aid = busNoToApplicantId.get(bn);
            if (aid != null) {
                String an = practitionerNameById.get(aid);
                if (an != null) {
                    row.setCreatorName(an);
                }
            }
            Long apid = busNoToApproverId.get(bn);
            if (apid != null) {
                String apn = practitionerNameById.get(apid);
                if (apn != null) {
                    row.setApproverName(apn);
                }
            }
            Long plid = row.getPurposeLocationId();
            if (plid != null) {
                String pln = purposeLocationNameById.get(plid);
                if (pln != null) {
                    row.setPurposeLocationName(pln);
                }
            }
            if (StringUtils.isNotEmpty(row.getStatusEnum())) {
                SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
                if (st != null) {
                    row.setStatusEnumText(st.getDisplay());
                }
            }
        }
    }

    /**
     * 按单号选取「头行」：同号多行时取 {@code apply_time} 最大者，与列表 Mapper 聚合口径一致。
     *
     * @param detailLines 列表同页明细
     * @return busNo → 代表头行
     */
    private Map<String, SupplyRequestModel> buildPurchaseOrderHeadLineByBusNo(List<SupplyRequestModel> detailLines) {
        Map<String, SupplyRequestModel> best = new HashMap<>();
        if (detailLines == null) {
            return best;
        }
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            String bn = line.getBusNo().trim();
            SupplyRequestModel prev = best.get(bn);
            if (prev == null) {
                best.put(bn, line);
                continue;
            }
            // —— 同号多行时保留 apply_time 较新者作为头行代表
            LocalDateTime prevApply = prev.getApplyTime();
            LocalDateTime curApply = line.getApplyTime();
            if (curApply != null && (prevApply == null || curApply.isAfter(prevApply))) {
                best.put(bn, line);
            }
        }
        return best;
    }

    /**
     * 校验供应请求行类型为药库订货；防止借订货接口读取其它供应类型单据。
     *
     * @param lines 同号供应请求行；{@code null} 时跳过
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_103} 任一行 typeEnum 非订货
     */
    private void assertCabinetPurchaseOrder(List<SupplyRequestModel> lines) {
        if (lines == null) {
            return;
        }
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            // —— 任一行 typeEnum 非订货即拒绝，防止跨类型单号误读
            if (!SupplyType.CABINET_PURCHASE_ORDER.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "单据类型非药库订货单");
            }
        }
    }

    /**
     * 解析当前用户可见的启用药库 id 集合（{@link CurrentManagedLocationIdsResolver} 与管辖库位同源收窄）。
     *
     * @return 允许作为订货目的库/列表/选品门禁的药库主键集合；无可见库时可能为空集
     */
    private Set<Long> resolveGatedWarehouseLocationIdsForPurchaseOrderGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 药库位置门控：须在管辖集合内，否则视为越权访问。
     *
     * @param locationId 待校验库位主键
     * @param allowed    当前用户可见药库 id 集合
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 库位 null 或不在 allowed 内
     */
    private void assertLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_PO_001, "药库位置不在当前机构可选范围");
        if (locationId == null || !allowed.contains(locationId)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "药库位置不在当前机构可选范围");
        }
    }

    /**
     * 可选供应商校验：传入时须存在且 {@link SupplierStatus#ACTIVE}。
     *
     * @param supplierId 供应商主键，null 时跳过
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_002} 不存在或不可用
     */
    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                // —— 停用供应商不允许写入订货单头，避免后续对账口径混乱
                throw new BusinessException(ErrorCodeConstants.WHS_PO_002, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                // —— 将 NOT_FOUND 收口为订货场景 WHS_PO_002，便于前端统一提示
                throw new BusinessException(ErrorCodeConstants.WHS_PO_002, "供应商不存在或不可用");
            }
            throw ex;
        }
    }

    /**
     * 领域 {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} 映射为应用层默认错误码，其余原样抛出。
     *
     * @param ex          领域业务异常
     * @param defaultCode 应用层默认错误码（如 WHS_PO_003）
     * @return 映射后的异常
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * 同意流程异常映射：领域 INVALID_ARGUMENT 且文案含「已同意」时映射 {@link ErrorCodeConstants#WHS_PO_104}。
     *
     * @param ex 领域 agreeRequest 抛出的业务异常
     * @return 映射后的应用层异常
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.WHS_PO_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_PO_003, msg);
        }
        return ex;
    }
}
