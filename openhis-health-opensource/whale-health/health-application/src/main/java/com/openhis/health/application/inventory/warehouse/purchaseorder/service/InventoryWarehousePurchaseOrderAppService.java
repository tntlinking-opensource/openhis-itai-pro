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

package com.openhis.health.application.inventory.warehouse.purchaseorder.service;

import com.openhis.health.application.inventory.warehouse.purchaseorder.mapper.InventoryWarehousePurchaseOrderMapper;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.PurchaseOrderLineDto;
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
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inventory.warehouse.purchaseorder.dto.ProductDefinitionItemDto;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionPurchasePageLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药库订货单（{@link SupplyType#CABINET_PURCHASE_ORDER}）应用编排。
 *
 * <p>同意订货单仅更新供应请求审批状态，不在此服务生成进货单；进货由进货单（inbound receipt）模块处理。</p>
 * <p>列表与门禁以 {@code purpose_location_id}（目的药库）为锚点；
 * {@link #ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID} 为「全部药库」筛选用占位值，不是真实 {@link Location} 主键。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehousePurchaseOrderAppService {

    private static final long CABINET_PURCHASE_TYPE_SORT = 1L;

    /** 列表筛选「全部药库」时传入的 purposeLocationId 占位（0 非库位主键）。 */
    public static final long ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID = 0L;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final PageWindowGate pageWindowGate;
    private final PractitionerQueryService practitionerQueryService;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final LocationQueryService locationQueryService;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final InventoryWarehousePurchaseOrderMapper purchaseOrderMapper;

    /**
     * 加载订货页主数据（管辖药库、供应商下拉）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 药库/供应商选项及「全部药库」列表筛选项
     */
    public LoadPurchaseOrderFormInitResponse loadPurchaseOrderFormInit(LoadPurchaseOrderFormInitRequest request) {
        // Gate：机构会话上下文
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        // Pre：装载管辖药库与启用供应商
        List<Location> warehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(
            this.locationQueryService.listAllActiveWarehouses());
        List<com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel> suppliers =
            supplierQueryService.listSuppliersByTypeAndActive(
                com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
        List<com.openhis.health.application.inventory.warehouse.purchaseorder.dto.WarehouseOptionDto> whOpts =
            purchaseOrderMapper.toWarehouseOptionDtoList(warehouses);
        List<com.openhis.health.application.inventory.warehouse.purchaseorder.dto.SupplierOptionDto> supOpts =
            purchaseOrderMapper.toSupplierOptionDtoList(suppliers);
        // Asm：映射页初始化响应并注入「全部药库」筛选项
        LoadPurchaseOrderFormInitResponse res = purchaseOrderMapper.toLoadPurchaseOrderFormInitResponse(whOpts, supOpts);
        com.openhis.health.application.inventory.warehouse.purchaseorder.dto.WarehouseOptionDto allOpt =
            new com.openhis.health.application.inventory.warehouse.purchaseorder.dto.WarehouseOptionDto();
        allOpt.setLocationId(ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID);
        allOpt.setDisplayName("全部");
        allOpt.setActive(Boolean.TRUE);
        res.setAllWarehousesOption(allOpt);
        return res;
    }

    /**
     * 分页查询订货单列表（按 busNo 聚合展示）。
     *
     * @param request 含分页窗口、目的药库与状态筛选
     * @return 聚合后的列表行与分页元数据
     */
    public PagePurchaseOrdersResponse pagePurchaseOrders(PagePurchaseOrdersRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_PO_001, "分页参数不能为空");
        this.validateOptionalPurchaseTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_PO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForPurchaseOrderGate();

        // Pre：组装列表查询（全部药库展开或单库门禁）
        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.CABINET_PURCHASE_ORDER.getCode());
        Long filterLocationId = request.getPurposeLocationId();
        if (filterLocationId != null && filterLocationId.longValue() == ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID) {
            // 「全部药库」：展开为当前用户可见药库 ID 列表
            if (!allowed.isEmpty()) {
                query.setPurposeLocationIds(new ArrayList<>(allowed));
            } else {
                // 无可见药库时按租户收窄，避免空 IN 导致全表扫描
                Long tenantId = TenantUtils.getCurrentTenantId();
                if (tenantId != null) {
                    query.setTenantId(tenantId);
                } else {
                    query.setPurposeLocationIds(new ArrayList<>());
                }
            }
        } else {
            // 单药库筛选：须落在管辖门控内（防横向越权）
            this.assertLocationAllowed(filterLocationId, allowed);
            query.setPurposeLocationId(filterLocationId);
        }
        if (request.getStatusEnum() != null) {
            SupplyStatus st = EnumUtil.requireFromSortOrder(SupplyStatus.class, request.getStatusEnum().longValue());
            query.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, query);
        query.setApplyTimeStart(request.getApplyTimeFrom());
        query.setApplyTimeEnd(request.getApplyTimeTo());

        try {
            // Cmd：领域分页查询供应请求
            Page<com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel> page =
                supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            // Asm：映射列表分页响应
            return purchaseOrderMapper.toPagePurchaseOrdersResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageWarehouseDocumentDetail failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_PO_102, "订货单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读订货单详情（含明细行与产品展示增强）。
     *
     * @param request 须含 busNo
     * @return 头行与明细
     */
    public GetPurchaseOrderDetailResponse getPurchaseOrderDetail(GetPurchaseOrderDetailRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }
        // Pre：按单号读供应请求并批量加载产品定义
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "订货单不存在");
        }
        this.assertCabinetPurchaseOrder(lines);

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
        Map<String, ProductDefinitionDisplayDto> products = productDefinitionBatchLoader.loadAsMap(lookups);
        // Asm：映射详情响应
        return purchaseOrderMapper.toGetPurchaseOrderDetailResponse(lines, products);
    }

    /**
     * 新建订货单草稿并落库供应请求。
     *
     * @param request 含目的药库、供应商与明细行
     * @return 新建 busNo
     */
    public CreatePurchaseOrderResponse createPurchaseOrder(CreatePurchaseOrderRequest request) {
        // Gate：请求体、明细、目的位置与机构/库房门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        this.validateOptionalPurchaseTypeEnum(request.getTypeEnum());
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
        // 明细行制单时刻 occurrence_time 由前端录入，落库供应请求行
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
            String busNo = supplyRequestDomainService.createPurchaseOrder(cmd);
            // Asm：映射新建响应
            return purchaseOrderMapper.toCreatePurchaseOrderResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "订货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 修订订货单（同号全量对齐明细，须可编辑态）。
     *
     * @param request 含 busNo、目的药库与明细行
     * @return 修订后 busNo
     */
    public UpdatePurchaseOrderResponse updatePurchaseOrder(UpdatePurchaseOrderRequest request) {
        // Gate：busNo、明细、目的位置与可编辑态
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
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

        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "订货单不存在");
        }
        this.assertCabinetPurchaseOrder(existing);

        // Pre：读侧校验当前状态允许编辑
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
            String busNo = supplyRequestDomainService.createPurchaseOrder(cmd);
            // Asm：映射修订响应
            return purchaseOrderMapper.toUpdatePurchaseOrderResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "订货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 提交订货单进入待审批态。
     *
     * @param request 含业务单号 busNo
     * @return 提交成功标志
     */
    public SubmitPurchaseOrderApprovalResponse submitPurchaseOrderApproval(SubmitPurchaseOrderApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_PO_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(purchaseOrderMapper.toSubmitApprovalCommand(request,
                currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录"),
                applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_PO_003);
        }
        // Asm：映射提交响应
        return purchaseOrderMapper.toSubmitPurchaseOrderApprovalResponse();
    }

    /**
     * 撤回订货单审批（回到可编辑态）。
     *
     * @param request 须含 busNo
     * @return 撤回成功标志
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
        return purchaseOrderMapper.toWithdrawPurchaseOrderApprovalResponse();
    }

    /**
     * 同意订货单（仅更新供应请求审批状态，不生成进货单）。
     *
     * @param request 须含 busNo；须未重复同意
     * @return 同意成功标志
     */
    public AgreePurchaseOrderResponse agreePurchaseOrder(AgreePurchaseOrderRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }

        // Pre：读侧校验未重复同意
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_104, "订货单已同意，禁止重复同意");
        }

        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_PO_101, "用户未登录");

        try {
            // Cmd：领域状态机同意订货单
            supplyRequestDomainService.agreeRequest(purchaseOrderMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        }
        // Asm：映射同意响应
        return purchaseOrderMapper.toAgreePurchaseOrderResponse();
    }

    /**
     * 驳回订货单并记录原因。
     *
     * @param request 须含 busNo 与 rejectReason
     * @return 驳回成功标志
     */
    public RejectPurchaseOrderResponse rejectPurchaseOrder(RejectPurchaseOrderRequest request) {
        // Gate：busNo 与驳回原因
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getRejectReason())) {
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
     * 删除订货单（仅允许草稿/可删态）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
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

    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

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
     * 分页查询订货选品（采购目录）。
     *
     * @param request 分页窗口与选品筛选条件
     * @return 产品定义展示行分页
     */
    public PageProductsForPurchaseResponse pageProductsForPurchase(PageProductsForPurchaseRequest request) {
        // Gate：分页窗口
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_PO_001, "分页参数不能为空");
        // Pre：组装产品定义分页查询
        ProductDefinitionPageQuery query = new ProductDefinitionPageQuery();
        query.setProductTableName(request.getProductTableName());
        query.setCategoryCode(request.getCategoryCode());
        query.setActiveFlag(request.getActiveFlag());
        query.setSupplierId(request.getSupplierId());
        query.setManufacturer(request.getManufacturer());
        query.setIncludeInventory(request.getIncludeInventory());
        PageQueryBinder.bind(request, query);
        query.setSortField(request.getSortField());
        query.setSortOrder(request.getSortOrder());
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
     * 采购选品行富化：从已术语回填的展示 DTO 拷贝类目展示名，并补实例维销售价。
     *
     * @param rows 当前页选品行（Mapper 已映射 code；{@code categoryCodeDictText} 来自分页展示 DTO）
     */
    private void enrichPurchaseProductSelectionRows(List<ProductDefinitionItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (ProductDefinitionItemDto row : rows) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isNotBlank(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                BigDecimal salePrice = this.chargeItemDefinitionQueryService.resolveFirstSalePriceValue(
                    row.getProductTableName().trim(), row.getProductDefinitionId());
                row.setPrice(salePrice);
            }
        }
    }

    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "busNo 不能为空");
        }
    }

    private void preAssertCabinetPurchaseOrder(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "订货单不存在");
        }
        this.assertCabinetPurchaseOrder(lines);
    }

    /** 校验供应请求行类型为药库订货；{@code lines == null} 时跳过（与进货/出库守卫同源）。 */
    private void assertCabinetPurchaseOrder(List<SupplyRequestModel> lines) {
        if (lines == null) {
            return;
        }
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_PURCHASE_ORDER.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_103, "单据类型非药库订货单");
            }
        }
    }

    private Set<Long> resolveGatedWarehouseLocationIdsForPurchaseOrderGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private void assertLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_PO_001, "药库位置不在当前机构可选范围");
        if (locationId == null || !allowed.contains(locationId)) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "药库位置不在当前机构可选范围");
        }
    }

    private void validateOptionalPurchaseTypeEnum(Integer typeEnum) {
        if (typeEnum == null) {
            return;
        }
        int expected = (int) CABINET_PURCHASE_TYPE_SORT;
        if (typeEnum.intValue() != expected) {
            throw new BusinessException(ErrorCodeConstants.WHS_PO_001, "typeEnum 必须为药库订货单类型");
        }
    }

    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_002, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_PO_002, "供应商不存在或不可用");
            }
            throw ex;
        }
    }

    /** 领域 {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} 映射为应用层默认错误码，其余原样抛出。 */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /** 同意流程：领域 INVALID_ARGUMENT 且文案含「已同意」时映射 {@link ErrorCodeConstants#WHS_PO_104}。 */
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
