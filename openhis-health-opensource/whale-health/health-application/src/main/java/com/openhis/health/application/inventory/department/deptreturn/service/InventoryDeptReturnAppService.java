/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.service;

import com.openhis.health.application.inventory.department.deptreturn.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.deptreturn.dto.DeptReturnLineDto;
import com.openhis.health.application.inventory.department.deptreturn.mapper.InventoryDeptReturnMapper;
import com.openhis.health.application.inventory.department.deptreturn.request.*;
import com.openhis.health.application.inventory.department.deptreturn.response.*;
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
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.module.querysupport.product.ProductDefinitionPurchasePageLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
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
 * 科室退库单（{@link SupplyType#DEPT_RETURN}）应用编排。
 *
 * <p>同意后：领域落已同意态 → 退库发放 → 源科室扣减、目的药库增库存 → 追溯码出库；不上报医保进销存。</p>
 */
@Slf4j
@Service("inventoryDeptReturn")
@RequiredArgsConstructor
public class InventoryDeptReturnAppService {

    private static final long DEPT_RETURN_TYPE_SORT = 22L;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
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
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final InventoryDeptReturnMapper deptReturnMapper;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    private final com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 加载科室退库页主数据（源科室、目的药库下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 经管辖范围收窄后的库位选项
     */
    public LoadDeptReturnFormInitResponse loadDeptReturnFormInit(LoadDeptReturnFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_RT_101, "机构上下文缺失");
        try {
            // Pre：拉取全部启用科室/药库并经管辖 Gate 收窄
            List<Location> wards = this.locationQueryService.listAllActiveWards();
            List<Location> gatedWards = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(wards);
            List<Location> warehouses = this.locationQueryService.listAllActiveWarehouses();
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            // Asm：装配表单初始化 Response
            return this.deptReturnMapper.toLoadDeptReturnFormInitResponse(
                this.deptReturnMapper.toWarehouseOptionDtoList(gatedWards),
                this.deptReturnMapper.toWarehouseOptionDtoList(gatedWarehouses));
        } catch (RuntimeException ex) {
            log.warn("loadDeptReturnFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_RT_102, "科室退库页主数据加载失败");
        }
    }

    /**
     * 分页查询科室退库单明细行列表。
     *
     * @param request 含源科室、状态筛选与分页窗口
     * @return 按供应请求明细展开的分页结果
     */
    public PageDeptReturnsResponse pageDeptReturns(PageDeptReturnsRequest request) {
        // Gate：请求体、源科室与分页窗口不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "请求不能为空");
        }
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_RT_001, "分页参数不能为空");
        this.validateOptionalReturnTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_RT_101, "机构上下文缺失");
        Set<Long> allowedDepts = this.resolveGatedDeptLocationIds();
        this.assertDeptLocationAllowed(request.getSourceLocationId(), allowedDepts);

        // Pre：组装仓储单据分页查询（固定科室退库类型）
        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.DEPT_RETURN.getCode());
        query.setSourceLocationId(request.getSourceLocationId());
        if (request.getStatusEnum() != null) {
            // 按展示 sort 反查 SupplyStatus 持久化码
            SupplyStatus st = EnumUtil.requireFromSortOrder(SupplyStatus.class, request.getStatusEnum().longValue());
            query.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, query);
        query.setApplyTimeStart(request.getApplyTimeFrom());
        query.setApplyTimeEnd(request.getApplyTimeTo());
        try {
            Page<com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            // Asm：领域分页模型转 API Response
            return this.deptReturnMapper.toPageDeptReturnsResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageDeptReturns failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_RT_102, "退货单列表查询失败");
        }
    }

    /**
     * 按业务单号查询科室退库单详情（含产品展示信息）。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     * @throws BusinessException 单号不存在或单据类型非科室退库
     */
    public GetDeptReturnDetailResponse getDeptReturnDetail(GetDeptReturnDetailRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "busNo 不能为空");
        }
        // Pre：按单号回读供应请求行并校验单据类型
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_103, "退货单不存在");
        }
        this.assertDeptReturn(lines);

        // Pre：批量预取产品定义展示字段
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
            log.warn("batchGetProductByIds degraded for dept return detail", ex);
            products = new HashMap<>();
        }
        // Asm：供应行 + 产品读模型装配详情 Response
        return this.deptReturnMapper.toGetDeptReturnDetailResponse(lines, products);
    }

    /**
     * 新建科室退库单（草稿/待提交）。
     *
     * @param request 含来源入库单、源科室、目的药库与明细行
     * @return 新建业务单号
     * @throws BusinessException 库存不足、来源单不匹配或参数非法
     */
    public CreateDeptReturnResponse createDeptReturn(CreateDeptReturnRequest request) {
        // Gate：请求体、来源单、明细与库位不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "请求不能为空");
        }
        if (StringUtils.isBlank(request.getOriginalBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "originalBusNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "退货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_RT_101, "机构上下文缺失");
        Set<Long> allowedDepts = this.resolveGatedDeptLocationIds();
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIds();
        this.assertDeptLocationAllowed(request.getSourceLocationId(), allowedDepts);
        this.assertWarehouseLocationAllowed(request.getPurposeLocationId(), allowedWarehouses);
        this.validateSupplierOptional(request.getSupplierId());
        this.assertDeptReturnTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.DEP_RT_001);
        this.validateOriginalDeliveryForDeptReturn(request.getOriginalBusNo().trim(), request.getSourceLocationId());
        this.assertDeptReturnDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertDeptReturnLinesOccurrencePresent(request.getLines());

        // Pre：解析操作人与制单人（医务人员档案）
        Long operatorId = this.currentOperatorResolver.requireOperatorId(ErrorCodeConstants.DEP_RT_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.DEP_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：组装 CreateReturnCommand 并落供应请求
            CreateReturnCommand cmd = this.deptReturnMapper.toCreateDeptReturnCommand(
                request, operatorId, applicantId, null);
            String busNo = this.supplyRequestDomainService.createReturn(cmd);
            // Asm：返回新建单号
            return this.deptReturnMapper.toCreateDeptReturnResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "退货参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 更新科室退库单（全量替换明细，仅可编辑态允许）。
     *
     * @param request 含 busNo、库位与明细；不支持 rowVersion
     * @return 业务单号（可能与原单号相同）
     * @throws BusinessException 状态不可编辑、并发冲突或参数非法
     */
    public UpdateDeptReturnResponse updateDeptReturn(UpdateDeptReturnRequest request) {
        // Gate：busNo 与明细、库位不可为空；本单不支持乐观锁字段
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "busNo 不能为空");
        }
        if (request.getRowVersion() != null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_105, "退货单不支持 rowVersion 乐观锁");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "退货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_RT_101, "机构上下文缺失");
        Set<Long> allowedDepts = this.resolveGatedDeptLocationIds();
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIds();
        this.assertDeptLocationAllowed(request.getSourceLocationId(), allowedDepts);
        this.assertWarehouseLocationAllowed(request.getPurposeLocationId(), allowedWarehouses);

        // Pre：回读现有单并校验可编辑态
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_103, "退货单不存在");
        }
        this.assertDeptReturn(existing);
        SupplyRequestModel head = existing.get(0);
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = this.supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_004, "当前状态不允许编辑退货单");
        }

        this.validateSupplierOptional(request.getSupplierId());
        this.assertDeptReturnTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.DEP_RT_004);
        this.assertDeptReturnDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertDeptReturnLinesOccurrencePresent(request.getLines());

        Long operatorId = this.currentOperatorResolver.requireOperatorId(ErrorCodeConstants.DEP_RT_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.DEP_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        String originalSnapshot = head.getOriginalBusNo();
        try {
            // Cmd：全量替换明细写回供应请求
            CreateReturnCommand cmd = this.deptReturnMapper.toCreateDeptReturnCommandForUpdate(
                request, operatorId, applicantId, originalSnapshot);
            String busNo = this.supplyRequestDomainService.createReturn(cmd);
            return this.deptReturnMapper.toUpdateDeptReturnResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "退货参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_105, "退货单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交科室退库单审批。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public SubmitDeptReturnApprovalResponse submitDeptReturnApproval(
        SubmitDeptReturnApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.DEP_RT_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域层推进至待审批态
            this.supplyRequestDomainService.submitApproval(
                this.deptReturnMapper.toSubmitApprovalCommand(
                    request,
                    this.currentOperatorResolver.requireOperatorId(ErrorCodeConstants.DEP_RT_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_RT_003);
        }
        return this.deptReturnMapper.toSubmitDeptReturnApprovalResponse();
    }

    /**
     * 撤回科室退库单审批（回到可编辑态）。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public WithdrawDeptReturnApprovalResponse withdrawDeptReturnApproval(
        WithdrawDeptReturnApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域层撤回审批
            this.supplyRequestDomainService.withdrawApproval(
                this.deptReturnMapper.toWithdrawApprovalCommand(
                    request,
                    this.currentOperatorResolver.requireOperatorId(ErrorCodeConstants.DEP_RT_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_RT_003);
        }
        return this.deptReturnMapper.toWithdrawDeptReturnApprovalResponse();
    }

    /**
     * 科室退库单同意编排：领域落已同意态 → 退库发放 → 源科室扣减、目的药库增库存 → 追溯码出库。
     *
     * @param request 须含 busNo
     * @return 同意成功标志
     * @throws BusinessException 重复同意、库存不足或发放失败
     */
    public AgreeDeptReturnResponse agreeDeptReturn(AgreeDeptReturnRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "busNo 不能为空");
        }
        Long operatorId = this.currentOperatorResolver.requireOperatorId(ErrorCodeConstants.DEP_RT_101, "用户未登录");

        // Pre：回读退库单并校验未重复同意
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_103, "退货单不存在");
        }
        this.assertDeptReturn(beforeLines);
        SupplyRequestValidationModel validation = this.supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "退货单已同意，禁止重复同意");
        }

        try {
            this.supplyRequestDomainService.agreeRequest(
                this.deptReturnMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_105, "退库单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertDeptReturn(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "同意后回读状态异常，中止退货编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "退货明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "退货明细数量无效，无法完成编排");
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
            throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "供应请求行已存在完成态发放，禁止重复同意退货");
        }

        try {
            this.supplyDeliveryDomainService.createSupplyDelivery(
                this.deptReturnMapper.toCreateSupplyDeliveryCommandForDeptReturn(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "退库发放落库失败：" + ex.getMessage());
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
                log.warn("batchGetProductByIds failed during dept return inventory assemble", ex);
            }
        }

        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        String traceOperationType = TraceNumberRecordOperationType.supplyTypeCode(SupplyType.DEPT_RETURN);
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems = new ArrayList<>();
        Map<Long, BigDecimal> decreaseMerged = new HashMap<>();

        // Cmd：组装源科室扣减与追溯出库项
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long sourceLocationId = line.getSourceLocationId();
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.DEP_RT_104, "供应请求行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "产品定义缺失或缺少拆零单位编码");
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
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "源库未找到匹配库存行，无法扣减");
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

        // Cmd：源科室按库存行 id 合并扣减（先扣后加，避免先加后扣窗口）
        try {
            this.inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseMerged);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "源科室库存扣减失败：" + ex.getMessage());
        }

        // Cmd：目的药库逐行入账；命中既有行则加量，否则新建库存项
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.DEP_RT_104, "供应请求行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());
            String productName = product.getProductName();
            if (StringUtils.isBlank(productName)) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "产品定义缺失，无法解析展示名称");
            }

            InventoryMatchKey dstKey = new InventoryMatchKey();
            dstKey.setSupplierId(line.getSupplierId());
            dstKey.setLotNumber(resolvedLot);
            dstKey.setLocationId(purposeLocationId);
            dstKey.setItemTable(line.getItemTable());
            dstKey.setItemId(line.getItemId());
            Long destInventoryId = this.inventoryItemQueryService.findFirstIdByInventoryMatchKey(dstKey).orElse(null);
            try {
                if (destInventoryId != null) {
                    IncreaseQuantityCommand incCmd =
                        this.inboundReceiptMapper.toIncreaseQuantityCommand(destInventoryId, splitQty, operatorId);
                    this.inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    CreateInventoryItemCommand createCmd = this.inboundReceiptMapper.toCreateInventoryItemCommand(
                        line, purposeLocationId, productName.trim(), resolvedLot, splitQty, splitUnitCode, operatorId, product);
                    this.inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "目的药库库存增加失败：" + ex.getMessage());
            }
        }

        if (!traceItems.isEmpty()) {
            try {
                AppendTraceRecordsCommand traceCmd = new AppendTraceRecordsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                this.traceNumberRecordDomainService.appendTraceRecords(traceCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_104, "追溯码出库记录失败：" + ex.getMessage());
            }
        }

        return this.deptReturnMapper.toAgreeDeptReturnResponse(true);
    }

    /**
     * 驳回科室退库单。
     *
     * @param request 须含 busNo 与驳回原因
     * @return 驳回成功标志
     */
    public RejectDeptReturnResponse rejectDeptReturn(RejectDeptReturnRequest request) {
        // Gate：busNo 与驳回原因不可为空
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "驳回原因不能为空");
        }
        Long operatorId = this.currentOperatorResolver.requireOperatorId(ErrorCodeConstants.DEP_RT_101, "用户未登录");
        try {
            // Cmd：领域层落驳回态
            this.supplyRequestDomainService.rejectRequest(
                this.deptReturnMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_RT_003);
        }
        return this.deptReturnMapper.toRejectDeptReturnResponse(true);
    }

    /**
     * 删除科室退库单（仅草稿/可删态）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     */
    public RemoveDeptReturnResponse removeDeptReturn(RemoveDeptReturnRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = this.currentOperatorResolver.requireOperatorId(ErrorCodeConstants.DEP_RT_101, "用户未登录");
        try {
            // Cmd：按单号软删供应请求
            this.supplyRequestDomainService.removeByBusNo(
                this.deptReturnMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_RT_005);
        }
        return this.deptReturnMapper.toRemoveDeptReturnResponse(true);
    }

    /**
     * 分页查询退库选品（采购目录 + 可选库存）。
     *
     * @param request 分页窗口与选品筛选条件
     * @return 产品定义展示行分页
     */
    public PageProductsForDeptReturnResponse pageProductsForDeptReturn(
        PageProductsForDeptReturnRequest request) {
        // Gate：分页窗口与机构上下文
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_RT_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_RT_101, "机构上下文缺失");
        this.assertProductTableNameWhitelistForSelection(request.getProductTableName());
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
            Page<ProductDefinitionDisplayDto> page = this.productDefinitionPurchasePageLoader.pageForPurchase(query);
            PageProductsForDeptReturnResponse response =
                this.deptReturnMapper.toPageProductsForDeptReturnResponse(page);
            // Asm：补充销售价等展示字段
            this.enrichProductSelectionRows(response.getContent());
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageProductsForDeptReturn failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_RT_102, "退货选品查询失败");
        }
    }

    /**
     * 分页查询可关联的科室入库来源单（已同意态）。
     *
     * @param request 须含源科室与分页窗口
     * @return 可引用的入库单明细行分页
     */
    public PageLinkableReturnSourceDocumentsResponse pageLinkableReturnSourceDocuments(PageLinkableReturnSourceDocumentsRequest request) {
        // Gate：源科室与分页窗口不可为空
        if (request == null || request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "sourceLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_RT_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_RT_101, "机构上下文缺失");
        Set<Long> allowedDepts = this.resolveGatedDeptLocationIds();
        this.assertDeptLocationAllowed(request.getSourceLocationId(), allowedDepts);

        // Pre：查询已同意的科室入库单作为退库来源
        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.DEPT_STOCK_IN.getCode());
        query.setStatusEnum(SupplyStatus.AGREE.getCode());
        query.setPurposeLocationId(request.getSourceLocationId());
        PageQueryBinder.bind(request, query);
        try {
            Page<com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel> page =
                this.supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            return this.deptReturnMapper.toPageLinkableReturnSourceDocumentsResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageLinkableReturnSourceDocuments failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_RT_102, "可关联来源单查询失败");
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

    private void validateOriginalDeliveryForDeptReturn(String originalBusNo, Long sourceDeptLocationId) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> inboundLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (inboundLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_002, "来源单据不存在");
        }
        boolean matchedDept = false;
        for (SupplyRequestModel line : inboundLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_STOCK_IN.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_002, "来源单据类型不是科室入库单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_002, "来源单据未同意，不可引用");
            }
            if (Objects.equals(sourceDeptLocationId, line.getPurposeLocationId())) {
                matchedDept = true;
            }
        }
        if (!matchedDept) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_002, "来源单据与当前源科室不匹配");
        }
    }

    private void assertDeptReturnDraftLinesWithinSourceAvailableInventory(
        Long sourceLocationId,
        List<DeptReturnLineDto> lines) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (DeptReturnLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "退货明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "退货明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.DEP_RT_001, "退货明细数量须为正数");
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
                            ErrorCodeConstants.DEP_RT_001,
                            "源库该批号可用量不足，请减少退货数量或刷新库存后再试");
                    }
                }
            }
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_RT_001,
                        "源库可用量不足，请减少退货数量或刷新库存后再试");
                }
            }
        }
    }

    private Set<Long> resolveGatedDeptLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWards());
    }

    private Set<Long> resolveGatedWarehouseLocationIds() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private void assertDeptLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEP_RT_001, "科室位置不在当前用户可选科室范围");
        if (locationId == null || !allowed.contains(locationId)) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "科室位置不在当前用户可选科室范围");
        }
        Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WARD.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "来源位置须为启用中的科室/病区");
        }
    }

    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEP_RT_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null || !allowed.contains(locationId)) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "目的位置须为启用中的药库");
        }
    }

    private void validateOptionalReturnTypeEnum(Integer typeEnum) {
        if (typeEnum == null) {
            return;
        }
        if (typeEnum.intValue() != DEPT_RETURN_TYPE_SORT) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "typeEnum 必须为科室退库单类型");
        }
    }

    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = this.supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "供应商不存在或不可用");
            }
            throw ex;
        }
    }

    private void assertDeptReturn(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_RETURN.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_103, "单据类型非科室退库单");
            }
        }
    }

    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "busNo 不能为空");
        }
    }

    private void assertDeptReturnLinesOccurrencePresent(List<DeptReturnLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (DeptReturnLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "退货明细制单时间不能为空");
            }
        }
    }

    private void assertDeptReturnTraceNosWithinWideLimit(List<DeptReturnLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (DeptReturnLineDto line : lines) {
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
            throw new BusinessException(ErrorCodeConstants.DEP_RT_001, "productTableName 非法");
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
        return com.openhis.health.domain.share.enums.SupplyRequestType.DEPARTMENT.getCode();
    }

    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        Long userId = this.currentOperatorResolver.requireOperatorId(notLoggedInErrorCode, notLoggedInMessage);
        Practitioner practitioner = this.practitionerQueryService.findByUserId(userId);
        if (practitioner == null || practitioner.getId() == null) {
            throw new BusinessException(notLoggedInErrorCode, noPractitionerMessage);
        }
        return practitioner.getId();
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
                return new BusinessException(ErrorCodeConstants.DEP_RT_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_RT_003, msg);
        }
        return ex;
    }
}
