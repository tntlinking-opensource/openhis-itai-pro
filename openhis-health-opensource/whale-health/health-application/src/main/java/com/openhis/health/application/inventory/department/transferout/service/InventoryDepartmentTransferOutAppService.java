/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.transferout.service;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.inventory.department.transferout.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.transferout.dto.TransferOutDetailLineDto;
import com.openhis.health.application.inventory.department.transferout.dto.TransferOutLineDto;
import com.openhis.health.application.inventory.department.transferout.dto.TransferOutListRowDto;
import com.openhis.health.application.inventory.department.transferout.mapper.InventoryDepartmentTransferOutMapper;
import com.openhis.health.application.inventory.department.transferout.request.*;
import com.openhis.health.application.inventory.department.transferout.response.*;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberSupplyAgreeTraceOrchestrator;
import com.openhis.health.application.shared.tracenumber.RecordTraceTransferPairSpec;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateTransferCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.application.shared.inventory.InventoryItemProductSelectionPageLoader;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.application.shared.supplyrequest.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
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
 * 科室调出单（{@link SupplyType#DEPT_TRANSFER_OUT}）应用编排（IF-DEPT-TR · DM §6.1.8）。
 *
 * <p>主动制单侧：维护供应请求草稿与审批链；<strong>同意</strong>后依次：领域落已同意态 → 调拨发放 → 源库扣减 / 目的库增加 → 追溯码，
 * 并生成对侧调入壳单（{@code AGREE} 只读台账）。调拨为<strong>平级调拨</strong>：本包源/目的均须为科室药房
 * （{@link LocationForm#DEPARTMENT_PHARMACY}），禁止跨药库/药房形态互转。</p>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/inventoryDepartmentTransferOut/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentTransferOutAppService {

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
    private final InventoryItemProductSelectionPageLoader inventoryItemProductSelectionPageLoader;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryDepartmentTransferOutMapper transferOutMapper;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 加载科室调出页主数据（源科室药房、目的库位、枚举选项、药师/库管等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
     */
    public LoadTransferOutFormInitResponse loadTransferOutFormInit(LoadTransferOutFormInitRequest request) {
        // Gate：机构会话上下文
        managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEPT_TR_101, "机构上下文缺失");
        try {
            // Pre：装载管辖范围内启用位置；经手人取全体在职医务人员（不限药师/库管）
            // Asm：Mapper 映射页初始化响应
            // —— 平级调拨：源/目的下拉均为管辖范围内启用中科室药房。——
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> gatedDepartmentPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<Practitioner> handlers =
                this.practitionerRoleScopeLoader.listAllActivePractitionersForInventoryDocHandlerSelect();
            return transferOutMapper.toLoadTransferOutFormInitResponse(
                transferOutMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies),
                transferOutMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies),
                transferOutMapper.toOptionDtoListInboundItemTableTypes(),
                transferOutMapper.toOptionDtoListSupplyStatuses(),
                transferOutMapper.toOptionDtoListFromPractitioners(handlers),
                List.of());
        } catch (RuntimeException ex) {
            log.warn("loadTransferOutFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_102, "调出页主数据加载失败");
        }
    }

    /**
     * 分页查询科室调出单列表（按 busNo 聚合；筛选源科室药房）。
     *
     * @param request 含分页窗口、源科室药房与状态筛选
     * @return 聚合后的列表行与分页元数据
     */
    public PageTransferOutsResponse pageTransferOuts(PageTransferOutsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "请求不能为空");
        }
        // Pre：组装列表查询与源科室药房门禁
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "sourceLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEPT_TR_001, "分页参数不能为空");
        this.validateOptionalTransferOutTypeEnum(request.getTypeEnum());
        // —— 机构上下文 + 岗位可见药房集合（sourceLocationId=0 与单库校验共用白名单）。——
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEPT_TR_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_TRANSFER_OUT.getCode());
        // —— sourceLocationId=0：多科室药房 IN 查询；无可见科室药房时用占位 id 收窄为空页。——
        if (Long.valueOf(0L).equals(request.getSourceLocationId())) {
            if (allowedDepartmentPharmacies.isEmpty()) {
                listQuery.setSourceLocationIds(List.of(-1L));
            } else {
                listQuery.setSourceLocationIds(new ArrayList<>(allowedDepartmentPharmacies));
            }
        } else {
            this.assertDepartmentPharmacyLocationAllowed(request.getSourceLocationId(), allowedDepartmentPharmacies);
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (request.getPurposeLocationId() != null) {
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        // —— 申请时刻窗：优先专用字段，否则回落 AbstractPageQuery 的 startDate/endDate（B.10.11）。——
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
            // —— 聚合路径：先按 bus_no 去重计数与分页取号，再批量拉行交给 Mapper 聚合成列表头。——
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<TransferOutListRowDto> records = this.transferOutMapper.toTransferOutListAggregated(busNos, lines);
            // —— 补全目的位置展示名（到何处）。——
            this.enrichTransferOutListPurposeLocationNames(records);
            this.enrichTransferOutListStatusEnumText(records);
            this.enrichTransferOutListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合 busNo 行并映射分页响应
            return this.transferOutMapper.toPageTransferOutsAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageTransferOuts failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_102, "调出单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读单据详情（含明细行与展示增强）。
     *
     * @param request 含业务单号 busNo
     * @return 单头与明细
     */
    public GetTransferOutDetailResponse getTransferOutDetail(GetTransferOutDetailRequest request) {
        // Gate：busNo 非空，否则无法定位供应请求聚合
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "busNo 不能为空");
        }
        // Pre：按 busNo 回读供应请求行
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            // 同号无行：按不存在处理，避免空壳响应误导前端
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_103, "调出单不存在");
        }
        this.assertDepartmentTransferOut(lines);
        // Pre：批量装载产品读模型（失败时降级为空映射，写模型字段仍可返回）
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
            log.warn("batchGetProductByIds degraded for stock out detail", ex);
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
        // Asm：Mapper 映射详情并增强展示字段
        GetTransferOutDetailResponse res = transferOutMapper.toGetTransferOutDetailResponse(lines, hints);
        this.enrichTransferOutDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichTransferOutDetailLines(res.getLines());
        this.enrichTransferOutDetailAvailableQuantity(res.getLines(), res.getSourceLocationId());
        this.enrichTransferOutDetailPractitionerName(res);
        return res;
    }

    /**
     * 新建科室调出草稿并落库供应请求。
     *
     * @param request 单头、明细与源科室药房/目的药库门禁字段
     * @return 新建后的 busNo
     */
    public CreateTransferOutResponse createTransfer(CreateTransferOutRequest request) {
        // Gate：请求体、明细与源科室药房/目的药库门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEPT_TR_101, "机构上下文缺失");
        this.assertTransferOutLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        this.assertTransferOutTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.DEPT_TR_001);
        // —— 源科室药房可用量与草稿行拆零量汇总比较。——
        this.assertTransferOutDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertDepartmentTransferOutDoesNotReferenceDeptRequisition(request.getOriginalBusNo());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "经手人不能为空");
        }
        this.assertTransferOutLinesOccurrencePresent(request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEPT_TR_101, "用户未登录");
        Long applicantId = this.requireDepartmentPreparerPractitionerId(
            ErrorCodeConstants.DEPT_TR_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        // —— 领域 createTransfer 落供应写模型（head + 明细行，类型 DEPT_TRANSFER_OUT）。——
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            String busNo = supplyRequestPricedWriteOrchestrator.createTransfer(
                transferOutMapper.toCreateTransferOutCommand(request, operatorId, applicantId, null));
            // Asm：映射新建响应
            return transferOutMapper.toCreateTransferOutResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 修订科室调出草稿（同号覆盖；须处于可编辑态）。
     *
     * @param request 修订请求；须含 {@code busNo}、非空明细及位置字段
     * @return 修订后单号（与请求同号）
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 参数/可用量非法；{@link ErrorCodeConstants#DEPT_TR_002} 不支持关联请领上游；{@link ErrorCodeConstants#DEPT_TR_004} 不可编辑；
     *                            {@link ErrorCodeConstants#DEPT_TR_101} 未登录
     */
    public UpdateTransferOutResponse updateTransferOut(UpdateTransferOutRequest request) {
        // Gate：busNo、明细与可编辑态校验
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "源位置与目标位置不能为空");
        }
        // —— 机构 + 源/目的库位形态（修订与新建门控同口径）。——
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEPT_TR_101, "机构上下文缺失");
        this.assertTransferOutLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        // —— 同号待审核可编辑态（verifyAbleEdit 与域状态机一致）。——
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_004, "当前状态不允许编辑调出单");
        }
        this.assertTransferOutTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.DEPT_TR_001);
        // —— 修订草稿同样须满足源库可用量校验（与新建同构）。——
        this.assertTransferOutDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        this.assertDepartmentTransferOutDoesNotReferenceDeptRequisition(request.getOriginalBusNo());
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "经手人不能为空");
        }
        this.assertTransferOutLinesOccurrencePresent(request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEPT_TR_101, "用户未登录");
        Long applicantId = this.requireDepartmentPreparerPractitionerId(
            ErrorCodeConstants.DEPT_TR_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        // —— 领域 createTransfer 同号修订（子行全量对齐与软删语义由域层约束）。——
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            String busNo = supplyRequestPricedWriteOrchestrator.createTransfer(
                transferOutMapper.toCreateTransferOutCommand(request, operatorId, applicantId));
            // Asm：映射修订响应
            return transferOutMapper.toUpdateTransferOutResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 提交单据进入待审批态。
     *
     * @param request 含业务单号 busNo
     * @return 提交后的展示状态
     */
    public SubmitTransferOutApprovalResponse submitTransferOutApproval(SubmitTransferOutApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireDepartmentPreparerPractitionerId(
            ErrorCodeConstants.DEPT_TR_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        // —— 工作流提交审批（非法参数类错误经 mapSupplyWorkflowException 回落接口码）。——
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(
                transferOutMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEPT_TR_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEPT_TR_003);
        }
        // Asm：映射提交响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return transferOutMapper.toSubmitTransferOutApprovalResponse(st);
    }

    /**
     * 撤回审批（回到可编辑草稿态）。
     *
     * @param request 含业务单号 busNo
     * @return 撤回后的展示状态
     */
    public WithdrawTransferOutApprovalResponse withdrawTransferOutApproval(WithdrawTransferOutApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(
                transferOutMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEPT_TR_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEPT_TR_003);
        }
        // Asm：映射撤回响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return transferOutMapper.toWithdrawTransferOutApprovalResponse(st);
    }

    /**
     * 同意科室调出单（IF-DEPT-TR-08）：领域同意 → 发放 → 源科室药房扣减 / 目的药库入账 → 对侧调入壳单 → 追溯 → 可选医保上报。
     *
     * @param request 含 busNo；审批时刻与审批人由服务端写入
     * @return 调出单号、终态与生成的对侧调入壳单 busNo
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 参数非法；{@link ErrorCodeConstants#DEPT_TR_103} 不存在；
     *                            {@link ErrorCodeConstants#DEPT_TR_104} 重复同意或库存/壳单编排失败；{@link ErrorCodeConstants#DEPT_TR_105} 乐观锁冲突
     */
    public AgreeTransferOutResponse agreeTransferOut(AgreeTransferOutRequest request) {
        // Gate：busNo 与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEPT_TR_101, "用户未登录");

        // Pre：加载同意前快照，校验类型并防重复同意
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_103, "调出单不存在");
        }
        this.assertDepartmentTransferOut(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "调出单已同意，禁止重复同意");
        }

        // Cmd：领域状态机同意调出
        try {
            supplyRequestDomainService.agreeRequest(transferOutMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_105, "调出单已被并发修改，请刷新后重试", ex);
        }

        // —— 强制读回：与进货同意链一致，保证后续发放/库存读到已落库的 AGREE 行。——
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertDepartmentTransferOut(agreedLines);
        // —— 回读 AGREE 行后校验状态、持久化主键与数量，保证后续发放/库存编排输入合法。——
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "同意后回读状态异常，中止调出编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "调出明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "调出明细数量无效，无法完成编排");
            }
        }

        // —— 批量防重「已完成发放」行，语义等价 supplyDeliveryValidation。——
        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        Set<Long> duplicateCompletedRequestIds =
            supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "供应请求行已存在完成态发放，禁止重复同意调出");
        }

        // —— 发放主数据落库（类型固定科室调出）。——
        try {
            supplyDeliveryDomainService.createTransferDelivery(
                transferOutMapper.toCreateTransferDeliveryCommand(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "调拨发放落库失败：" + ex.getMessage());
        }

        // —— 批量产品读模型（拆零换算、展示名；单点异常记日志，缺键在后续换算处显式失败）。——
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
                log.warn("batchGetProductByIds failed during stock out inventory assemble", ex);
            }
        }

        // —— 内存编排：解析拆零量、源库存匹配键、合并扣减表、目的侧入账计划与追溯项（空白 trace 不入列）。——
        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        RecordTraceTransferPairSpec transferPairCmd = new RecordTraceTransferPairSpec();
        transferPairCmd.setOperatorId(operatorId);
        transferPairCmd.setSupplyTypeCode(SupplyType.DEPT_TRANSFER_OUT.getCode());
        transferPairCmd.setBusNo(busNoTrim);
        transferPairCmd.setOutLocationId(head.getSourceLocationId());
        transferPairCmd.setOutLocationTypeEnum(this.resolveSourceLocationTypeCode(head, head));
        transferPairCmd.setInLocationId(head.getPurposeLocationId());
        transferPairCmd.setInLocationTypeEnum(this.resolvePurposeLocationTypeCode(head, head));
        List<RecordTraceTransferPairSpec.TransferLine> transferLines = new ArrayList<>();
        Map<Long, BigDecimal> decreaseMerged = new HashMap<>();
        List<TransferOutDestIncr> destIncrs = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long sourceLocationId = line.getSourceLocationId();
            Long purposeLocationId = line.getPurposeLocationId();
            String productName = this.resolveProductName(line, productByKey);
            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.DEPT_TR_104, "供应请求行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();

            String resolvedLot = this.resolveLotNumberForTransferOutInventory(line.getLotNumber());
            InventoryMatchKey srcKey = new InventoryMatchKey();
            srcKey.setSupplierId(line.getSupplierId());
            srcKey.setLotNumber(resolvedLot);
            srcKey.setLocationId(sourceLocationId);
            srcKey.setItemTable(line.getItemTable());
            srcKey.setItemId(line.getItemId());
            Long sourceInventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(srcKey).orElse(null);
            if (sourceInventoryId == null) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "源库未找到匹配库存行，无法扣减");
            }
            decreaseMerged.merge(sourceInventoryId, splitQty, BigDecimal::add);

            TransferOutDestIncr op = new TransferOutDestIncr();
            op.line = line;
            op.purposeLocationId = purposeLocationId;
            op.splitQty = splitQty;
            op.resolvedLot = resolvedLot;
            op.productName = productName;
            op.splitUnitCode = splitUnitCode;
            op.productDefinition = product;
            InventoryItemModel srcInv = inventoryItemQueryService.getInventoryItemById(sourceInventoryId);
            op.sourceInventorySupplierId = null;
            op.sourceInventoryProductionDate = null;
            op.sourceInventoryExpirationDate = null;
            if (srcInv != null) {
                op.sourceInventorySupplierId = srcInv.getSupplierId();
                op.sourceInventoryProductionDate = srcInv.getProductionDate();
                op.sourceInventoryExpirationDate = srcInv.getExpirationDate();
            }
            destIncrs.add(op);

            if (StringUtils.isNotEmpty(line.getTraceNo())) {
                transferLines.addAll(traceNumberSupplyAgreeTraceOrchestrator.buildTransferLinesFromTraceNoString(
                    line.getTraceNo(),
                    line.getItemTable().trim(),
                    line.getItemId(),
                    resolvedLot,
                    splitQty,
                    splitUnitCode,
                    ProductDefinitionDisplaySupport.toUnitConversionSource(product)));
            }
        }

        // —— 源库按库存行 id 合并扣减（先扣后加，避免先加后扣窗口）。——
        try {
            inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseMerged);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "源库库存扣减失败：" + ex.getMessage());
        }

        // —— 目的库逐行入账；命中既有行则加量，否则按四元组新建新库存项（与进货 Mapper 命令同构）。——
        for (TransferOutDestIncr op : destIncrs) {
            SupplyRequestModel line = op.line;
            InventoryMatchKey dstKey = new InventoryMatchKey();
            dstKey.setSupplierId(op.sourceInventorySupplierId);
            dstKey.setLotNumber(op.resolvedLot);
            dstKey.setLocationId(op.purposeLocationId);
            dstKey.setItemTable(line.getItemTable());
            dstKey.setItemId(line.getItemId());
            Long destInventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(dstKey).orElse(null);
            try {
                if (destInventoryId != null) {
                    IncreaseQuantityCommand incCmd =
                        inboundReceiptMapper.toIncreaseQuantityCommand(destInventoryId, op.splitQty, operatorId);
                    inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    if (op.sourceInventorySupplierId == null) {
                        throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "源库库存行缺少供应商标识，无法在目的库新建账面");
                    }
                    CreateInventoryItemCommand createCmd = inboundReceiptMapper.toCreateInventoryItemCommand(
                        line,
                        op.purposeLocationId,
                        op.productName,
                        op.resolvedLot,
                        op.splitQty,
                        op.splitUnitCode,
                        operatorId,
                        op.productDefinition,
                        op.sourceInventorySupplierId);
                    // —— 供应行未带效期时承袭源库库存生产日期/效期（调出行常不落 start_time/end_time）。——
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
                    inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "目的库库存增加失败：" + ex.getMessage());
            }
        }

        // —— 对侧调入壳单须先于追溯落库，失败则整单回滚（库存已在上方源减/目增完成）。——
        Long shellApplicantId = head.getApplicantId();
        if (shellApplicantId == null) {
            shellApplicantId = this.requireDepartmentPreparerPractitionerId(
                ErrorCodeConstants.DEPT_TR_101,
                "用户未登录",
                "当前用户未绑定医务人员档案，无法生成调入壳单制单人");
        }
        String counterpartyTransferInType = this.resolveCounterpartyTransferInType(head);
        String generatedInBusNo;
        try {
            CreateTransferCommand shellCmd = transferOutMapper.toCreateTransferCommandForShellCounterpartyTransferIn(
                agreedLines, busNoTrim, counterpartyTransferInType, operatorId, shellApplicantId);
            generatedInBusNo = supplyRequestPricedWriteOrchestrator.createTransfer(shellCmd);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "同步生成对侧调入壳单失败：" + ex.getMessage());
        }

        transferPairCmd.setLines(transferLines);
        if (transferLines != null && !transferLines.isEmpty()) {
            try {
                traceNumberSupplyAgreeTraceOrchestrator.recordTransferPair(transferPairCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "追溯码调出记录失败：" + ex.getMessage());
            }
        }
        // Asm：映射同意响应（含对侧调入壳单 busNo）
        return transferOutMapper.toAgreeTransferOutResponse(
            busNoTrim, SupplyStatus.AGREE.getCode(), generatedInBusNo);
    }

    /**
     * 驳回单据并记录原因。
     *
     * @param request 含 busNo 与驳回原因
     * @return 是否驳回成功
     */
    public RejectTransferOutResponse rejectTransferOut(RejectTransferOutRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getChangeReason()) && StringUtils.isEmpty(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "驳回原因不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEPT_TR_101, "用户未登录");
        // —— 领域驳回请求。——
        try {
            supplyRequestDomainService.rejectRequest(transferOutMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEPT_TR_003);
        }
        // Asm：映射驳回响应
        return transferOutMapper.toRejectTransferOutResponse(true);
    }

    /**
     * 删除单据（仅允许草稿/可删态）。
     *
     * @param request 含业务单号 busNo
     * @return 是否删除成功
     */
    public RemoveTransferOutResponse removeTransferOut(RemoveTransferOutRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEPT_TR_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删
            supplyRequestDomainService.removeByBusNo(transferOutMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEPT_TR_005);
        }
        // Asm：映射删除响应
        return transferOutMapper.toRemoveTransferOutResponse(true);
    }

    /**
     * 分页查询科室调出选品（源科室药房库存目录可用量）。
     *
     * @param request 含源科室药房、物品表与分页参数
     * @return 选品行与分页元数据
     */
    public PageProductsForTransferOutResponse pageProductsForTransferOut(PageProductsForTransferOutRequest request) {
        // Gate：分页窗口、机构与源科室药房门禁
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEPT_TR_001, "分页参数不能为空");
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "sourceLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEPT_TR_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertProductTableNameWhitelistForTransferOutSelection(request.getProductTableName());
        // —— 查询：源科室药房库存分页（物品表白名单），再批量拼产品定义、批号维进价 LOT_NUMBER_COST 与目录槽。——
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getSourceLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        // —— 前台可选：true 仅返回选品库位账面量>0 的批号行；false/未传不过滤数量。——
        invQuery.setPositiveQuantityOnly(request.getPositiveQuantityOnly());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            Page<InventoryItemModel> page = inventoryItemProductSelectionPageLoader.page(invQuery, pageable);
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
                    log.warn("batchGetProductByIds failed for stock out selection", ex);
                }
            }
            String itemTable = request.getProductTableName().trim();
            List<Long> pageItemIds = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv != null && inv.getItemId() != null) {
                    pageItemIds.add(inv.getItemId());
                }
            }
            // —— 选品展示可用量须与保存前可用量校验同口径（可用状态、未过期、未锁定），避免账面 quantity 误导。——
            Map<Long, Map<String, BigDecimal>> availableQtyByItemIdAndNormLot = new HashMap<>();
            if (!pageItemIds.isEmpty()) {
                List<InventoryCatalogAvailabilityRowModel> availRows =
                    this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                        itemTable, pageItemIds, List.of(request.getSourceLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getSourceLocationId())) {
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
                ProductDefinitionItemDto dto = transferOutMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
                if (inv.getItemId() != null) {
                    // —— 可用量：与 assert 阶段同一归一化批号键聚合，避免分页 quantity 与可用口径不一致。——
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
                    // 选品预览：批号成本价走当前 ACTIVE 目录（新开/选品场景，非历史单据槽价）
                    lotCostUnit = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                        inv.getItemTable(), inv.getItemId(), inv.getLotNumber().trim());
                }
                dto.setPurchaseUnitPrice(lotCostUnit);
                ProductDefinitionCatalogEditorHintsDto hints =
                    ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                if (hints != null) {
                    List<ProductDefinitionCatalogUnitBandDto> bands = hints.getUnitBands();
                    dto.setUnitBands(bands);
                    // —— 库存流转选品槽锚仅 LOT_NUMBER_COST；无批号价不灌目录 PURCHASE。——
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, lotCostUnit);
                }
                records.add(dto);
            }
            // —— B.10.11：行已映射为 DTO，分页元数据仍取自同一 Spring Page（禁止手写 getNumber()+1）。——
            return PagedResultBuilder.build(new PageProductsForTransferOutResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForTransferOut failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_102, "调出选品查询失败");
        }
    }

    /**
     * 列表行补全制单人/审批人展示名（批量查 practitioner，避免 N+1）。
     *
     * @param rows        列表聚合行
     * @param detailLines 本页全部供应请求明细（用于收集人员 id）
     */
    private void enrichTransferOutListDisplayNames(List<TransferOutListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (TransferOutListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getApplicantId() != null) {
                practitionerIds.add(row.getApplicantId());
            }
            if (row.getApproverId() != null) {
                practitionerIds.add(row.getApproverId());
            }
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long pid : practitionerIds) {
            if (pid == null) {
                continue;
            }
            Practitioner pr = this.practitionerQueryService.findById(pid);
            if (pr != null && StringUtils.isNotEmpty(pr.getName())) {
                practitionerNameById.put(pid, pr.getName().trim());
            }
        }
        for (TransferOutListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getApplicantId() != null) {
                String name = practitionerNameById.get(row.getApplicantId());
                if (name != null) {
                    row.setCreatorName(name);
                }
            }
            if (row.getApproverId() != null) {
                String name = practitionerNameById.get(row.getApproverId());
                if (name != null) {
                    row.setApproverName(name);
                }
            }
        }
    }

    /**
     * 科室调出详情：补齐项目名称、规格、厂家与行单位展示名（与调入 IF-DEPT-TI-03 同构）。
     *
     * @param lines           详情明细行
     * @param products        产品读模型映射（表名+主键复合键）
     * @param terminologyMaps 选品术语（单位展示名等）
     */
    private void enrichTransferOutDetailProductDisplay(
        List<TransferOutDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        if (products == null || products.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        for (TransferOutDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            dto.setProductName(pr.getProductName());
            dto.setSpecification(pr.getTotalVolume());
            dto.setManufacturer(pr.getManufacturer());
            dto.setUnitCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, dto.getUnitCode(), pr));
            ProductDefinitionCatalogEditorHintsDto catalog = dto.getProductCatalog();
            if (catalog != null && catalog.getUnitBands() != null && !catalog.getUnitBands().isEmpty()) {
                this.productDefinitionDisplayTerminologyAssembler.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 列表行补全目的药库展示名（批量查 location，避免 N+1）。
     *
     * @param rows 列表聚合行
     */
    private void enrichTransferOutListPurposeLocationNames(List<TransferOutListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 目的药库 id 去重后批量查名，避免列表对 location 逐行点查。——
        Set<Long> purposeIds = new HashSet<>();
        for (TransferOutListRowDto row : rows) {
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
        // —— 回填列表展示列：未在 nameById 命中时保持 null。——
        for (TransferOutListRowDto row : rows) {
            if (row == null || row.getPurposeLocationId() == null) {
                continue;
            }
            row.setPurposeLocationName(nameById.get(row.getPurposeLocationId()));
        }
    }

    /**
     * 列表行补全状态中文展示（由持久化 {@code statusEnum} code 解析）。
     *
     * @param rows 列表聚合行
     */
    private void enrichTransferOutListStatusEnumText(List<TransferOutListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (TransferOutListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getStatusEnum())) {
                continue;
            }
            // —— 列表行 statusEnum 为持久化 code：解析失败则展示文案留空。——
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    /**
     * 详情行槽价：历史落库价锚定（BR-PRICE-SRC-001），禁止用当前目录价覆盖。
     *
     * @param lines 详情行列表，可为 null 或空
     */
    private void enrichTransferOutDetailLines(List<TransferOutDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (TransferOutDetailLineDto dto : lines) {
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
     * 详情回填源库可用量（拆零），与选品 {@code availableQuantity} 同口径。
     *
     * @param lines            详情明细行
     * @param sourceLocationId 调出源科室药房
     */
    private void enrichTransferOutDetailAvailableQuantity(
        List<TransferOutDetailLineDto> lines, Long sourceLocationId) {
        if (lines == null || lines.isEmpty() || sourceLocationId == null) {
            return;
        }
        // —— 按物品表分组批量取可用行，避免按明细行 N 次查库。——
        Map<String, List<Long>> itemIdsByTable = new HashMap<>();
        for (TransferOutDetailLineDto line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            String table = line.getItemTable().trim();
            itemIdsByTable.computeIfAbsent(table, k -> new ArrayList<>()).add(line.getItemId());
        }
        Map<String, Map<Long, Map<String, BigDecimal>>> availableByTableItemLot = new HashMap<>();
        for (Map.Entry<String, List<Long>> entry : itemIdsByTable.entrySet()) {
            String table = entry.getKey();
            List<Long> itemIds = entry.getValue();
            if (itemIds.isEmpty()) {
                continue;
            }
            List<InventoryCatalogAvailabilityRowModel> availRows =
                this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                    table, itemIds, List.of(sourceLocationId));
            Map<Long, Map<String, BigDecimal>> byItem =
                availableByTableItemLot.computeIfAbsent(table, k -> new HashMap<>());
            for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                    continue;
                }
                if (!Objects.equals(ar.getLocationId(), sourceLocationId)) {
                    continue;
                }
                String normLot =
                    InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(ar.getLotNumber());
                byItem.computeIfAbsent(ar.getItemId(), k -> new HashMap<>())
                    .merge(normLot, ar.getQuantity(), BigDecimal::add);
            }
        }
        for (TransferOutDetailLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                line.setAvailableQuantity(BigDecimal.ZERO);
                continue;
            }
            String table = line.getItemTable().trim();
            Map<Long, Map<String, BigDecimal>> byItem = availableByTableItemLot.get(table);
            BigDecimal avail = BigDecimal.ZERO;
            if (byItem != null) {
                Map<String, BigDecimal> byLot = byItem.get(line.getItemId());
                if (byLot != null) {
                    String normLot =
                        InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
                    BigDecimal v = byLot.get(normLot);
                    if (v != null) {
                        avail = v;
                    }
                }
            }
            line.setAvailableQuantity(avail);
        }
    }

    /**
     * 详情头回填经手人姓名，供前端下拉 pin 展示（避免仅有 id）。
     *
     * @param res 详情响应
     */
    private void enrichTransferOutDetailPractitionerName(GetTransferOutDetailResponse res) {
        if (res == null || res.getPractitionerId() == null) {
            return;
        }
        Practitioner pr = this.practitionerQueryService.findById(res.getPractitionerId());
        if (pr != null && StringUtils.isNotEmpty(pr.getName())) {
            res.setPractitionerName(pr.getName().trim());
        }
    }

    /**
     * 新建/编辑入口位置门控：源/目的均须为岗位可见且启用中的科室药房（平级调拨），且源≠目的。
     *
     * @param sourceLocationId  源科室药房主键
     * @param purposeLocationId 目的科室药房主键
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 位置越权或形态非法
     */
    private void assertTransferOutLocationGate(Long sourceLocationId, Long purposeLocationId) {
        if (sourceLocationId != null && sourceLocationId.equals(purposeLocationId)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出源与目的库位不能相同");
        }
        Set<Long> departmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(sourceLocationId, departmentPharmacies);
        this.assertDepartmentPharmacyLocationAllowed(purposeLocationId, departmentPharmacies);
    }

    /**
     * 解析岗位可见科室药房 id 集合（调出来源侧门控）。
     *
     * @return 启用中科室药房主键集合（已与管辖范围求交）
     */
    private Set<Long> resolveGatedDepartmentPharmacyLocationIdsForGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveDepartmentPharmacies());
    }

    /**
     * 断言给定科室药房主键落在门控集合且为启用中 {@link LocationForm#DEPARTMENT_PHARMACY} 形态。
     *
     * @param locationId 待校验库位
     * @param allowed    门控白名单
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 越权或形态非法
     */
    private void assertDepartmentPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEPT_TR_001, "科室药房不在当前用户可选范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "科室药房不在当前用户可选范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "科室药房不存在");
        }
        String formCode = locOpt.get().getFormEnum();
        // 平级调拨：仅接受 DEPARTMENT_PHARMACY
        if (!LocationForm.DEPARTMENT_PHARMACY.getCode().equals(formCode)
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "位置须为启用中的科室药房");
        }
    }

    /**
     * 科室调出单制单人（{@code wkf_supply_request.applicant_id}）：取当前登录用户绑定医务人员主键。
     *
     * @param notLoggedInErrorCode   未登录或缺用户主键时错误码
     * @param notLoggedInMessage     未登录提示（保留参数位与药库调出同构）
     * @param noPractitionerMessage  已登录但未绑定医务人员时的提示
     * @return 非空医务人员主键
     * @throws BusinessException 无登录用户或未绑定医务人员
     */
    private Long requireDepartmentPreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 调出明细行须携带制单时间 {@code occurrence_time}（由前端传入）；草稿阶段不写申请时间。
     */
    private void assertTransferOutLinesOccurrencePresent(List<TransferOutLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (TransferOutLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出明细制单时间不能为空");
            }
        }
    }

    /**
     * 业务单号非空门禁（多处写接口复用）。
     *
     * @param busNo 业务单号
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 空白
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "busNo 不能为空");
        }
    }

    /**
     * 断言供应请求行集合均为科室调出类型（读模型/写模型混用路径的公共守卫）。
     *
     * @param lines 行集合，可为 null
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_103} 存在非调出类型行
     */
    private void assertDepartmentTransferOut(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_TRANSFER_OUT.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_103, "单据类型非科室调出单");
            }
        }
    }

    /**
     * 列表筛选可选校验：若传入 {@code typeEnum}，则必须为 {@link SupplyType#DEPT_TRANSFER_OUT}（防前端误传其它供应类型）。
     *
     * @param typeEnumCode 请求筛选类型 code，可 null 或空白
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 非法类型
     */
    private void validateOptionalTransferOutTypeEnum(String typeEnumCode) {
        if (StringUtils.isEmpty(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "typeEnum 非法");
        }
        if (declared != SupplyType.DEPT_TRANSFER_OUT) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "typeEnum 必须为科室调出单类型");
        }
    }

    /**
     * 科室调出单不得关联科室请领上游；请领执行由药库出库承接。
     *
     * @param originalBusNo 请求中的上游单号，非空白即拒绝
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_002}
     */
    private void assertDepartmentTransferOutDoesNotReferenceDeptRequisition(String originalBusNo) {
        if (StringUtils.isNotEmpty(originalBusNo)) {
            throw new BusinessException(
                ErrorCodeConstants.DEPT_TR_002,
                "科室调出单不支持关联科室请领单，请使用药库出库单");
        }
    }

    /**
     *
     * <p><strong>数量口径</strong>：{@code item_quantity} 与 {@code wkf_inventory_item.quantity} 均为拆零单位；{@code unit_code} 不参与比较。</p>
     *
     * <p><strong>两阶段</strong>：</p>
     * <ul>
     *   <li><strong>阶段 A</strong>：行携带 {@code inventoryItemId} 时，按 {@code wkf_inventory_item.id} 批量取可用行，
     *   核对库位、物品表、物品 id 与（若填写）归一化批号后，再比请求量与行上可用量。</li>
     *   <li><strong>阶段 B</strong>：未携带库存主键时，在内存按「物品 + 归一化批号」聚合同品同批请求；无批号键时与该品在源库全部可用批之和比较。</li>
     * </ul>
     *
     * @param sourceLocationId 调出来源科室药房
     * @param lines            草稿明细
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 字段缺失或可用量不足
     */
    private void assertTransferOutDraftLinesWithinSourceAvailableInventory(Long sourceLocationId, List<TransferOutLineDto> lines) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);
        // —— 阶段 A：带 inventoryItemId —— 行级门禁，避免仅靠批号字符串聚合时把可用量算成 0 或算错批。
        Map<Long, BigDecimal> requestedByInventoryItemId = new LinkedHashMap<>();
        Map<Long, TransferOutLineDto> sampleLineByInventoryItemId = new LinkedHashMap<>();
        for (TransferOutLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.DEPT_TR_001, "调出明细数量须为正数");
            Long invId = line.getInventoryItemId();
            // —— 同一库存行多明细：调出拆零数量先累加再与该行可用量比（与调入阶段 A 对称）。——
            requestedByInventoryItemId.merge(invId, splitQty, BigDecimal::add);
            if (!sampleLineByInventoryItemId.containsKey(invId)) {
                sampleLineByInventoryItemId.put(invId, line);
            }
        }
        if (!requestedByInventoryItemId.isEmpty()) {
            List<Long> invIds = new ArrayList<>(requestedByInventoryItemId.keySet());
            List<InventoryCatalogAvailabilityRowModel> rowsById =
                this.inventoryItemQueryService.findAvailableCatalogRowsForInventoryItemIds(invIds, locationIds);
            Map<Long, InventoryCatalogAvailabilityRowModel> rowByInventoryItemId = new HashMap<>();
            for (InventoryCatalogAvailabilityRowModel row : rowsById) {
                if (row == null || row.getId() == null) {
                    continue;
                }
                rowByInventoryItemId.put(row.getId(), row);
            }
            for (Map.Entry<Long, BigDecimal> want : requestedByInventoryItemId.entrySet()) {
                Long invId = want.getKey();
                BigDecimal requested = want.getValue();
                // —— 可用投影必须仍包含该 id；缺失则说明草稿引用了已不可用库存行。——
                InventoryCatalogAvailabilityRowModel row = rowByInventoryItemId.get(invId);
                TransferOutLineDto sample = sampleLineByInventoryItemId.get(invId);
                if (row == null || sample == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEPT_TR_001,
                        "所选库存行当前不可用、已锁定或已过期，请刷新选品后再试");
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "所选库存行与当前源科室药房不一致");
                }
                String rowTable = "";
                if (row.getItemTable() != null) {
                    rowTable = row.getItemTable().trim();
                }
                if (!rowTable.equals(sample.getItemTable().trim())) {
                    throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "所选库存行物品表与明细不一致");
                }
                if (!Objects.equals(row.getItemId(), sample.getItemId())) {
                    throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "所选库存行物品主键与明细不一致");
                }
                String lineLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(sample.getLotNumber());
                if (!lineLotKey.isEmpty()) {
                    // —— 明细声明批号则必须与库存行归一化批号一致，防止串批调出。——
                    String rowLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                    if (!lineLotKey.equals(rowLotKey)) {
                        throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "明细批号与所选库存行批号不一致");
                    }
                }
                BigDecimal available = row.getQuantity();
                if (available == null) {
                    available = BigDecimal.ZERO;
                }
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEPT_TR_001,
                        "源库该库存行可用量不足，请减少调出数量或刷新库存后再试");
                }
            }
        }
        // —— 阶段 B：无 inventoryItemId —— 在固定来源库位 + 物品表下用「物品 id → 归一化批号」二维 Map 聚合，与 DM 四列唯一一致。
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (TransferOutLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() != null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "调出明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.DEPT_TR_001, "调出明细数量须为正数");
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            if (lotKeyPart.isEmpty()) {
                // 调出行未约束到具体批号：用该物品在来源库全部可用批之和做上限（与调入阶段 B「无批号」语义一致）。
                Map<Long, BigDecimal> perItem =
                    requestedWithoutLotByTable.computeIfAbsent(table, t -> new HashMap<>());
                perItem.merge(itemId, splitQty, BigDecimal::add);
            } else {
                // 调出行约束到批号：在固定库位 + 表名下按物品 id 与归一化批号累加请求量。
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
            // —— 每张物理表单独查可用目录：避免跨表 OR 混用谓词。——
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
            // —— 有批号维请求：逐归一化批号键与聚合可用量比。——
            for (Map.Entry<Long, Map<String, BigDecimal>> wantItem : wantedByItemLot.entrySet()) {
                Long itemId = wantItem.getKey();
                Map<String, BigDecimal> wantedLots = wantItem.getValue();
                Map<String, BigDecimal> availLots = availableByItemLot.getOrDefault(itemId, Collections.emptyMap());
                for (Map.Entry<String, BigDecimal> wantLot : wantedLots.entrySet()) {
                    BigDecimal requested = wantLot.getValue();
                    BigDecimal available = availLots.getOrDefault(wantLot.getKey(), BigDecimal.ZERO);
                    if (requested.compareTo(available) > 0) {
                        throw new BusinessException(
                            ErrorCodeConstants.DEPT_TR_001,
                            "源库该批号可用量不足，请减少调出数量或刷新库存后再试");
                    }
                }
            }
            // —— 无批号维请求：与按品纵向汇总的可用总量比（允许多批混扣）。——
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEPT_TR_001,
                        "源库可用量不足，请减少调出数量或刷新库存后再试");
                }
            }
        }
    }

    /**
     * 读取同号调出单首行状态枚举（提交/撤回后用于响应装配；无行或状态码空白时返回 null）。
     *
     * @param busNo 业务单号
     * @return 首行状态枚举；无法解析时返回 null
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
     * 新建/编辑草稿：追溯码总串与逗号分段长度宽限校验（与进货同源常量 {@link GeneralSharedConstants#TRACE_NO_WIDE_MAX_LENGTH}）。
     *
     * @param lines     调出明细，可 null
     * @param errorCode 失败时业务错误码（新建与修订可区分）
     * @throws BusinessException 超长
     */
    private void assertTransferOutTraceNosWithinWideLimit(List<TransferOutLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (TransferOutLineDto line : lines) {
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
                    throw new BusinessException(errorCode, "追溯码片段长度超过允许上限（" + maxLen + "）");
                }
            }
        }
    }

    /**
     * 将批号规范为库存五元组匹配用字符串（空批号用空串与领域查询约定一致）。
     *
     * @param lotNumber 原始批号，可 null
     * @return trim 后字符串；空白则为空串
     */
    private String resolveLotNumberForTransferOutInventory(String lotNumber) {
        if (StringUtils.isEmpty(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    /**
     * 从批量产品读模型中解析展示名称（目的库新建库存项时需要非空名称）。
     *
     * @param line          供应请求行
     * @param productByKey  复合键 → 产品读模型
     * @return 非空商品名；键缺失时抛业务异常
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_104} 产品定义缺失
     */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isEmpty(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    /**
     * 解析追溯记录上的源位置类型持久化 code：优先行上源类型，其次头行，最后默认药库（{@link SupplyRequestType#CABINET}）。
     *
     * @param line 当前明细行，可 null
     * @param head 同号头行，可 null
     * @return 位置类型 code，永不为 null
     */
    private String resolveSourceLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotEmpty(line.getSourceTypeEnum())) {
            return line.getSourceTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotEmpty(head.getSourceTypeEnum())) {
            return head.getSourceTypeEnum().trim();
        }
        return SupplyRequestType.CABINET.getCode();
    }

    /**
     * 出库目的库位类型码：行/头 purposeTypeEnum 优先，否则按 purposeLocationId 解析。
     *
     * @param line 明细行
     * @param head 单头行
     * @return 目的类型码
     */
    private String resolvePurposeLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotEmpty(line.getPurposeTypeEnum())) {
            return line.getPurposeTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotEmpty(head.getPurposeTypeEnum())) {
            return head.getPurposeTypeEnum().trim();
        }
        Long purposeLocationId = null;
        if (line != null && line.getPurposeLocationId() != null) {
            purposeLocationId = line.getPurposeLocationId();
        } else if (head != null && head.getPurposeLocationId() != null) {
            purposeLocationId = head.getPurposeLocationId();
        }
        if (purposeLocationId != null) {
            return this.locationQueryService.requireSupplyRequestTypeForLocationId(purposeLocationId).getCode();
        }
        return SupplyRequestType.PHARMACY.getCode();
    }

    /**
     * 选品接口物品表白名单门控（药品定义表 / 设备定义表）。
     *
     * @param raw 请求 productTableName
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_001} 空白或不在白名单
     */
    private void assertProductTableNameWhitelistForTransferOutSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_001, "productTableName 非法");
        }
    }

    /**
     * 供应工作流领域异常到调出接口错误码的归一映射（非法参数类错误回落到接口默认码）。
     *
     * @param ex          领域抛出的业务异常
     * @param defaultCode 当 {@code ex} 为 {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} 时包装为该码；否则原样返回 {@code ex}
     * @return 供调用方继续抛出的异常实例
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * 同意链专用：将 {@code agreeRequest} 抛出的非法参数类错误分流为「重复同意」与「其它工作流拒绝」。
     *
     * @param ex 领域业务异常
     * @return 非法参数类：按文案含「已同意」映射为 {@link ErrorCodeConstants#DEPT_TR_104}，否则为 {@link ErrorCodeConstants#DEPT_TR_003}；其它错误码原样返回 {@code ex}
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.DEPT_TR_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEPT_TR_003, msg);
        }
        return ex;
    }

    /**
     * 按调出单目的位置类型解析对侧调入壳单的 {@code type_enum}。
     *
     * @param head 已同意调出头行
     * @return 对侧调入 type 持久化码
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_TR_104} 目的位置类型无法映射
     */
    private String resolveCounterpartyTransferInType(SupplyRequestModel head) {
        if (head == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "调出头行缺失，无法解析对侧调入类型");
        }
        Long purposeLocationId = head.getPurposeLocationId();
        if (purposeLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "调出头行缺少目的库位，无法生成调入壳单");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(purposeLocationId);
        if (locOpt.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "目的库位不存在，无法生成调入壳单");
        }
        String formCode = locOpt.get().getFormEnum();
        // 平级调拨：科室药房调出目的须同为科室药房，对侧壳单恒为 DEPT_TRANSFER_IN
        if (!LocationForm.DEPARTMENT_PHARMACY.getCode().equals(formCode)) {
            throw new BusinessException(ErrorCodeConstants.DEPT_TR_104, "科室药房调出目的须为科室药房（平级调拨）");
        }
        return SupplyType.DEPT_TRANSFER_IN.getCode();
    }

    /**
     * 同意调出编排内部结构：承载目的侧入账所需字段（在源库合并扣减之后逐条消费）。
     */
    private static final class TransferOutDestIncr {
        private SupplyRequestModel line;
        private Long purposeLocationId;
        private BigDecimal splitQty;
        private String resolvedLot;
        private String productName;
        private String splitUnitCode;
    /** 目的侧新建库存行时冗余分类/检索码；与 agree 循环内批量读模型快照一致。 */
        private ProductDefinitionDisplayDto productDefinition;
    /** 源库命中行上的供应商标识，用于目的库五元组定位与首次建账 {@code supplier_id}。 */
        private Long sourceInventorySupplierId;
    /** 源库命中行生产日期，目的库新建时供应行未带 {@code start_time} 则承袭。 */
        private LocalDateTime sourceInventoryProductionDate;
    /** 源库命中行效期，目的库新建时供应行未带 {@code end_time} 则承袭。 */
        private LocalDateTime sourceInventoryExpirationDate;
    }
}
