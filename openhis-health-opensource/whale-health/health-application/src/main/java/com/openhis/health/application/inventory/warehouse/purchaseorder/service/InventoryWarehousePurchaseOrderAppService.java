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
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.LinkableDeptPurchaseRequestListRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderDetailLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderListRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseWarningProductRowDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.AgreePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.CreatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.GetLinkableDeptPurchaseRequestDetailRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.GetPurchaseOrderDetailRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.LoadPurchaseOrderFormInitRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PageLinkableDeptPurchaseRequestsRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PageProductsForPurchaseRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PagePurchaseOrdersRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.PagePurchaseWarningProductsRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RejectPurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.RemovePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.SubmitPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.UpdatePurchaseOrderRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.request.WithdrawPurchaseOrderApprovalRequest;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.AgreePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.CreatePurchaseOrderResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.GetLinkableDeptPurchaseRequestDetailResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.GetPurchaseOrderDetailResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.LoadPurchaseOrderFormInitResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageLinkableDeptPurchaseRequestsResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PageProductsForPurchaseResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PagePurchaseOrdersResponse;
import com.openhis.health.application.inventory.warehouse.purchaseorder.response.PagePurchaseWarningProductsResponse;
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
import com.openhis.health.application.inventory.warehouse.purchaseorder.support.PurchaseOrderWarningProductResolver;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.constants.InventoryWarningConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryDepartmentConstants;
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
 *
 * <p>预警快捷选品（IF-WHS-PO-14）只读查询最晚效期批近效 / 库存低于最小值品种，勾选后经 IF-11
 * {@code productDefinitionIds} 纳入明细；不写库存、不自动填数量。</p>
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
    /** 订货预警快捷选品：效期最晚批 + 仅低储合并（IF-WHS-PO-14）。 */
    private final PurchaseOrderWarningProductResolver purchaseOrderWarningProductResolver;
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
        List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
            WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
        List<OptionDto> warehouseOpts = purchaseOrderMapper.toOptionDtoListFromLocations(warehouses);
        List<OptionDto> warehouseOptsWithAll = new ArrayList<>();
        // —— 列表筛「全部药库」：value=0 与 pagePurchaseOrders.purposeLocationId 约定一致，非 Location 主键
        warehouseOptsWithAll.add(new OptionDto(0L, "0", "全部"));
        warehouseOptsWithAll.addAll(warehouseOpts);
        // —— 关联科室采购申请弹窗：院内启用科室药房（不按药库管辖收窄）
        List<Location> departmentPharmacies = this.locationQueryService.listAllActiveDepartmentPharmacies();
        List<OptionDto> deptPharmacyOpts =
            purchaseOrderMapper.toOptionDtoListFromLocations(departmentPharmacies);
        List<OptionDto> deptPharmacyOptsWithAll = new ArrayList<>();
        deptPharmacyOptsWithAll.add(new OptionDto(0L, "0", "全部"));
        deptPharmacyOptsWithAll.addAll(deptPharmacyOpts);
        // Asm：挂接各下拉并返回页初始化响应
        return purchaseOrderMapper.toLoadPurchaseOrderFormInitResponse(
            warehouseOptsWithAll,
            deptPharmacyOptsWithAll,
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
     *                            {@link ErrorCodeConstants#WHS_PO_106} 来源科室采购申请不可引用；
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
        if (StringUtils.isNotEmpty(request.getOriginalBusNo())) {
            // Pre：可选拉源须已同意科室采购申请且未被其它订货占用（BR-WHS-PO-012）
            this.validateOriginalDeptPurchaseRequestForPurchaseOrder(request.getOriginalBusNo().trim(), null);
        }
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
     * <p>可选 {@code productDefinitionIds}：预警快捷勾选后按 id 集合收窄（仍须表名/启用门禁）。</p>
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
        // 无目的药库无法做药库门禁，拒绝探测选品
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForPurchaseOrderGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForPurchaseSelection(request.getProductTableName());
        List<Long> idFilter = request.getProductDefinitionIds();
        // Pre：预警勾选 ids 路径 — 须带表头项目类型，按目录批量读后内存分页
        if (idFilter != null && !idFilter.isEmpty()) {
            // ids 快捷路径须锚定表头药/耗，防止跨表型 id 混入
            if (StringUtils.isEmpty(request.getProductTableName())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "传 productDefinitionIds 时须指定 productTableName");
            }
            return this.pageProductsForPurchaseByIds(request, idFilter);
        }
        // Pre：组装产品定义分页查询（普通关键字选品）
        ProductDefinitionPageQuery query = purchaseOrderMapper.toProductDefinitionPageQuery(request);
        try {
            // Cmd：分页查询采购选品
            Page<ProductDefinitionDisplayDto> page = productDefinitionPurchasePageLoader.pageForPurchase(query);
            PageProductsForPurchaseResponse response = purchaseOrderMapper.toPageProductsForPurchaseResponse(page);
            this.enrichPurchaseProductSelectionRows(response.getContent());
            // Asm：返回富化后的选品分页
            return response;
        } catch (RuntimeException ex) {
            // 读侧失败统一包装，避免透出底层堆栈给客户端
            log.warn("pageProductForPurchase failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_PO_102, "采购选品查询失败");
        }
    }

    /**
     * 订货预警快捷选品分页（IF-WHS-PO-14 / AS-INV-WHS-001）：只读；勾选后走 IF-11 {@code productDefinitionIds}。
     *
     * <p>效期按最晚效期批近效（BR-014）；低储仅低于最低（BR-015）；本接口不写库存、不自动写订货数量（BR-016）。</p>
     *
     * @param request 须含目的药库、表头项目类型、预警筛选项与分页窗口；可选 {@code keyword}
     * @return 品种维预警行分页（无批号）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 参数非法或药库越权；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 机构缺失；
     *                            {@link ErrorCodeConstants#WHS_PO_102} 查询失败
     */
    public PagePurchaseWarningProductsResponse pagePurchaseWarningProducts(
        PagePurchaseWarningProductsRequest request) {
        // Gate：分页、药库、机构、表头项目类型、筛选项白名单
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_PO_001, "分页参数不能为空");
        // 无目的药库则拒绝，与订货头门禁同构
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "purposeLocationId 不能为空");
        }
        // 未选表头项目类型时无法收窄药/耗，阻断误查全目录预警
        if (StringUtils.isEmpty(request.getProductTableName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请先选择表头项目类型（药品或耗材）");
        }
        // 筛选项为空无法决定效期/低储/全部，拒绝
        if (StringUtils.isEmpty(request.getWarningFilter())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "warningFilter 不能为空");
        }
        String warningFilter = request.getWarningFilter().trim();
        // 非法筛选项不查库，直接提示合法枚举码
        if (!InventoryWarningConstants.WARNING_MODE_EXPIRE.equals(warningFilter)
            && !InventoryWarningConstants.WARNING_MODE_LOW_STOCK.equals(warningFilter)
            && !InventoryWarningConstants.WARNING_FILTER_ALL.equals(warningFilter)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "warningFilter 须为 expire、lowStock 或 all");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForPurchaseOrderGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForPurchaseSelection(request.getProductTableName());
        String productTableName = request.getProductTableName().trim();
        try {
            // Pre：Resolver 合并效期/低储命中（跨聚合目录读在 Asm 装配阶段）
            List<PurchaseOrderWarningProductResolver.PurchaseWarningHit> hits =
                this.purchaseOrderWarningProductResolver.resolveWarningProductHits(
                    warningFilter, productTableName);
            // Cmd：本接口只读，无领域写命令；装配弹窗行并按 keyword 过滤
            List<PurchaseWarningProductRowDto> allRows = this.assemblePurchaseWarningRows(hits, request.getKeyword());
            // 分页缺省：与其它库存列表一致（1-based / 20）
            int pageNo = 1;
            if (request.getPageNo() != null) {
                pageNo = request.getPageNo();
            }
            int pageSize = 20;
            if (request.getPageSize() != null) {
                pageSize = request.getPageSize();
            }
            long total = allRows.size();
            int from = (pageNo - 1) * pageSize;
            List<PurchaseWarningProductRowDto> pageRows;
            // 页越界返回空页，避免 subList 抛错
            if (from >= allRows.size()) {
                pageRows = List.of();
            } else {
                int to = from + pageSize;
                // 末页不足一整页时截到列表尾
                if (to > allRows.size()) {
                    to = allRows.size();
                }
                pageRows = allRows.subList(from, to);
            }
            // Asm：PagedResultBuilder 包装当前页
            return purchaseOrderMapper.toPagePurchaseWarningProductsResponse(pageRows, total, pageNo, pageSize);
        } catch (BusinessException ex) {
            // 业务门禁异常原样抛出，不包装成查询失败
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("pagePurchaseWarningProducts failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_PO_102, "预警快捷选品查询失败");
        }
    }

    /**
     * 可关联科室采购申请分页（IF-WHS-PO-12）：仅 {@link SupplyType#DEPT_PURCHASE_REQUEST} + {@link SupplyStatus#AGREE}，
     * 排除已被未删除药库订货单以 {@code original_bus_no} 占用的单号。
     *
     * @param request 可选 {@code keyword}、申请时段与分页窗口
     * @return 按 {@code busNo} 聚合的可关联申请行
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 分页非法；
     *                            {@link ErrorCodeConstants#WHS_PO_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_PO_102} 列表查询失败
     */
    public PageLinkableDeptPurchaseRequestsResponse pageLinkableDeptPurchaseRequests(
        PageLinkableDeptPurchaseRequestsRequest request) {
        // Gate：分页窗口与机构上下文
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_PO_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");

        // Pre：组装供应单据列表查询（固定科室采购申请 + 已同意）
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_PURCHASE_REQUEST.getCode());
        listQuery.setStatusEnum(SupplyStatus.AGREE.getCode());
        // purposeLocationId=0/空：不按科室收窄；否则按申请科室药房筛选
        Long purposeLocationId = request.getPurposeLocationId();
        if (purposeLocationId != null
            && !Long.valueOf(InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID)
                .equals(purposeLocationId)) {
            listQuery.setPurposeLocationId(purposeLocationId);
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
            // Cmd：按 bus_no 去重分页查询已同意科室采购申请
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<LinkableDeptPurchaseRequestListRowDto> records =
                this.purchaseOrderMapper.toLinkableDeptPurchaseRequestListAggregated(busNos, lines);
            this.enrichLinkableDeptPurchaseRequestListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            PageLinkableDeptPurchaseRequestsResponse response =
                this.purchaseOrderMapper.toPageLinkableDeptPurchaseRequestsAggregatedResponse(
                    mergedTotal, pageNo, pageSize, records);
            // —— 排除已被未删除订货单引用的申请单（当前页内存过滤，与进货可关联订货同构）
            this.filterAlreadyReferencedDeptPurchaseRequestsFromLinkablePage(response);
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageLinkableDeptPurchaseRequests failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_PO_102, "可关联科室采购申请查询失败");
        }
    }

    /**
     * 按科室采购申请单号拉取订货草稿预填数据（IF-WHS-PO-13；只读，不写库）。
     *
     * @param request 须含非空 {@code originalBusNo}
     * @return 申请头摘要与订货预填明细行
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_001} 参数非法；
     *                            {@link ErrorCodeConstants#WHS_PO_106} 申请不可引用
     */
    public GetLinkableDeptPurchaseRequestDetailResponse getLinkableDeptPurchaseRequestDetail(
        GetLinkableDeptPurchaseRequestDetailRequest request) {
        // Gate：originalBusNo 与机构上下文
        if (request == null || StringUtils.isEmpty(request.getOriginalBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "originalBusNo 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        String originalBusNo = request.getOriginalBusNo().trim();

        // Pre：校验申请可引用并回读明细行
        this.validateOriginalDeptPurchaseRequestForPurchaseOrder(originalBusNo, null);

        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> deptLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (deptLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "来源科室采购申请不存在");
        }

        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : deptLines) {
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
            log.warn("batchGetProductByIds degraded for linkable dept purchase request detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionDisplayTerminologyAssembler.loadSelectionTerminologyMaps();

        // Asm：映射订货预填响应并富化产品展示
        GetLinkableDeptPurchaseRequestDetailResponse res =
            purchaseOrderMapper.toGetLinkableDeptPurchaseRequestDetailResponse(deptLines, products);
        this.enrichLinkableDeptPurchaseRequestPurposeLocationName(res);
        this.enrichPurchaseOrderDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichPurchaseOrderDetailLines(res.getLines());
        return res;
    }

    /**
     * 预警 ids 快捷选品：批量读目录后按启用/表名过滤并内存分页。
     *
     * @param request  选品请求（含表名与分页）
     * @param idFilter 产品定义主键集合
     * @return 选品分页
     */
    private PageProductsForPurchaseResponse pageProductsForPurchaseByIds(
        PageProductsForPurchaseRequest request,
        List<Long> idFilter) {
        String productTableName = request.getProductTableName().trim();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : idFilter) {
            // 跳过空 id，避免 NPE lookup
            if (id == null) {
                continue;
            }
            // 去重，避免重复 lookup 与重复行
            if (!seen.add(id)) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(productTableName);
            lookup.setProductDefinitionId(id);
            lookups.add(lookup);
        }
        try {
            // Pre：批量加载目录读模型（B.6.4）
            Map<String, ProductDefinitionDisplayDto> productMap =
                this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            List<ProductDefinitionDisplayDto> matched = new ArrayList<>();
            for (ProductDefinitionBatchLookup lookup : lookups) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
                ProductDefinitionDisplayDto pr = productMap.get(key);
                // 未命中目录则丢弃（可能已删或 id 无效）
                if (pr == null) {
                    continue;
                }
                // 停用目录不可订
                if (Boolean.FALSE.equals(pr.getActiveFlag())) {
                    continue;
                }
                matched.add(pr);
            }
            int pageNo = 1;
            if (request.getPageNo() != null) {
                pageNo = request.getPageNo();
            }
            int pageSize = 20;
            if (request.getPageSize() != null) {
                pageSize = request.getPageSize();
            }
            long total = matched.size();
            int from = (pageNo - 1) * pageSize;
            List<ProductDefinitionDisplayDto> pageSlice;
            // 页越界空页
            if (from >= matched.size()) {
                pageSlice = List.of();
            } else {
                int to = from + pageSize;
                if (to > matched.size()) {
                    to = matched.size();
                }
                pageSlice = matched.subList(from, to);
            }
            PageProductsForPurchaseResponse response =
                purchaseOrderMapper.toPageProductsForPurchaseResponse(pageSlice, total, pageNo, pageSize);
            this.enrichPurchaseProductSelectionRows(response.getContent());
            // Asm：返回 ids 收窄后的选品页
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageProductsForPurchaseByIds failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_PO_102, "采购选品查询失败");
        }
    }

    /**
     * 将预警命中装配为弹窗行，并按关键字过滤名称/编码/拼音。
     *
     * @param hits    解析命中
     * @param keyword 可选关键字
     * @return 全量行（未分页）
     */
    private List<PurchaseWarningProductRowDto> assemblePurchaseWarningRows(
        List<PurchaseOrderWarningProductResolver.PurchaseWarningHit> hits,
        String keyword) {
        // 无命中直接空列表，省一批目录加载
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (PurchaseOrderWarningProductResolver.PurchaseWarningHit hit : hits) {
            // 键不完整则无法组 compositeKey
            if (hit == null || hit.getItemId() == null || StringUtils.isEmpty(hit.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(hit.getItemTable());
            lookup.setProductDefinitionId(hit.getItemId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> products =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        String kw = null;
        // 有关键字才做小写规范化，供名称/编码/拼音匹配
        if (StringUtils.isNotEmpty(keyword)) {
            kw = keyword.trim().toLowerCase();
        }
        List<PurchaseWarningProductRowDto> rows = new ArrayList<>();
        for (PurchaseOrderWarningProductResolver.PurchaseWarningHit hit : hits) {
            if (hit == null || hit.getItemId() == null || StringUtils.isEmpty(hit.getItemTable())) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(hit.getItemTable(), hit.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            // 目录缺失或停用则不展示，避免不可订品种进入勾选
            if (pr == null || Boolean.FALSE.equals(pr.getActiveFlag())) {
                continue;
            }
            // 关键字未命中则过滤
            if (kw != null && !this.matchesPurchaseWarningKeyword(pr, kw)) {
                continue;
            }
            PurchaseWarningProductRowDto row = new PurchaseWarningProductRowDto();
            row.setProductDefinitionId(pr.getProductDefinitionId());
            row.setProductTableName(pr.getProductTableName());
            row.setProductBusNo(pr.getProductBusNo());
            row.setProductName(pr.getProductName());
            row.setSpecification(pr.getTotalVolume());
            row.setHitExpire(hit.isHitExpire());
            row.setHitLowStock(hit.isHitLowStock());
            row.setLatestExpirationDate(hit.getLatestExpirationDate());
            row.setRemainingDays(hit.getRemainingDays());
            row.setHospitalWidePackageQty(hit.getHospitalWidePackageQty());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 预警弹窗关键字：匹配名称、编码、拼音、五笔（小写）。
     *
     * @param pr      目录展示
     * @param keyword 已 trim+lower 的关键字
     * @return true 命中
     */
    private boolean matchesPurchaseWarningKeyword(ProductDefinitionDisplayDto pr, String keyword) {
        // 任一检索字段包含关键字即保留该行
        if (this.containsIgnoreCase(pr.getProductName(), keyword)
            || this.containsIgnoreCase(pr.getProductBusNo(), keyword)
            || this.containsIgnoreCase(pr.getMerchandiseName(), keyword)
            || this.containsIgnoreCase(pr.getPyStr(), keyword)
            || this.containsIgnoreCase(pr.getWbStr(), keyword)) {
            return true;
        }
        return false;
    }

    /**
     * 源串转小写后是否包含关键字。
     *
     * @param source  可空
     * @param keyword 小写关键字
     * @return true 包含
     */
    private boolean containsIgnoreCase(String source, String keyword) {
        // 空源串视为不匹配，避免 NPE
        if (StringUtils.isEmpty(source)) {
            return false;
        }
        return source.toLowerCase().contains(keyword);
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

    /**
     * 校验 {@code originalBusNo} 指向的科室采购申请可被订货引用（须已同意、类型正确，且未被其它订货占用）。
     *
     * @param originalBusNo             来源申请单号
     * @param excludePurchaseOrderBusNo 改单时排除的本订货单号，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#WHS_PO_106} 申请不存在、未同意、类型不符或已占用
     */
    private void validateOriginalDeptPurchaseRequestForPurchaseOrder(
        String originalBusNo,
        String excludePurchaseOrderBusNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> deptLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (deptLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "来源科室采购申请不存在");
        }
        for (SupplyRequestModel line : deptLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_PURCHASE_REQUEST.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "来源单据类型不是科室采购申请");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "科室采购申请未同意，禁止引用");
            }
        }
        if (this.isDeptPurchaseRequestReferencedByUndeletedPurchaseOrder(originalBusNo, excludePurchaseOrderBusNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_106, "该科室采购申请已被其他订货单引用");
        }
    }

    /**
     * 可关联申请列表：剔除已被未删除药库订货单引用的申请单号（当前页内存过滤）。
     *
     * @param response 分页响应，就地改写 {@code content}
     */
    private void filterAlreadyReferencedDeptPurchaseRequestsFromLinkablePage(
        PageLinkableDeptPurchaseRequestsResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return;
        }
        Set<String> skippedBusNos = new HashSet<>();
        List<LinkableDeptPurchaseRequestListRowDto> kept = new ArrayList<>();
        for (LinkableDeptPurchaseRequestListRowDto row : response.getContent()) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String busNo = row.getBusNo().trim();
            if (skippedBusNos.contains(busNo)) {
                continue;
            }
            if (this.isDeptPurchaseRequestReferencedByUndeletedPurchaseOrder(busNo, null)) {
                skippedBusNos.add(busNo);
                continue;
            }
            kept.add(row);
        }
        response.setContent(kept);
    }

    /**
     * 判断科室采购申请是否已被未删除的 {@link SupplyType#CABINET_PURCHASE_ORDER} 以 {@code original_bus_no} 引用。
     *
     * @param deptRequestBusNo          科室采购申请业务单号
     * @param excludePurchaseOrderBusNo 改单时排除的本订货单号，可 blank
     * @return 已被其它订货单引用时 {@code true}
     */
    private boolean isDeptPurchaseRequestReferencedByUndeletedPurchaseOrder(
        String deptRequestBusNo,
        String excludePurchaseOrderBusNo) {
        if (StringUtils.isEmpty(deptRequestBusNo)) {
            return false;
        }
        return supplyRequestQueryService.existsUndeletedSupplyByOriginalBusNo(
            deptRequestBusNo.trim(),
            SupplyType.CABINET_PURCHASE_ORDER.getCode(),
            excludePurchaseOrderBusNo);
    }

    /**
     * 可关联申请列表聚合行展示名批量富化（制单人/审批人/科室药房/状态文案）。
     *
     * @param rows  当前页聚合行
     * @param lines 同页 busNo 对应的供应请求明细
     */
    private void enrichLinkableDeptPurchaseRequestListDisplayNames(
        List<LinkableDeptPurchaseRequestListRowDto> rows,
        List<SupplyRequestModel> lines) {
        if (rows == null || rows.isEmpty() || lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = this.buildDeptPurchaseRequestHeadLineByBusNo(lines);
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
        Set<Long> purposeLocationIds = new HashSet<>();
        for (LinkableDeptPurchaseRequestListRowDto row : rows) {
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
        for (LinkableDeptPurchaseRequestListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
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
     * 预填响应：补齐申请科室药房展示名。
     *
     * @param res 可关联申请预填响应
     */
    private void enrichLinkableDeptPurchaseRequestPurposeLocationName(GetLinkableDeptPurchaseRequestDetailResponse res) {
        if (res == null || res.getPurposeLocationId() == null) {
            return;
        }
        Optional<Location> locOpt = this.locationQueryService.findOptionalById(res.getPurposeLocationId());
        locOpt.ifPresent(loc -> {
            if (StringUtils.isNotEmpty(loc.getName())) {
                res.setPurposeLocationName(loc.getName().trim());
            }
        });
    }

    /**
     * 按单号选取「头行」：同号多行时取 {@code apply_time} 最大者。
     *
     * @param detailLines 列表同页明细
     * @return busNo → 代表头行
     */
    private Map<String, SupplyRequestModel> buildDeptPurchaseRequestHeadLineByBusNo(List<SupplyRequestModel> detailLines) {
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
            LocalDateTime prevApply = prev.getApplyTime();
            LocalDateTime curApply = line.getApplyTime();
            if (curApply != null && (prevApply == null || curApply.isAfter(prevApply))) {
                best.put(bn, line);
            }
        }
        return best;
    }
}
