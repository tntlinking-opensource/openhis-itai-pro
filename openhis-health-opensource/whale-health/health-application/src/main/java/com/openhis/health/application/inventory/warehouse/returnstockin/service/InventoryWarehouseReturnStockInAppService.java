/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.returnstockin.service;

import com.openhis.health.application.inventory.warehouse.returnstockin.mapper.InventoryWarehouseReturnStockInMapper;
import com.openhis.health.application.inventory.warehouse.returnstockin.request.*;
import com.openhis.health.application.inventory.warehouse.returnstockin.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseConstants;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 退库入库单（{@link SupplyType#CABINET_RETURN_STOCK_IN}）应用编排（只读台账侧 · W-05 · AS 待建）。
 *
 * <p>本单为药房/科室退库 agree 后自动生成的药库侧只读壳单（{@code originalBusNo} 指向下游退库单，初态 {@code AGREE}）。
 * 本服务<strong>不支持</strong>手工新建/改单/审批落账（{@link ErrorCodeConstants#WHS_RSI_006}）。
 * 库存变动已在下游退库 agree 时完成一次（DM §6.1.5 · {@code ReturnToWarehouseStockInLineHelper}）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseReturnStockInAppService {

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final LocationQueryService locationQueryService;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryWarehouseReturnStockInMapper returnStockInMapper;

    /**
     * 加载退库入库单页主数据（管辖药库、药房、科室与供应商下拉）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
     */
    public LoadReturnStockInFormInitResponse loadReturnStockInFormInit(LoadReturnStockInFormInitRequest request) {
        // Gate：机构会话上下文
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RSI_101, "机构上下文缺失");
        try {
            // Pre：装载管辖范围内启用位置
            List<Location> warehouses = this.locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Location> wards = this.locationQueryService.listAllActiveWards();
            List<Location> gatedWards = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(wards);
            List<Location> pharmacies = this.locationQueryService.listAllActivePharmacies();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel> suppliers =
                this.supplierQueryService.listSuppliersByTypeAndActive(
                    com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
            // Asm：映射页初始化响应
            return this.returnStockInMapper.toLoadReturnStockInFormInitResponse(
                this.returnStockInMapper.toWarehouseOptionDtoList(gatedWarehouses),
                this.returnStockInMapper.toWarehouseOptionDtoList(gatedWards),
                this.returnStockInMapper.toWarehouseOptionDtoList(gatedPharmacies),
                this.returnStockInMapper.toSupplierOptionDtoList(suppliers));
        } catch (RuntimeException ex) {
            log.warn("loadReturnStockInFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_102, "退库入库页主数据加载失败");
        }
    }

    /**
     * 分页查询退库入库壳单列表（按 busNo 聚合展示）。
     *
     * @param request 含分页窗口、目的药库与状态筛选
     * @return 聚合后的列表行与分页元数据
     */
    public PageReturnStockInsResponse pageReturnStockIns(PageReturnStockInsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_001, "请求不能为空");
        }
        Long purposeLocationId = request.getPurposeLocationId();
        if (purposeLocationId == null) {
            // 兼容旧字段：未传 purpose 时回落 sourceLocationId
            purposeLocationId = request.getSourceLocationId();
        }
        if (purposeLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_001, "purposeLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_RSI_001, "分页参数不能为空");
        this.validateOptionalReturnStockInTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RSI_101, "机构上下文缺失");
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIds();

        // Pre：组装列表查询
        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.CABINET_RETURN_STOCK_IN.getCode());
        // Pre：purposeLocationId=0 表示当前用户可见的全部药库；否则单库且须在管辖门控内
        if (Long.valueOf(InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID)
            .equals(purposeLocationId)) {
            if (allowedWarehouses.isEmpty()) {
                // 无可见药库：占位 id 收窄为空页，避免无约束全表扫描
                query.setPurposeLocationIds(
                    List.of(InventoryWarehouseConstants.LIST_FILTER_NO_VISIBLE_WAREHOUSE_PLACEHOLDER_ID));
            } else {
                // 多药库 IN 查询：下推当前用户可见 id 集合
                query.setPurposeLocationIds(new ArrayList<>(allowedWarehouses));
            }
        } else {
            // 单药库筛选（防横向越权）
            this.assertWarehouseLocationAllowed(purposeLocationId, allowedWarehouses);
            query.setPurposeLocationId(purposeLocationId);
        }
        if (request.getStatusEnum() != null) {
            SupplyStatus st = EnumUtil.requireFromSortOrder(SupplyStatus.class, request.getStatusEnum().longValue());
            query.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, query);
        query.setApplyTimeStart(request.getApplyTimeFrom());
        query.setApplyTimeEnd(request.getApplyTimeTo());
        try {
            // Cmd：领域分页查询
            Page<com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            // Asm：映射分页响应
            return this.returnStockInMapper.toPageReturnStockInsResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageReturnStockIns failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_102, "退库入库单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读退库入库壳单详情（含明细行与展示增强）。
     *
     * @param request 含业务单号 busNo
     * @return 单头与明细
     */
    public GetReturnStockInDetailResponse getReturnStockInDetail(GetReturnStockInDetailRequest request) {
        // Gate：busNo 非空，否则无法定位供应请求聚合
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_001, "busNo 不能为空");
        }
        // Pre：按 busNo 回读供应请求行
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            // 同号无行：按不存在处理，避免空壳响应误导前端
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_103, "退库入库单不存在");
        }
        this.assertReturnStockIn(lines);

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
            log.warn("batchGetProductByIds degraded for return inbound detail", ex);
            products = new HashMap<>();
        }
        // Asm：映射详情响应
        return this.returnStockInMapper.toGetReturnStockInDetailResponse(lines, products);
    }

    /**
     * 不支持手工新建退库入库单（须由药房/科室退库 agree 生成壳单）。
     *
     * @param request 单头与明细
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public CreateReturnStockInResponse createReturnStockIn(CreateReturnStockInRequest request) {
        // Gate：退库入库壳单只读，拒绝一切手工新建
        return this.rejectManualReturnStockInWrite("新建");
    }

    /**
     * 不支持手工修订退库入库壳单。
     *
     * @param request 含 busNo 与明细
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public UpdateReturnStockInResponse updateReturnStockIn(UpdateReturnStockInRequest request) {
        // Gate：退库入库壳单只读，拒绝手工修订
        return this.rejectManualReturnStockInWrite("修订");
    }

    /**
     * 退库入库壳单已终态同意，禁止提交审批流。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public SubmitReturnStockInApprovalResponse submitReturnStockInApproval(
        SubmitReturnStockInApprovalRequest request) {
        // Gate：壳单已 AGREE，无独立审批链
        return this.rejectManualReturnStockInWrite("提交审批");
    }

    /**
     * 退库入库壳单已终态同意，禁止撤回。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public WithdrawReturnStockInApprovalResponse withdrawReturnStockInApproval(
        WithdrawReturnStockInApprovalRequest request) {
        // Gate：壳单已 AGREE，禁止撤回
        return this.rejectManualReturnStockInWrite("撤回审批");
    }

    /**
     * 库存已在药房/科室退库 agree 时落账，本接口禁止再次审批。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public AgreeReturnStockInResponse agreeReturnStockIn(AgreeReturnStockInRequest request) {
        // Gate：库存已在下游退库 agree 落账，禁止二次同意
        return this.rejectManualReturnStockInWrite("审批同意");
    }

    /**
     * 系统退库入库壳单不允许驳回（无独立审批链）。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public RejectReturnStockInResponse rejectReturnStockIn(RejectReturnStockInRequest request) {
        // Gate：系统壳单无驳回路径
        return this.rejectManualReturnStockInWrite("驳回");
    }

    /**
     * 系统退库入库壳单不允许删除（追溯绑定下游退库单）。
     *
     * @param request 含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public RemoveReturnStockInResponse removeReturnStockIn(RemoveReturnStockInRequest request) {
        // Gate：壳单须保留追溯链，禁止删除
        return this.rejectManualReturnStockInWrite("删除");
    }

    /**
     * 退库入库单禁止自由选品（须从下游退库单自动生成）。
     *
     * @param request 选品请求
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public PageProductsForReturnStockInResponse pageProductsForReturnStockIn(
        PageProductsForReturnStockInRequest request) {
        // Gate：壳单明细由下游退库自动生成，禁止目录选品
        return this.rejectManualReturnStockInWrite("选品");
    }

    /**
     * 退库入库单禁止关联上游手工制单（无主动建单入口）。
     *
     * @param request 关联查询请求
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    public PageLinkableDeliveriesResponse pageLinkableDeliveries(PageLinkableDeliveriesRequest request) {
        // Gate：无主动建单入口，禁止关联上游手工单
        return this.rejectManualReturnStockInWrite("关联上游");
    }

    /**
     * 退库入库单仅允许由药房/科室退库 agree 生成；拒绝一切手工写路径。
     *
     * <p>泛型返回仅为满足调用方 {@code return} 语法；方法体始终抛出 {@link BusinessException}，永不正常返回。</p>
     *
     * @param actionLabel 操作名称（如「新建」「审批同意」），用于提示文案
     * @param <T>         调用方期望的 Response 类型（占位，不会实际实例化）
     * @return 不返回；编译器占位
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_006}
     */
    private <T> T rejectManualReturnStockInWrite(String actionLabel) {
        String label = actionLabel;
        if (label == null) {
            label = "操作";
        }
        throw new BusinessException(ErrorCodeConstants.WHS_RSI_006,
            "退库入库单由药房/科室退库同意后系统自动生成，不支持手工" + label);
    }

    /**
     * 解析岗位可见药库 id 集合（列表目的侧门控）。
     *
     * @return 启用中药库主键集合（已与管辖范围求交）
     */
    private Set<Long> resolveGatedWarehouseLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 断言给定药库主键在允许集合内。
     *
     * @param locationId 位置主键
     * @param allowed      岗位可见药库 id 集合
     */
    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_RSI_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_001, "药库位置不在当前用户可选药库范围");
        }
    }

    /**
     * 列表可选 type 收窄：若传入则须为退库入库单类型 sort 值。
     *
     * @param typeEnum 请求 typeEnum，可 null
     * @throws BusinessException {@link ErrorCodeConstants#WHS_RSI_001} 非空但类型不匹配
     */
    private void validateOptionalReturnStockInTypeEnum(Integer typeEnum) {
        if (typeEnum == null) {
            return;
        }
        int expectedSort = SupplyType.CABINET_RETURN_STOCK_IN.getSortOrder().intValue();
        if (typeEnum.intValue() != expectedSort) {
            throw new BusinessException(ErrorCodeConstants.WHS_RSI_001, "typeEnum 必须为退库入库单类型");
        }
    }

    /**
     * 断言多行共享同一 {@code CABINET_RETURN_STOCK_IN} 类型（详情读侧一致性）。
     *
     * @param lines 供应请求行
     */
    private void assertReturnStockIn(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_RETURN_STOCK_IN.getCode().equals(line.getTypeEnum())) {
                // 防止按 busNo 误读其它供应类型
                throw new BusinessException(ErrorCodeConstants.WHS_RSI_103, "单据类型非退库入库单");
            }
        }
    }
}
