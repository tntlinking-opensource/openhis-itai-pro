/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.service;

import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.pharmacy.returnupstream.dto.ReturnUpstreamLineDto;
import com.openhis.health.application.inventory.pharmacy.returnupstream.mapper.InventoryPharmacyReturnUpstreamMapper;
import com.openhis.health.application.inventory.pharmacy.returnupstream.request.*;
import com.openhis.health.application.inventory.pharmacy.returnupstream.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.module.querysupport.product.ProductDefinitionPurchasePageLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
 * 药房退库单（{@link SupplyType#PHARMACY_RETURN_TO_UPSTREAM}）应用编排。
 *
 * <p>同意后：领域落已同意态 → 退库发放 → 源药房扣减、目的药库增库存 → 追溯码出库；不上报医保进销存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPharmacyReturnUpstreamAppService {

    private static final long PHARMACY_RETURN_TYPE_SORT = 14L;

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
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final InventoryPharmacyReturnUpstreamMapper returnUpstreamMapper;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    private final com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 加载页主数据（库位、人员、状态等下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 表单初始化选项集合
     */
    public LoadReturnUpstreamFormInitResponse loadReturnUpstreamFormInit(LoadReturnUpstreamFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位与人员
        // Pre：拉取启用库位/人员并经管辖 Gate 收窄
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RU_101, "机构上下文缺失");
        try {
            List<Location> pharmacies = this.locationQueryService.listAllActivePharmacies();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<Location> warehouses = this.locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel> suppliers =
                this.supplierQueryService.listSuppliersByTypeAndActive(
                    com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
            return this.returnUpstreamMapper.toLoadReturnUpstreamFormInitResponse(
                this.returnUpstreamMapper.toWarehouseOptionDtoList(gatedPharmacies),
                this.returnUpstreamMapper.toWarehouseOptionDtoList(gatedWarehouses),
                this.returnUpstreamMapper.toSupplierOptionDtoList(suppliers));
        } catch (RuntimeException ex) {
            log.warn("loadReturnUpstreamFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RU_102, "退货页主数据加载失败");
        }
    }

    /**
     * 分页查询单据列表。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     */
    public PageReturnUpstreamsResponse pageReturnUpstreams(PageReturnUpstreamsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        // Pre：组装供应单据列表查询条件
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "请求不能为空");
        }
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_RU_001, "分页参数不能为空");
        this.validateOptionalReturnTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RU_101, "机构上下文缺失");
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIds();
        this.assertPharmacyLocationAllowed(request.getSourceLocationId(), allowedPharmacies);

        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.PHARMACY_RETURN_TO_UPSTREAM.getCode());
        query.setSourceLocationId(request.getSourceLocationId());
        if (request.getStatusEnum() != null) {
            SupplyStatus st = EnumUtil.requireFromSortOrder(SupplyStatus.class, request.getStatusEnum().longValue());
            query.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, query);
        query.setApplyTimeStart(request.getApplyTimeFrom());
        query.setApplyTimeEnd(request.getApplyTimeTo());
        try {
            Page<com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            return this.returnUpstreamMapper.toPageReturnUpstreamsResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageReturnUpstreams failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RU_102, "退货单列表查询失败");
        }
    }

    /**
     * 按业务单号查询单据详情。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     */
    public GetReturnUpstreamDetailResponse getReturnUpstreamDetail(GetReturnUpstreamDetailRequest request) {
        // Gate：busNo 不可为空
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_103, "退货单不存在");
        }
        this.assertPharmacyReturnUpstream(lines);

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
            products = this.productDefinitionBatchLoader.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for pharmacy return upstream detail", ex);
            products = new HashMap<>();
        }
        return this.returnUpstreamMapper.toGetReturnUpstreamDetailResponse(lines, products);
    }

    /**
     * 新建单据（草稿/待提交）。
     *
     * @param request 含库位、明细行等业务字段
     * @return 新建业务单号
     */
    public CreateReturnUpstreamResponse createReturn(CreateReturnUpstreamRequest request) {
        // Gate：请求体、库位与明细不可为空
        // Pre：解析操作人与制单人（医务人员档案）
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "请求不能为空");
        }
        if (StringUtils.isBlank(request.getOriginalBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "originalBusNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "退货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RU_101, "机构上下文缺失");
        this.assertReturnUpstreamLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        this.validateSupplierOptional(request.getSupplierId());
        this.assertReturnUpstreamTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.PHA_RU_001);
        this.validateOriginalPharmacyStockInForReturnUpstream(request.getOriginalBusNo().trim(), request.getSourceLocationId());
        this.assertReturnUpstreamDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnUpstreamLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RU_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.PHA_RU_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            CreateReturnCommand cmd = this.returnUpstreamMapper.toCreateReturnUpstreamCommand(
                request, operatorId, applicantId, null);
            String busNo = this.supplyRequestDomainService.createReturn(cmd);
            return this.returnUpstreamMapper.toCreateReturnUpstreamResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "退货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 更新单据（全量替换明细，仅可编辑态允许）。
     *
     * @param request 含 busNo、库位与明细
     * @return 业务单号
     */
    public UpdateReturnUpstreamResponse updateReturnUpstream(UpdateReturnUpstreamRequest request) {
        // Gate：busNo 与明细、库位不可为空
        // Pre：回读现有单并校验可编辑态
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "busNo 不能为空");
        }
        if (request.getRowVersion() != null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_105, "退货单不支持 rowVersion 乐观锁");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "退货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RU_101, "机构上下文缺失");
        this.assertReturnUpstreamLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());

        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_103, "退货单不存在");
        }
        this.assertPharmacyReturnUpstream(existing);
        SupplyRequestModel head = existing.get(0);
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = this.supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_004, "当前状态不允许编辑退货单");
        }

        this.validateSupplierOptional(request.getSupplierId());
        this.assertReturnUpstreamTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.PHA_RU_004);
        this.assertReturnUpstreamDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnUpstreamLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RU_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.PHA_RU_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        String originalSnapshot = head.getOriginalBusNo();
        try {
            CreateReturnCommand cmd = this.returnUpstreamMapper.toCreateReturnUpstreamCommandForUpdate(
                request, operatorId, applicantId, originalSnapshot);
            String busNo = this.supplyRequestDomainService.createReturn(cmd);
            return this.returnUpstreamMapper.toUpdateReturnUpstreamResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "退货参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_105, "退货单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交单据审批。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public SubmitReturnUpstreamApprovalResponse submitReturnUpstreamApproval(
        SubmitReturnUpstreamApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.PHA_RU_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            this.supplyRequestDomainService.submitApproval(
                this.returnUpstreamMapper.toSubmitApprovalCommand(
                    request,
                    this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RU_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RU_003);
        }
        return this.returnUpstreamMapper.toSubmitReturnUpstreamApprovalResponse();
    }

    /**
     * 撤回单据审批（回到可编辑态）。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public WithdrawReturnUpstreamApprovalResponse withdrawReturnUpstreamApproval(
        WithdrawReturnUpstreamApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            this.supplyRequestDomainService.withdrawApproval(
                this.returnUpstreamMapper.toWithdrawApprovalCommand(
                    request,
                    this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RU_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RU_003);
        }
        return this.returnUpstreamMapper.toWithdrawReturnUpstreamApprovalResponse();
    }

    /**
     * 药房退库单同意编排：领域落已同意态 → 退库发放 → 源药房扣减、目的药库增库存 → 追溯码出库。
     *
     * @param request 须含 busNo
     * @return 同意成功标志
     */
    public AgreeReturnUpstreamResponse agreeReturnUpstream(AgreeReturnUpstreamRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "busNo 不能为空");
        }
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RU_101, "用户未登录");

        // Pre：回读退库单并校验未重复同意
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_103, "退货单不存在");
        }
        this.assertPharmacyReturnUpstream(beforeLines);
        SupplyRequestValidationModel validation = this.supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "退货单已同意，禁止重复同意");
        }

        try {
            this.supplyRequestDomainService.agreeRequest(
                this.returnUpstreamMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_105, "退库单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertPharmacyReturnUpstream(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "同意后回读状态异常，中止退货编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "退货明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "退货明细数量无效，无法完成编排");
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
            throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "供应请求行已存在完成态发放，禁止重复同意退货");
        }

        try {
            this.supplyDeliveryDomainService.createSupplyDelivery(
                this.returnUpstreamMapper.toCreateSupplyDeliveryCommandForReturnUpstream(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "退库发放落库失败：" + ex.getMessage());
        }

        // Cmd：批量产品读模型，供拆零量与目的侧建库存行使用
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
                productByKey = this.productDefinitionBatchLoader.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during pharmacy return upstream inventory assemble", ex);
            }
        }

        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        String traceOperationType = TraceNumberRecordOperationType.supplyTypeCode(SupplyType.PHARMACY_RETURN_TO_UPSTREAM);
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems = new ArrayList<>();
        Map<Long, BigDecimal> decreaseMerged = new HashMap<>();
        List<ReturnUpstreamDestIncr> destIncrs = new ArrayList<>();

        // Cmd：组装源侧扣减、目的侧入账计划与追溯出库项
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long sourceLocationId = line.getSourceLocationId();
            Long purposeLocationId = line.getPurposeLocationId();
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.PHA_RU_104, "供应请求行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();
            String productName = this.resolveProductName(line, productByKey);
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());

            InventoryMatchKey srcKey = new InventoryMatchKey();
            srcKey.setLotNumber(resolvedLot);
            srcKey.setLocationId(sourceLocationId);
            srcKey.setItemTable(line.getItemTable());
            srcKey.setItemId(line.getItemId());
            Long sourceInventoryId = this.inventoryItemQueryService.findFirstIdByInventoryMatchKey(srcKey).orElse(null);
            if (sourceInventoryId == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "源药房未找到匹配库存行，无法扣减");
            }
            decreaseMerged.merge(sourceInventoryId, splitQty, BigDecimal::add);

            ReturnUpstreamDestIncr op = new ReturnUpstreamDestIncr();
            op.line = line;
            op.purposeLocationId = purposeLocationId;
            op.splitQty = splitQty;
            op.resolvedLot = resolvedLot;
            op.productName = productName;
            op.splitUnitCode = splitUnitCode;
            op.productDefinition = product;
            InventoryItemModel srcInv = this.inventoryItemQueryService.getInventoryItemById(sourceInventoryId);
            op.supplierId = line.getSupplierId();
            op.sourceInventoryProductionDate = null;
            op.sourceInventoryExpirationDate = null;
            if (srcInv != null) {
                if (op.supplierId == null) {
                    op.supplierId = srcInv.getSupplierId();
                }
                op.sourceInventoryProductionDate = srcInv.getProductionDate();
                op.sourceInventoryExpirationDate = srcInv.getExpirationDate();
            }
            if (op.supplierId == null && head != null) {
                op.supplierId = head.getSupplierId();
            }
            destIncrs.add(op);

            if (StringUtils.isNotBlank(line.getTraceNo())) {
                String lt = this.resolveSourceLocationTypeCode(line, head);
                for (String part : line.getTraceNo().split(GeneralSharedConstants.COMMA)) {
                    String traceNo = part.trim();
                    if (traceNo.isEmpty()) {
                        continue;
                    }
                    AppendTraceRecordsCommand.AppendTraceRecordItemCommand it =
                        new AppendTraceRecordsCommand.AppendTraceRecordItemCommand();
                    it.setItemTable(line.getItemTable().trim());
                    it.setItemId(line.getItemId());
                    it.setLocationTypeEnum(lt);
                    it.setLocationId(sourceLocationId);
                    it.setLocationStoreId(null);
                    it.setLotNumber(resolvedLot);
                    it.setTraceNo(traceNo);
                    it.setStatusEnum(TraceDirection.OUT.getCode());
                    it.setUnitCode(splitUnitCode);
                    it.setOperationType(traceOperationType);
                    it.setBusNo(busNoTrim);
                    traceItems.add(it);
                }
            }
        }

        // —— 源药房按库存行 id 合并扣减（先扣后加，避免先加后扣窗口）。——
        try {
            this.inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseMerged);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "源药房库存扣减失败：" + ex.getMessage());
        }

        // —— 目的药库逐行入账；命中既有行则加量，否则按四元组新建新库存项。——
        for (ReturnUpstreamDestIncr op : destIncrs) {
            SupplyRequestModel line = op.line;
            if (op.supplierId == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "源药房库存行缺少供应商标识，无法在目的药库新建账面");
            }
            line.setSupplierId(op.supplierId);
            InventoryMatchKey dstKey = new InventoryMatchKey();
            dstKey.setSupplierId(op.supplierId);
            dstKey.setLotNumber(op.resolvedLot);
            dstKey.setLocationId(op.purposeLocationId);
            dstKey.setItemTable(line.getItemTable());
            dstKey.setItemId(line.getItemId());
            Long destInventoryId = this.inventoryItemQueryService.findFirstIdByInventoryMatchKey(dstKey).orElse(null);
            try {
                if (destInventoryId != null) {
                    IncreaseQuantityCommand incCmd =
                        this.inboundReceiptMapper.toIncreaseQuantityCommand(destInventoryId, op.splitQty, operatorId);
                    this.inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    CreateInventoryItemCommand createCmd = this.inboundReceiptMapper.toCreateInventoryItemCommand(
                        line,
                        op.purposeLocationId,
                        op.productName,
                        op.resolvedLot,
                        op.splitQty,
                        op.splitUnitCode,
                        operatorId,
                        op.productDefinition);
                    if (createCmd.getItem() != null) {
                        if (createCmd.getItem().getProductionDate() == null
                            && op.sourceInventoryProductionDate != null) {
                            createCmd.getItem().setProductionDate(op.sourceInventoryProductionDate);
                        }
                        if (createCmd.getItem().getExpirationDate() == null
                            && op.sourceInventoryExpirationDate != null) {
                            createCmd.getItem().setExpirationDate(op.sourceInventoryExpirationDate);
                        }
                    }
                    this.inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "目的药库库存增加失败：" + ex.getMessage());
            }
        }

        if (!traceItems.isEmpty()) {
            try {
                AppendTraceRecordsCommand traceCmd = new AppendTraceRecordsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                this.traceNumberRecordDomainService.appendTraceRecords(traceCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "追溯码出库记录失败：" + ex.getMessage());
            }
        }

        return this.returnUpstreamMapper.toAgreeReturnUpstreamResponse(true);
    }

    /**
     * 驳回单据。
     *
     * @param request 须含 busNo 与驳回原因
     * @return 驳回成功标志
     */
    public RejectReturnUpstreamResponse rejectReturnUpstream(RejectReturnUpstreamRequest request) {
        // Gate：busNo 与驳回原因不可为空
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "驳回原因不能为空");
        }
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RU_101, "用户未登录");
        try {
            this.supplyRequestDomainService.rejectRequest(
                this.returnUpstreamMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RU_003);
        }
        return this.returnUpstreamMapper.toRejectReturnUpstreamResponse(true);
    }

    /**
     * 删除单据（仅草稿/可删态）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     */
    public RemoveReturnUpstreamResponse removeReturnUpstream(RemoveReturnUpstreamRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.PHA_RU_101, "用户未登录");
        try {
            this.supplyRequestDomainService.removeByBusNo(
                this.returnUpstreamMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.PHA_RU_005);
        }
        return this.returnUpstreamMapper.toRemoveReturnUpstreamResponse(true);
    }

    /**
     * 分页查询选品（采购目录 + 可选库存）。
     *
     * @param request 分页窗口与选品筛选条件
     * @return 产品定义展示行分页
     */
    public PageProductsForReturnUpstreamResponse pageProductsForReturnUpstream(
        PageProductsForReturnUpstreamRequest request) {
        // Gate：分页窗口与机构上下文
        // Pre：组装产品定义分页查询
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_RU_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RU_101, "机构上下文缺失");
        this.assertProductTableNameWhitelistForSelection(request.getProductTableName());
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
            Page<ProductDefinitionDisplayDto> page = this.productDefinitionPurchasePageLoader.pageForPurchase(query);
            PageProductsForReturnUpstreamResponse response =
                this.returnUpstreamMapper.toPageProductsForReturnUpstreamResponse(page);
            this.enrichProductSelectionRows(response.getContent());
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageProductsForReturnUpstream failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RU_102, "退货选品查询失败");
        }
    }

    /**
     * 分页查询可关联的来源单据。
     *
     * @param request 须含库位与分页窗口
     * @return 可引用来源单分页
     */
    public PageLinkableReturnSourceDocumentsResponse pageLinkableReturnSourceDocuments(PageLinkableReturnSourceDocumentsRequest request) {
        // Gate：库位与分页窗口不可为空
        // Pre：查询已同意的来源单据
        if (request == null || request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_RU_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_RU_101, "机构上下文缺失");
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIds();
        this.assertPharmacyLocationAllowed(request.getSourceLocationId(), allowedPharmacies);

        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.PHARMACY_STOCK_IN.getCode());
        query.setStatusEnum(SupplyStatus.AGREE.getCode());
        query.setPurposeLocationId(request.getSourceLocationId());
        PageQueryBinder.bind(request, query);
        try {
            Page<com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            return this.returnUpstreamMapper.toPageLinkableReturnSourceDocumentsResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageLinkableReturnSourceDocuments failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_RU_102, "可关联入库单查询失败");
        }
    }

    private void enrichProductSelectionRows(List<ProductDefinitionItemDto> rows) {
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

    private void validateOriginalPharmacyStockInForReturnUpstream(String originalBusNo, Long sourcePharmacyId) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> inboundLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (inboundLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_002, "来源入库单不存在");
        }
        boolean matchedPharmacy = false;
        for (SupplyRequestModel line : inboundLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_STOCK_IN.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_002, "来源单据类型不是药房入库单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_002, "来源入库单未同意，不可引用");
            }
            if (Objects.equals(sourcePharmacyId, line.getPurposeLocationId())) {
                matchedPharmacy = true;
            }
        }
        if (!matchedPharmacy) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_002, "来源入库单与当前源药房不匹配");
        }
    }

    private void assertReturnUpstreamDraftLinesWithinSourceAvailableInventory(
        Long sourceLocationId,
        List<ReturnUpstreamLineDto> lines) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (ReturnUpstreamLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "退货明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "退货明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.PHA_RU_001, "退货明细数量须为正数");
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
                        throw new BusinessException(
                            ErrorCodeConstants.PHA_RU_001,
                            "源库该批号可用量不足，请减少退货数量或刷新库存后再试");
                    }
                }
            }
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.PHA_RU_001,
                        "源库可用量不足，请减少退货数量或刷新库存后再试");
                }
            }
        }
    }

    private Set<Long> resolveGatedPharmacyLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActivePharmacies());
    }

    private Set<Long> resolveGatedWarehouseLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private void assertReturnUpstreamLocationGate(Long sourceLocationId, Long purposeLocationId) {
        Set<Long> pharmacies = this.resolveGatedPharmacyLocationIds();
        this.assertPharmacyLocationAllowed(sourceLocationId, pharmacies);
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIds();
        this.assertWarehouseLocationAllowed(purposeLocationId, warehouses);
    }

    private void assertPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.PHA_RU_001, "药房位置不在当前用户可选药房范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "药房位置不在当前用户可选药房范围");
        }
        Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "源位置须为启用中的药房");
        }
    }

    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.PHA_RU_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "目的位置须为启用中的药库");
        }
    }

    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isBlank(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isBlank(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    private void validateOptionalReturnTypeEnum(Integer typeEnum) {
        if (typeEnum == null) {
            return;
        }
        if (typeEnum.intValue() != PHARMACY_RETURN_TYPE_SORT) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "typeEnum 必须为药房退库单类型");
        }
    }

    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = this.supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "供应商不存在或不可用");
            }
            throw ex;
        }
    }

    private void assertPharmacyReturnUpstream(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_RETURN_TO_UPSTREAM.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_103, "单据类型非药房退库单");
            }
        }
    }

    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "busNo 不能为空");
        }
    }

    private void assertReturnUpstreamLinesOccurrencePresent(List<ReturnUpstreamLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (ReturnUpstreamLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "退货明细制单时间不能为空");
            }
        }
    }

    private void assertReturnUpstreamTraceNosWithinWideLimit(List<ReturnUpstreamLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (ReturnUpstreamLineDto line : lines) {
            if (line == null || StringUtils.isBlank(line.getTraceNo())) {
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
                    throw new BusinessException(errorCode, "追溯码片段长度超过允许上限（" + maxLen + "）");
                }
            }
        }
    }

    private void assertProductTableNameWhitelistForSelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            return;
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.PHA_RU_001, "productTableName 非法");
        }
    }

    private String resolveLotNumberForInventory(String lotNumber) {
        if (StringUtils.isBlank(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    private String resolveSourceLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotBlank(line.getSourceTypeEnum())) {
            return line.getSourceTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotBlank(head.getSourceTypeEnum())) {
            return head.getSourceTypeEnum().trim();
        }
        return com.openhis.health.domain.share.enums.SupplyRequestType.CABINET.getCode();
    }

    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
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
                return new BusinessException(ErrorCodeConstants.PHA_RU_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.PHA_RU_003, msg);
        }
        return ex;
    }

    private static final class ReturnUpstreamDestIncr {
        private SupplyRequestModel line;
        private Long purposeLocationId;
        private BigDecimal splitQty;
        private String resolvedLot;
        private String productName;
        private String splitUnitCode;
        private ProductDefinitionDisplayDto productDefinition;
        private Long supplierId;
        private java.time.LocalDateTime sourceInventoryProductionDate;
        private java.time.LocalDateTime sourceInventoryExpirationDate;
    }
}
