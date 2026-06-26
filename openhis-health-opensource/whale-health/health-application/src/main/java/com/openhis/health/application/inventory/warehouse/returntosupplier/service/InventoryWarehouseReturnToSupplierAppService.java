/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.warehouse.returntosupplier.service;

import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.returntosupplier.dto.ReturnToSupplierLineDto;
import com.openhis.health.integration.support.insurance.inventory.InventoryMedicareUploadSupport;
import com.openhis.health.application.inventory.warehouse.returntosupplier.mapper.InventoryWarehouseReturnToSupplierMapper;
import com.openhis.health.application.inventory.warehouse.returntosupplier.request.*;
import com.openhis.health.application.inventory.warehouse.returntosupplier.response.*;
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
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateReturnToSupplierCommand;
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
import com.openhis.health.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterPurchaseReturnCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Set;

/**
 * 药库退货单（{@link SupplyType#CABINET_RETURN_TO_SUPPLIER}）应用编排。
 *
 * <p>同意后：领域落已同意态 → 退货发放 → 源药库扣减（不增加目的侧库存）→ 追溯码；可选触发医保 3504 上传。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseReturnToSupplierAppService {

    private static final long CABINET_RETURN_TYPE_SORT = 3L;

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
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final InventoryWarehouseReturnToSupplierMapper returnToSupplierMapper;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    private final com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 加载库房单据页主数据（管辖药库、枚举选项、制单人员等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
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
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);

        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode());
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
            return this.returnToSupplierMapper.toPageReturnToSuppliersResponse(page);
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
     */
    public GetReturnToSupplierDetailResponse getReturnToSupplierDetail(GetReturnToSupplierDetailRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            // Gate：busNo 非空
            // Pre：按 busNo 回读供应请求行
            // Pre：批量装载产品读模型
            // Asm：Mapper 映射详情并增强展示字段
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_103, "退货单不存在");
        }
        this.assertCabinetReturnToSupplier(lines);

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
            log.warn("batchGetProductByIds degraded for return to supplier detail", ex);
            products = new HashMap<>();
        }
        return this.returnToSupplierMapper.toGetReturnToSupplierDetailResponse(lines, products);
    }

    /**
     * 新建库房单据草稿并落库供应请求。
     *
     * @param request 单头、明细与库房门禁字段
     * @return 新建后的 busNo
     */
    public CreateReturnToSupplierResponse createReturnToSupplier(CreateReturnToSupplierRequest request) {
        // Gate：请求体、明细与库房/供应商门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "请求不能为空");
        }
        // Cmd：组装 Create 命令并调用领域写服务
        // Asm：返回 busNo
        if (StringUtils.isBlank(request.getOriginalBusNo())) {
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
        this.validateOriginalInboundReceiptForReturn(request.getOriginalBusNo().trim(), request.getSourceLocationId());
        this.assertReturnToSupplierDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnToSupplierLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            CreateReturnToSupplierCommand cmd = this.returnToSupplierMapper.toCreateReturnToSupplierCommand(
                request, operatorId, applicantId, null);
            String busNo = this.supplyRequestDomainService.createReturnToSupplier(cmd);
            return this.returnToSupplierMapper.toCreateReturnToSupplierResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 更新可编辑态库房单据（全量明细覆盖）。
     *
     * @param request 含 busNo、明细与乐观锁相关字段
     * @return 更新后的 busNo
     */
    public UpdateReturnToSupplierResponse updateReturnToSupplier(UpdateReturnToSupplierRequest request) {
        // Gate：busNo、明细与可编辑态校验
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        if (request.getRowVersion() != null) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_105, "退货单不支持 rowVersion 乐观锁");
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
            throw new BusinessException(ErrorCodeConstants.WHS_RT_103, "退货单不存在");
        }
        this.assertCabinetReturnToSupplier(existing);
        SupplyRequestModel head = existing.get(0);
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = this.supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_004, "当前状态不允许编辑退货单");
        }

        this.validateSupplierOptional(request.getSupplierId());
        this.assertReturnToSupplierTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_RT_004);
        this.assertReturnToSupplierDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertReturnToSupplierLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_RT_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        String originalSnapshot = head.getOriginalBusNo();
        try {
            CreateReturnToSupplierCommand cmd = this.returnToSupplierMapper.toCreateReturnToSupplierCommandForUpdate(
                request, operatorId, applicantId, originalSnapshot);
            String busNo = this.supplyRequestDomainService.createReturnToSupplier(cmd);
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
     * 药库退供应商单同意编排：领域落已同意态 → 退货发放 → 源药库扣减库存 → 追溯码出库。
     *
     * @param request 含 busNo；须未重复同意
     * @return 是否同意成功
     */
    public AgreeReturnToSupplierResponse agreeReturnToSupplier(AgreeReturnToSupplierRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
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
                log.warn("batchGetProductByIds failed during return to supplier inventory assemble", ex);
            }
        }

        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        String traceOperationType = TraceNumberRecordOperationType.supplyTypeCode(SupplyType.CABINET_RETURN_TO_SUPPLIER);
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems = new ArrayList<>();
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
            if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());

            InventoryMatchKey srcKey = new InventoryMatchKey();
            srcKey.setLotNumber(resolvedLot);
            srcKey.setLocationId(sourceLocationId);
            srcKey.setItemTable(line.getItemTable());
            srcKey.setItemId(line.getItemId());
            Long sourceInventoryId = this.inventoryItemQueryService.findFirstIdByInventoryMatchKey(srcKey).orElse(null);
            if (sourceInventoryId == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "源库未找到匹配库存行，无法扣减");
            }
            decreaseMerged.merge(sourceInventoryId, splitQty, BigDecimal::add);

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

        try {
            this.inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseMerged);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_104, "源库库存扣减失败：" + ex.getMessage());
        }

        if (!traceItems.isEmpty()) {
            try {
                AppendTraceRecordsCommand traceCmd = new AppendTraceRecordsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                this.traceNumberRecordDomainService.appendTraceRecords(traceCmd);
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
     */
    public RejectReturnToSupplierResponse rejectReturnToSupplier(RejectReturnToSupplierRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getRejectReason())) {
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
     * 分页查询单据选品（库存目录可用量）。
     *
     * @param request 含库房、物品表与分页参数
     * @return 选品行与分页元数据
     */
    public PageProductsForReturnToSupplierResponse pageProductsForReturnToSupplier(
        PageProductsForReturnToSupplierRequest request) {
        // Gate：分页窗口、机构与库房门禁
        // Pre：组装库存目录分页查询
        // Cmd：查询可用库存行
        // Asm：映射选品分页并增强价格
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_RT_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
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
            PageProductsForReturnToSupplierResponse response =
                this.returnToSupplierMapper.toPageProductsForReturnToSupplierResponse(page);
            this.enrichProductSelectionRows(response.getContent());
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageProductsForReturnToSupplier failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_RT_102, "退货选品查询失败");
        }
    }

    /**
     * 分页查询可关联来源单据（已同意态供引用）。
     *
     * @param request 含来源位置与分页参数
     * @return 可关联单据行
     */
    public PageLinkableGoodsReceiptsResponse pageLinkableGoodsReceipts(PageLinkableGoodsReceiptsRequest request) {
        if (request == null || request.getSourceLocationId() == null) {
            // Gate：来源位置与分页窗口
            // Pre：组装可关联来源单查询
            // Cmd：分页查询已同意来源单
            // Asm：映射分页响应
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_RT_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_RT_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIds();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);

        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.CABINET_INBOUND_RECEIPT.getCode());
        query.setStatusEnum(SupplyStatus.AGREE.getCode());
        query.setPurposeLocationId(request.getSourceLocationId());
        PageQueryBinder.bind(request, query);
        try {
            Page<com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            return this.returnToSupplierMapper.toPageLinkableGoodsReceiptsResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageLinkableGoodsReceipts failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_RT_102, "可关联进货单查询失败");
        }
    }

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
            return;
        }
        InventoryMedicareUploadSupport.uploadIfEnabled(this.inventoryUploadExtension, () -> {
            InventoryUploadAfterPurchaseReturnCommand command = new InventoryUploadAfterPurchaseReturnCommand()
                .setReturnBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setContractBusNo(request.getContractBusNo());
            this.inventoryUploadExtension.uploadAfterPurchaseReturn(command);
        });
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

    private void validateOriginalInboundReceiptForReturn(String originalBusNo, Long sourceWarehouseId) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> inboundLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (inboundLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源进货单不存在");
        }
        boolean matchedWarehouse = false;
        for (SupplyRequestModel line : inboundLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源单据类型不是药库进货单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源进货单未同意，不可引用");
            }
            if (Objects.equals(sourceWarehouseId, line.getPurposeLocationId())) {
                matchedWarehouse = true;
            }
        }
        if (!matchedWarehouse) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_002, "来源进货单与当前源药库不匹配");
        }
    }

    private void assertReturnToSupplierDraftLinesWithinSourceAvailableInventory(
        Long sourceLocationId,
        List<ReturnToSupplierLineDto> lines) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (ReturnToSupplierLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "退货明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.WHS_RT_001, "退货明细数量须为正数");
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
                            ErrorCodeConstants.WHS_RT_001,
                            "源库该批号可用量不足，请减少退货数量或刷新库存后再试");
                    }
                }
            }
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_RT_001,
                        "源库可用量不足，请减少退货数量或刷新库存后再试");
                }
            }
        }
    }

    private Set<Long> resolveGatedWarehouseLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private void assertLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_RT_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null || !allowed.contains(locationId)) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "药库位置不在当前用户可选药库范围");
        }
    }

    private void validateOptionalReturnTypeEnum(Integer typeEnum) {
        if (typeEnum == null) {
            return;
        }
        if (typeEnum.intValue() != CABINET_RETURN_TYPE_SORT) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "typeEnum 必须为药库退货单类型");
        }
    }

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
                throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "供应商不存在或不可用");
            }
            throw ex;
        }
    }

    private void assertCabinetReturnToSupplier(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_RT_103, "单据类型非药库退货单");
            }
        }
    }

    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "busNo 不能为空");
        }
    }

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

    private void assertReturnToSupplierTraceNosWithinWideLimit(List<ReturnToSupplierLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (ReturnToSupplierLineDto line : lines) {
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
            throw new BusinessException(ErrorCodeConstants.WHS_RT_001, "productTableName 非法");
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
                return new BusinessException(ErrorCodeConstants.WHS_RT_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_RT_003, msg);
        }
        return ex;
    }
}
