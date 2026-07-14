/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissuereturn.service;

import com.openhis.health.application.inventory.department.deptissuereturn.dto.DeptIssueReturnDetailLineDto;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.DeptIssueReturnLineDto;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.DeptIssueReturnListRowDto;
import com.openhis.health.application.inventory.department.deptissuereturn.dto.LinkableIssueListRowDto;
import com.openhis.health.application.inventory.department.deptissuereturn.mapper.InventoryDepartmentDeptIssueReturnMapper;
import com.openhis.health.application.inventory.department.deptissuereturn.request.*;
import com.openhis.health.application.inventory.department.deptissuereturn.response.*;
import com.openhis.health.application.inventory.department.deptissuereturn.support.DeptIssueReturnLineHelper;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberSupplyAgreeTraceOrchestrator;
import com.openhis.health.application.shared.tracenumber.RecordTraceFromSupplyLineSpec;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryDepartmentConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByOriginalBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.application.inventory.support.SupplyRequestPricedWriteOrchestrator;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueReturnExtensionIn;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.dao.OptimisticLockingFailureException;
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
 * 科室退领单（{@link SupplyType#DEPT_ISSUE_RETURN}，AS-INV-DEP-009）应用编排（IF-DEP-TL · SDD §3.1）。
 *
 * <p>明细须从已同意 {@link SupplyType#DEPT_ISSUE} 经 {@code original_bus_no} 拉取；同意后在
 * {@code purpose_location_id} 科室药房加回库存（{@code increaseQuantity} / {@code createInventoryItem}），
 * 无壳单、无跨库调拨。</p>
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link SupplyRequestDomainService} / {@link SupplyRequestQueryService}：退领单写模型与列表/详情/可编辑态门禁</li>
 *   <li>{@link SupplyDeliveryDomainService} / {@link SupplyDeliveryQueryService}：同意发放与幂等门禁</li>
 *   <li>{@link InventoryItemDomainService} / {@link InventoryItemQueryService}：同意加回与匹配键</li>
 *   <li>{@link LocationQueryService} / {@link OrganizationQueryService}：科室药房与上级科室只读 enrich</li>
 *   <li>{@link PractitionerRoleScopeLoader}：药师岗位 agree 门禁（非财务岗）</li>
 *   <li>{@link TraceNumberSupplyAgreeTraceOrchestrator}：条件追溯写入库轨迹</li>
 *   <li>{@link DeptIssueReturnLineHelper}：行键、可退余量与草稿行校验</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inventoryDepartmentDeptIssueReturn/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentDeptIssueReturnAppService {

    /** 会话机构与管辖组织解析。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 操作人与制单医务人员档案解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 分页窗口必填门禁。 */
    private final PageWindowGate pageWindowGate;
    /** 供应请求写模型（建单/改单/审批/删除）。 */
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestPricedWriteOrchestrator supplyRequestPricedWriteOrchestrator;
    /** 供应请求读模型（列表/详情/校验）。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** 供应发放写模型（同意落账）。 */
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    /** 供应发放读模型（发放完成态幂等）。 */
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    /** 库存项写模型（同意加回）。 */
    private final InventoryItemDomainService inventoryItemDomainService;
    /** 库存项读模型（匹配键）。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    /** 库位读模型（科室药房门控与展示名）。 */
    private final LocationQueryService locationQueryService;
    /** 组织读模型（领用科室展示名）。 */
    private final OrganizationQueryService organizationQueryService;
    /** 医务人员读模型（列表制单/审批人 enrich）。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 岗位范围加载（init 药师、agree 药师门禁）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    /** 管辖库位 Gate（列表/建单/拉取源单）。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    /** 产品定义批量读（详情 enrich）。 */
    /** 选品术语与单位展示名。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 追溯码写入（同意条件分支，入库向）。 */
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;
    /** 目的侧新建库存行命令装配（对偶损益报溢）。 */
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    /** HTTP 契约与领域命令装配。 */
    private final InventoryDepartmentDeptIssueReturnMapper deptIssueReturnMapper;
    /** 医保进销存第三方上报扩展点。 */
    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    /**
     * 加载页主数据（科室药房、状态、药师下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 表单初始化选项集合
     */
    public LoadDeptIssueReturnFormInitResponse loadDeptIssueReturnFormInit(LoadDeptIssueReturnFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位与人员
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEP_TL_101, "机构上下文缺失");
        try {
            // Pre：拉取启用科室药房/药师并经管辖 Gate 收窄
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> gatedDepartmentPharmacies =
                this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            // Asm：下拉选项写入初始化响应
            return deptIssueReturnMapper.toLoadDeptIssueReturnFormInitResponse(
                deptIssueReturnMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies),
                deptIssueReturnMapper.toOptionDtoListSupplyStatuses(),
                deptIssueReturnMapper.toOptionDtoListFromPractitioners(pharmacists));
        } catch (RuntimeException ex) {
            log.warn("loadDeptIssueReturnFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_TL_101, "退领页主数据加载失败");
        }
    }

    /**
     * 分页查询科室退领单列表。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     */
    public PageDeptIssueReturnsResponse pageDeptIssueReturns(PageDeptIssueReturnsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_TL_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_TL_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        // Pre：组装供应单据列表查询条件
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_ISSUE_RETURN.getCode());
        if (Long.valueOf(InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID)
            .equals(request.getPurposeLocationId())) {
            if (allowedDepartmentPharmacies.isEmpty()) {
                listQuery.setPurposeLocationIds(
                    List.of(InventoryDepartmentConstants.LIST_FILTER_NO_VISIBLE_DEPARTMENT_PHARMACY_PLACEHOLDER_ID));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedDepartmentPharmacies));
            }
        } else {
            this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        listQuery.setStartDate(request.getApplyTimeFrom());
        listQuery.setEndDate(request.getApplyTimeTo());
        try {
            // Cmd：按 busNo 去重分页查询供应请求行
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<DeptIssueReturnListRowDto> records =
                this.deptIssueReturnMapper.toDeptIssueReturnListAggregated(busNos, lines);
            this.enrichDeptIssueReturnListPurposeLocationNames(records);
            this.enrichDeptIssueReturnListOrganizationNames(records);
            this.enrichDeptIssueReturnListStatusEnumText(records);
            this.enrichDeptIssueReturnListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合行映射为分页响应
            return this.deptIssueReturnMapper.toPageDeptIssueReturnsAggregatedResponse(
                mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageDeptIssueReturns failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_TL_102, "退领单列表查询失败");
        }
    }

    /**
     * 按业务单号查询科室退领单详情。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     */
    public GetDeptIssueReturnDetailResponse getDeptIssueReturnDetail(GetDeptIssueReturnDetailRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "busNo 不能为空");
        }
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_103, "退领单不存在");
        }
        this.assertDeptIssueReturn(lines);
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
            log.warn("batchGetProductByIds degraded for dept issue return detail", ex);
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
        GetDeptIssueReturnDetailResponse res =
            deptIssueReturnMapper.toGetDeptIssueReturnDetailResponse(lines, hints);
        SupplyRequestModel head = lines.get(0);
        if (head.getPurposeLocationId() != null) {
            Optional<Location> locOpt = locationQueryService.findOptionalById(head.getPurposeLocationId());
            if (locOpt.isPresent() && StringUtils.isNotEmpty(locOpt.get().getName())) {
                res.setPurposeLocationName(locOpt.get().getName().trim());
            }
            Long orgId = null;
            if (locOpt.isPresent()) {
                orgId = locOpt.get().getOrganizationId();
            }
            if (orgId != null) {
                res.setConsumingOrganizationId(String.valueOf(orgId));
                Map<Long, String> orgNames = this.organizationQueryService.findOrganizationNamesByIds(List.of(orgId));
                res.setConsumingOrganizationName(orgNames.get(orgId));
            }
        }
        this.enrichDeptIssueReturnDetailProductDisplay(res.getLines(), products, terminologyMaps);
        // Asm：槽价按落库行价 enrich（BR-PRICE-SRC-001 历史业务，禁止覆盖为当前目录价）
        this.enrichDeptIssueReturnDetailLinesCatalogPricing(res.getLines());
        return res;
    }

    /**
     * 新建科室退领单（草稿/待提交）。
     *
     * @param request 含源领用单号、库位、明细行等业务字段
     * @return 新建业务单号
     */
    public CreateDeptIssueReturnResponse createDeptIssueReturn(CreateDeptIssueReturnRequest request) {
        // Gate：请求体、源单、库位与明细不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "请求不能为空");
        }
        if (StringUtils.isEmpty(request.getOriginalBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "originalBusNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "退领明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_TL_101, "机构上下文缺失");
        this.assertDeptIssueReturnPurposeLocationGate(request.getPurposeLocationId());
        String originalBusNoTrim = request.getOriginalBusNo().trim();
        this.validateSourceDeptIssueForReturn(originalBusNoTrim, request.getPurposeLocationId());
        this.assertDraftLinesBoundToIssueAndWithinReturnable(
            originalBusNoTrim, request.getPurposeLocationId(),
            this.toDraftReturnLinesFromLineDtos(request.getLines()), null);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_TL_101, "用户未登录");
        Long applicantId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_TL_101, "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：领域建单并分配 busNo
            String busNo = supplyRequestPricedWriteOrchestrator.createDeptIssueReturn(
                deptIssueReturnMapper.toCreateDeptIssueReturnCommand(request, operatorId, applicantId, null));
            // Asm：返回新建 busNo
            return deptIssueReturnMapper.toCreateDeptIssueReturnResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "退领参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            throw this.mapDeptIssueReturnLocationException(ex);
        }
    }

    /**
     * 更新可编辑态科室退领单（全量明细覆盖，同号软删重插）。
     *
     * @param request 含 busNo、明细与库位
     * @return 更新后的 busNo（与请求相同）
     */
    public UpdateDeptIssueReturnResponse updateDeptIssueReturn(UpdateDeptIssueReturnRequest request) {
        // Gate：busNo、明细与库位不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "退领明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_TL_101, "机构上下文缺失");
        this.assertDeptIssueReturnPurposeLocationGate(request.getPurposeLocationId());
        // Pre：回读存量并校验工作流可编辑态
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_103, "退领单不存在");
        }
        this.assertDeptIssueReturn(existing);
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_004, "当前状态不允许编辑退领单");
        }
        String originalBusNoSnapshot = head.getOriginalBusNo();
        if (StringUtils.isEmpty(originalBusNoSnapshot)) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "退领单缺少源领用单号");
        }
        this.validateSourceDeptIssueForReturn(originalBusNoSnapshot.trim(), request.getPurposeLocationId());
        this.assertDraftLinesBoundToIssueAndWithinReturnable(
            originalBusNoSnapshot.trim(), request.getPurposeLocationId(),
            this.toDraftReturnLinesFromLineDtos(request.getLines()), request.getBusNo().trim());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_TL_101, "用户未登录");
        Long applicantId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_TL_101, "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：同号全量对齐改单（领域软删重插）
            String busNo = supplyRequestPricedWriteOrchestrator.createDeptIssueReturn(
                deptIssueReturnMapper.toCreateDeptIssueReturnCommand(
                    request, operatorId, applicantId, originalBusNoSnapshot.trim()));
            // Asm：返回 busNo
            return deptIssueReturnMapper.toUpdateDeptIssueReturnResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "退领参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_105, "退领单已被并发修改，请刷新后重试", ex);
        } catch (BusinessException ex) {
            throw this.mapDeptIssueReturnLocationException(ex);
        }
    }

    /**
     * 提交科室退领单审批。
     *
     * @param request 须含 busNo
     * @return 提交成功响应
     */
    public SubmitDeptIssueReturnApprovalResponse submitDeptIssueReturnApproval(
        SubmitDeptIssueReturnApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_TL_101, "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批，状态迁移至待审核
            supplyRequestDomainService.submitApproval(
                deptIssueReturnMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_TL_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_TL_003);
        }
        // Asm：回读提交后状态写入响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return deptIssueReturnMapper.toSubmitDeptIssueReturnApprovalResponse(st);
    }

    /**
     * 撤回科室退领单审批（回到可编辑态）。
     *
     * @param request 须含 busNo
     * @return 撤回成功响应
     */
    public WithdrawDeptIssueReturnApprovalResponse withdrawDeptIssueReturnApproval(
        WithdrawDeptIssueReturnApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批，单据回到可编辑态
            supplyRequestDomainService.withdrawApproval(
                deptIssueReturnMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_TL_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_TL_003);
        }
        // Asm：回读撤回后状态写入响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return deptIssueReturnMapper.toWithdrawDeptIssueReturnApprovalResponse(st);
    }

    /**
     * 科室退领单同意编排：领域落已同意态 → 退领发放 → 科室药房加回 → 条件追溯（入库向）。
     *
     * <p>须药师岗位；重复 agree、发放已完成等场景抛 {@link ErrorCodeConstants#DEP_TL_104}。</p>
     *
     * @param request 含 busNo；须未重复同意
     * @return 同意后 busNo 与状态 code
     */
    public AgreeDeptIssueReturnResponse agreeDeptIssueReturn(AgreeDeptIssueReturnRequest request) {
        // Gate：busNo、机构上下文与药师岗位
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "busNo 不能为空");
        }
        Long sessionOrgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEP_TL_101, "机构上下文缺失");
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_TL_101, "用户未登录");
        Long operatorPractitionerId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_TL_101, "当前用户未绑定医务人员档案，无法审批");
        Set<Long> pharmacistIds = this.practitionerRoleScopeLoader.resolvePractitionerIdsForActivePlatformRole(
            WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, sessionOrgId);
        if (!pharmacistIds.contains(operatorPractitionerId)) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "当前用户无药师岗位，无法同意退领单");
        }

        // Pre：回读退领单并校验未重复同意与可退余量
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_103, "退领单不存在");
        }
        this.assertDeptIssueReturn(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "退领单已同意，禁止重复同意");
        }
        SupplyRequestModel agreeHead = beforeLines.get(0);
        if (StringUtils.isEmpty(agreeHead.getOriginalBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "退领单缺少源领用单号");
        }
        this.assertDraftLinesBoundToIssueAndWithinReturnable(
            agreeHead.getOriginalBusNo().trim(),
            agreeHead.getPurposeLocationId(),
            this.toDraftReturnLinesFromSupplyModels(beforeLines),
            request.getBusNo().trim());

        // Cmd：领域状态机同意退领单
        try {
            supplyRequestDomainService.agreeRequest(deptIssueReturnMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_105, "退领单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
        this.supplyRequestDomainService.flushPendingChanges();
        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertDeptIssueReturn(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "同意后回读状态异常，中止退领编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "退领明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "退领明细数量无效，无法完成编排");
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
            throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "供应请求行已存在完成态发放，禁止重复同意退领");
        }

        // Cmd：退领发放落库
        try {
            supplyDeliveryDomainService.createSupplyDelivery(
                deptIssueReturnMapper.toCreateSupplyDeliveryCommand(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "退领发放落库失败：" + ex.getMessage());
        }

        // Pre：批量产品读模型，供拆零量与报溢建库存行使用
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
                log.warn("batchGetProductByIds failed during dept issue return inventory assemble", ex);
            }
        }

        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        List<RecordTraceFromSupplyLineSpec> traceCommands = new ArrayList<>();

        // Cmd：逐行在 purpose 科室药房加回库存（命中则加量，否则新建库存项）
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.DEP_TL_104, "供应请求行 item_quantity 须为拆零正数量");
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());
            InventoryMatchKey matchKey = new InventoryMatchKey();
            matchKey.setLotNumber(resolvedLot);
            matchKey.setLocationId(purposeLocationId);
            matchKey.setItemTable(line.getItemTable());
            matchKey.setItemId(line.getItemId());
            Long inventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(matchKey).orElse(null);
            try {
                if (inventoryId != null) {
                    IncreaseQuantityCommand incCmd =
                        inboundReceiptMapper.toIncreaseQuantityCommand(inventoryId, splitQty, operatorId);
                    inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    String productName = this.resolveProductName(line, productByKey);
                    String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
                    ProductDefinitionDisplayDto product = productByKey.get(key);
                    String splitUnitCode = this.resolveSplitUnitCode(line, product);
                    CreateInventoryItemCommand createCmd = inboundReceiptMapper.toCreateInventoryItemCommand(
                        line,
                        purposeLocationId,
                        productName,
                        resolvedLot,
                        splitQty,
                        splitUnitCode,
                        operatorId,
                        product);
                    inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "退领库存加回失败：" + ex.getMessage());
            }

            if (StringUtils.isNotEmpty(line.getTraceNo())) {
                String lt = this.resolvePurposeLocationTypeCode(line, head);
                String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                String splitUnitCode = this.resolveSplitUnitCode(line, product);
                RecordTraceFromSupplyLineSpec traceCmd = new RecordTraceFromSupplyLineSpec();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setSupplyTypeCode(SupplyType.DEPT_ISSUE_RETURN.getCode());
                traceCmd.setBusNo(busNoTrim);
                traceCmd.setItemTable(line.getItemTable().trim());
                traceCmd.setItemId(line.getItemId());
                traceCmd.setLotNumber(resolvedLot);
                traceCmd.setTraceNos(line.getTraceNo());
                traceCmd.setQuantity(splitQty);
                traceCmd.setUnitCode(splitUnitCode);
                traceCmd.setUnitConversionSource(ProductDefinitionDisplaySupport.toUnitConversionSource(product));
                traceCmd.setLocationId(purposeLocationId);
                traceCmd.setLocationTypeEnum(lt);
                traceCmd.setRecordDirectionEnum(com.openhis.health.domain.share.enums.TraceDirection.IN.getCode());
                traceCommands.add(traceCmd);
            }
        }

        if (!traceCommands.isEmpty()) {
            try {
                traceNumberSupplyAgreeTraceOrchestrator.recordSingleEndLines(traceCommands);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "追溯码退领入库记录失败：" + ex.getMessage());
            }
        }

        tryUploadMedicareInventoryAfterDeptIssueReturnAgree(request, agreedLines);

        // Asm：映射同意响应
        return deptIssueReturnMapper.toAgreeDeptIssueReturnResponse(
            busNoTrim, SupplyStatus.AGREE.getCode());
    }

    /**
     * 退领 agree 后尝试上报医保 3502 其他入库（集成层降级，失败不阻断主流程）。
     *
     * @param request     agree 请求
     * @param agreedLines 已同意退领明细行
     */
    private void tryUploadMedicareInventoryAfterDeptIssueReturnAgree(
        AgreeDeptIssueReturnRequest request,
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
            InventoryUploadAfterDeptIssueReturnExtensionIn command = new InventoryUploadAfterDeptIssueReturnExtensionIn()
                .setDeptIssueReturnBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setContractBusNo(request.getContractBusNo());
            inventoryUploadExtension.uploadAfterDeptIssueReturnAgree(command);
        });
    }

    /**
     * 驳回科室退领单。
     *
     * @param request 须含 busNo 与驳回原因
     * @return 驳回成功标志
     */
    public RejectDeptIssueReturnResponse rejectDeptIssueReturn(RejectDeptIssueReturnRequest request) {
        // Gate：busNo 与驳回原因不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getChangeReason()) && StringUtils.isEmpty(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "驳回原因不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_TL_101, "用户未登录");
        try {
            // Cmd：领域驳回，须携带驳回原因
            supplyRequestDomainService.rejectRequest(deptIssueReturnMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_TL_003);
        }
        // Asm：返回驳回成功标志
        return deptIssueReturnMapper.toRejectDeptIssueReturnResponse(true);
    }

    /**
     * 删除科室退领单（仅草稿/可删态）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     */
    public RemoveDeptIssueReturnResponse removeDeptIssueReturn(RemoveDeptIssueReturnRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_TL_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删（仅可删态）
            supplyRequestDomainService.removeByBusNo(deptIssueReturnMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_TL_005);
        }
        // Asm：返回删除成功标志
        return deptIssueReturnMapper.toRemoveDeptIssueReturnResponse(true);
    }

    /**
     * 分页查询可关联领用单（已同意且仍有可退余量，IF-DEP-TL-12）。
     *
     * @param request 须含 {@code purposeLocationId} 与分页参数
     * @return 按 {@code bus_no} 聚合的领用单候选行
     */
    public PageLinkableIssueDocumentsResponse pageLinkableIssueDocuments(PageLinkableIssueDocumentsRequest request) {
        // Gate：科室药房与分页窗口
        if (request == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "purposeLocationId 不能为空");
        }
        this.pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_TL_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_TL_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);

        // Pre：组装已同意领用单聚合列表查询
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_ISSUE.getCode());
        listQuery.setStatusEnum(SupplyStatus.AGREE.getCode());
        listQuery.setPurposeLocationId(request.getPurposeLocationId());
        PageQueryBinder.bind(request, listQuery);
        try {
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<LinkableIssueListRowDto> filtered = new ArrayList<>();
            for (String busNo : busNos) {
                if (StringUtils.isEmpty(busNo)) {
                    continue;
                }
                LinkableIssueListRowDto row = this.buildLinkableIssueListRow(
                    busNo.trim(), lines, request.getPurposeLocationId());
                if (row == null) {
                    continue;
                }
                if (!Boolean.TRUE.equals(row.getReturnable())) {
                    continue;
                }
                filtered.add(row);
            }
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：映射分页响应
            return this.deptIssueReturnMapper.toPageLinkableIssueDocumentsAggregatedResponse(
                mergedTotal, pageNo, pageSize, filtered);
        } catch (RuntimeException ex) {
            log.warn("pageLinkableIssueDocuments failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_TL_102, "可关联领用单查询失败");
        }
    }

    /**
     * 按领用单号拉取退领草稿预填（IF-DEP-TL-13）。
     *
     * @param request 须含 {@code originalBusNo} 与 {@code purposeLocationId}
     * @return 可退行预填（含可退余量）
     */
    public GetLinkableIssueDetailResponse getLinkableIssueDetail(GetLinkableIssueDetailRequest request) {
        // Gate：参数非空与机构/科室药房门禁
        if (request == null || StringUtils.isEmpty(request.getOriginalBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "originalBusNo 不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_TL_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        String originalBusNoTrim = request.getOriginalBusNo().trim();
        this.validateSourceDeptIssueForReturn(originalBusNoTrim, request.getPurposeLocationId());
        this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);

        List<SupplyRequestModel> issueLines = this.loadIssueLinesByBusNo(originalBusNoTrim);
        if (issueLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_103, "源领用单不存在");
        }
        SupplyRequestModel head = issueLines.get(0);
        Long issuePurposeLocationId = head.getPurposeLocationId();
        if (issuePurposeLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_002, "源领用单缺少科室药房，无法拉取退领预填");
        }
        if (!request.getPurposeLocationId().equals(issuePurposeLocationId)) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_002, "源领用单科室药房与请求不一致");
        }
        List<SupplyRequestModel> returnsReferencingIssue = this.loadReturnsReferencingIssue(originalBusNoTrim);
        String excludeReturnBusNo = request.getExcludeReturnBusNo();
        // Pre：按行键计算可退余量
        List<DeptIssueReturnLineHelper.ReturnableLineSnapshot> snapshots =
            DeptIssueReturnLineHelper.buildReturnableLines(issueLines, returnsReferencingIssue, excludeReturnBusNo);
        // Pre：批量装载产品读模型，供可退行展示项目名/规格/单位
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : issueLines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
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
            log.warn("batchGetProductByIds degraded for linkable issue detail", ex);
            products = new HashMap<>();
        }
        // Asm：映射退领预填响应（行价保留源领用落库价）
        return deptIssueReturnMapper.toGetLinkableIssueDetailResponse(issueLines, snapshots, products);
    }

    /**
     * 列表行回填科室药房展示名。
     *
     * @param rows 列表聚合行
     */
    private void enrichDeptIssueReturnListPurposeLocationNames(List<DeptIssueReturnListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> purposeIds = new HashSet<>();
        for (DeptIssueReturnListRowDto row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getPurposeLocationId())) {
                try {
                    purposeIds.add(Long.valueOf(row.getPurposeLocationId().trim()));
                } catch (NumberFormatException ignored) {
                    // 非法 id 跳过
                }
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
        for (DeptIssueReturnListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getPurposeLocationId())) {
                continue;
            }
            try {
                Long pid = Long.valueOf(row.getPurposeLocationId().trim());
                row.setPurposeLocationName(nameById.get(pid));
            } catch (NumberFormatException ignored) {
                // 非法 id 跳过
            }
        }
    }

    /**
     * 列表行回填领用科室展示名。
     *
     * @param rows 列表聚合行
     */
    private void enrichDeptIssueReturnListOrganizationNames(List<DeptIssueReturnListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> orgIds = new HashSet<>();
        for (DeptIssueReturnListRowDto row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getConsumingOrganizationId())) {
                try {
                    orgIds.add(Long.valueOf(row.getConsumingOrganizationId().trim()));
                } catch (NumberFormatException ignored) {
                    // 非法 id 跳过
                }
            }
        }
        if (orgIds.isEmpty()) {
            return;
        }
        Map<Long, String> orgNameById = this.organizationQueryService.findOrganizationNamesByIds(orgIds);
        for (DeptIssueReturnListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getConsumingOrganizationId())) {
                continue;
            }
            try {
                Long orgId = Long.valueOf(row.getConsumingOrganizationId().trim());
                row.setConsumingOrganizationName(orgNameById.get(orgId));
            } catch (NumberFormatException ignored) {
                // 非法 id 跳过
            }
        }
    }

    /**
     * 列表行回填供应状态中文展示。
     *
     * @param rows 列表聚合行
     */
    private void enrichDeptIssueReturnListStatusEnumText(List<DeptIssueReturnListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (DeptIssueReturnListRowDto row : rows) {
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
     * 列表行回填申请人、审批人姓名。
     *
     * @param rows        列表聚合行
     * @param detailLines 本页全部供应请求明细
     */
    private void enrichDeptIssueReturnListDisplayNames(
        List<DeptIssueReturnListRowDto> rows,
        List<SupplyRequestModel> detailLines) {
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
        for (DeptIssueReturnListRowDto row : rows) {
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
     * 详情明细回填产品名、规格、厂家与行单位展示名。
     *
     * @param lines           详情明细行
     * @param products        产品读模型映射
     * @param terminologyMaps 选品术语
     */
    private void enrichDeptIssueReturnDetailProductDisplay(
        List<DeptIssueReturnDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps) {
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
        for (DeptIssueReturnDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            dto.setItemName(pr.getProductName());
            dto.setSpec(pr.getTotalVolume());
            dto.setManufacturer(pr.getManufacturer());
            dto.setUnitName(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, dto.getUnitCode(), pr));
            ProductDefinitionCatalogEditorHintsDto catalog = dto.getProductCatalog();
            if (catalog != null && catalog.getUnitBands() != null && !catalog.getUnitBands().isEmpty()) {
                this.productDefinitionDisplayTerminologyAssembler.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 详情明细目录价带与持久化行价对齐（BR-PRICE-SRC-001：仅用 supplyLine.price）。
     *
     * @param lines 详情明细行
     */
    private void enrichDeptIssueReturnDetailLinesCatalogPricing(List<DeptIssueReturnDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (DeptIssueReturnDetailLineDto dto : lines) {
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
     * 校验退领科室药房在管辖 Gate 内、为启用中形态且上级科室非空。
     *
     * @param purposeLocationId 科室药房位置 id
     */
    private void assertDeptIssueReturnPurposeLocationGate(Long purposeLocationId) {
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(purposeLocationId, allowedDepartmentPharmacies);
        Optional<Location> locOpt = locationQueryService.findOptionalById(purposeLocationId);
        if (locOpt.isEmpty() || locOpt.get().getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_002, "科室药房未配置上级科室，无法退领");
        }
    }

    /**
     * 当前用户可见的启用中科室药房位置 id 集合。
     *
     * @return 管辖 Gate 内科室药房 id 集合
     */
    private Set<Long> resolveGatedDepartmentPharmacyLocationIdsForGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveDepartmentPharmacies());
    }

    /**
     * 断言库位在允许集合内且形态/状态为启用中科室药房。
     *
     * @param locationId 待校验库位
     * @param allowed    管辖 Gate 内允许 id 集合
     */
    private void assertDepartmentPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEP_TL_002, "科室药房不在当前用户可选范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_002, "科室药房不在当前用户可选范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.DEPARTMENT_PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_002, "退领位置须为启用中的科室药房");
        }
    }

    /**
     * 断言供应请求行均为科室退领类型。
     *
     * @param lines 按 busNo 查出的供应请求行
     */
    private void assertDeptIssueReturn(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_ISSUE_RETURN.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_103, "单据类型非科室退领单");
            }
        }
    }

    /**
     * 校验源领用单存在、类型/状态/库位合法。
     *
     * @param originalBusNo       源领用单号
     * @param purposeLocationId   期望科室药房（可为 null 表示仅校验类型/状态）
     */
    private void validateSourceDeptIssueForReturn(String originalBusNo, Long purposeLocationId) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> issueLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
        if (issueLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_103, "源领用单不存在");
        }
        boolean matchedPurposeLocation = false;
        for (SupplyRequestModel line : issueLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_ISSUE.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "来源单据类型不是科室领用单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "来源领用单未同意，不可引用");
            }
            if (purposeLocationId != null && Objects.equals(purposeLocationId, line.getPurposeLocationId())) {
                matchedPurposeLocation = true;
            }
        }
        if (purposeLocationId != null && !matchedPurposeLocation) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_002, "来源领用单与当前科室药房不匹配");
        }
    }

    /**
     * 按领用单号加载供应请求明细。
     *
     * @param issueBusNo 领用业务单号
     * @return 领用明细行
     */
    private List<SupplyRequestModel> loadIssueLinesByBusNo(String issueBusNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(issueBusNo);
        return this.supplyRequestQueryService.getSupplyByBusNo(q);
    }

    /**
     * 加载 {@code original_bus_no} 指向指定领用单的全部退领明细。
     *
     * @param issueBusNo 领用业务单号
     * @return 引用该领用单的退领明细
     */
    private List<SupplyRequestModel> loadReturnsReferencingIssue(String issueBusNo) {
        SupplyByOriginalBusNoQuery q = new SupplyByOriginalBusNoQuery();
        q.setOriginalBusNo(issueBusNo);
        return this.supplyRequestQueryService.getSupplyByOriginalBusNo(q);
    }

    /**
     * 校验草稿明细均来自领用行且数量不超过可退余量。
     *
     * @param originalBusNo      源领用单号
     * @param purposeLocationId  科室药房
     * @param draftLines         待保存退领行
     * @param excludeReturnBusNo 修订/同意复检时排除的本退领单号
     */
    private void assertDraftLinesBoundToIssueAndWithinReturnable(
        String originalBusNo,
        Long purposeLocationId,
        List<DeptIssueReturnLineHelper.DraftReturnLine> draftLines,
        String excludeReturnBusNo) {
        if (StringUtils.isEmpty(originalBusNo)) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "originalBusNo 不能为空");
        }
        this.validateSourceDeptIssueForReturn(originalBusNo.trim(), purposeLocationId);
        List<SupplyRequestModel> issueLines = this.loadIssueLinesByBusNo(originalBusNo.trim());
        List<SupplyRequestModel> returnsReferencingIssue = this.loadReturnsReferencingIssue(originalBusNo.trim());
        DeptIssueReturnLineHelper.assertDraftLinesWithinReturnable(
            issueLines,
            returnsReferencingIssue,
            draftLines,
            excludeReturnBusNo,
            ErrorCodeConstants.DEP_TL_001);
    }

    /**
     * 构建可关联领用单列表聚合行。
     *
     * @param issueBusNo          领用业务单号
     * @param pageLines           当前页供应请求明细
     * @param purposeLocationId   科室药房
     * @return 列表行；无可匹配明细时返回 null
     */
    private LinkableIssueListRowDto buildLinkableIssueListRow(
        String issueBusNo,
        List<SupplyRequestModel> pageLines,
        Long purposeLocationId) {
        List<SupplyRequestModel> issueLines = new ArrayList<>();
        for (SupplyRequestModel line : pageLines) {
            if (line == null || StringUtils.isEmpty(line.getBusNo())) {
                continue;
            }
            if (!issueBusNo.equals(line.getBusNo().trim())) {
                continue;
            }
            issueLines.add(line);
        }
        if (issueLines.isEmpty()) {
            return null;
        }
        List<SupplyRequestModel> returnsReferencingIssue = this.loadReturnsReferencingIssue(issueBusNo);
        Map<String, BigDecimal> returnableByKey = DeptIssueReturnLineHelper.computeReturnableQuantityByLineKey(
            issueLines, returnsReferencingIssue, null);
        boolean returnable = DeptIssueReturnLineHelper.issueHasAnyReturnableLine(returnableByKey);
        SupplyRequestModel head = issueLines.get(0);
        LinkableIssueListRowDto row = new LinkableIssueListRowDto();
        row.setBusNo(issueBusNo);
        row.setPurposeLocationId(head.getPurposeLocationId());
        row.setApprovalTime(head.getApprovalTime());
        row.setReturnable(Boolean.valueOf(returnable));
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (SupplyRequestModel line : issueLines) {
            if (line != null && line.getTotalPrice() != null) {
                totalAmount = totalAmount.add(line.getTotalPrice());
            }
        }
        row.setTotalAmount(totalAmount);
        if (head.getPurposeLocationId() != null) {
            Optional<Location> locOpt = locationQueryService.findOptionalById(head.getPurposeLocationId());
            if (locOpt.isPresent() && locOpt.get().getOrganizationId() != null) {
                Map<Long, String> orgNames = this.organizationQueryService.findOrganizationNamesByIds(
                    List.of(locOpt.get().getOrganizationId()));
                row.setConsumingOrganizationName(orgNames.get(locOpt.get().getOrganizationId()));
            }
        }
        if (purposeLocationId != null && !purposeLocationId.equals(head.getPurposeLocationId())) {
            return null;
        }
        return row;
    }

    /**
     * 将 HTTP 明细 DTO 转为 support 草稿行。
     *
     * @param lines 请求明细
     * @return support 草稿行
     */
    private List<DeptIssueReturnLineHelper.DraftReturnLine> toDraftReturnLinesFromLineDtos(
        List<DeptIssueReturnLineDto> lines) {
        List<DeptIssueReturnLineHelper.DraftReturnLine> draftLines = new ArrayList<>();
        if (lines == null) {
            return draftLines;
        }
        for (DeptIssueReturnLineDto line : lines) {
            if (line == null) {
                continue;
            }
            draftLines.add(new DeptIssueReturnLineHelper.DraftReturnLine(
                line.getItemTable(),
                line.getItemId(),
                line.getItemQuantity(),
                line.getUnitCode(),
                line.getLotNumber(),
                line.getTraceNo()));
        }
        return draftLines;
    }

    /**
     * 将持久化退领明细转为 support 草稿行（同意前复检）。
     *
     * @param lines 供应请求明细
     * @return support 草稿行
     */
    private List<DeptIssueReturnLineHelper.DraftReturnLine> toDraftReturnLinesFromSupplyModels(
        List<SupplyRequestModel> lines) {
        List<DeptIssueReturnLineHelper.DraftReturnLine> draftLines = new ArrayList<>();
        if (lines == null) {
            return draftLines;
        }
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            draftLines.add(new DeptIssueReturnLineHelper.DraftReturnLine(
                line.getItemTable(),
                line.getItemId(),
                line.getItemQuantity(),
                line.getUnitCode(),
                line.getLotNumber(),
                line.getTraceNo()));
        }
        return draftLines;
    }

    /**
     * busNo 非空门禁。
     *
     * @param busNo 业务单号
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_001, "busNo 不能为空");
        }
    }

    /**
     * 读取表头供应状态枚举；单号不存在时返回 null。
     *
     * @param busNo 业务单号
     * @return 表头状态，不存在或空状态时为 null
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
     * 库存匹配用批号规范化（空批号统一为空串）。
     *
     * @param lotNumber 原始批号
     * @return 去空白后的批号或空串
     */
    private String resolveLotNumberForInventory(String lotNumber) {
        if (StringUtils.isEmpty(lotNumber)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        return lotNumber.trim();
    }

    /**
     * 解析追溯记录上的目的位置类型持久化 code。
     *
     * @param line 当前供应请求行
     * @param head 表头行
     * @return purpose 位置类型 code
     */
    private String resolvePurposeLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotEmpty(line.getPurposeTypeEnum())) {
            return line.getPurposeTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotEmpty(head.getPurposeTypeEnum())) {
            return head.getPurposeTypeEnum().trim();
        }
        return SupplyRequestType.PHARMACY.getCode();
    }

    /**
     * 从批量产品读模型解析行展示名称。
     *
     * @param line         供应请求行
     * @param productByKey 批量产品读模型映射
     * @return 产品名称
     */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isEmpty(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "产品定义缺失，无法解析展示名称");
        }
        return pr.getProductName().trim();
    }

    /**
     * 库存动账拆零单位：优先行单位，否则取产品定义拆零单位。
     *
     * @param line    供应请求行
     * @param product 产品读模型
     * @return 拆零单位 code
     */
    private String resolveSplitUnitCode(SupplyRequestModel line, ProductDefinitionDisplayDto product) {
        if (product == null || StringUtils.isEmpty(product.getSplitUnitCode())) {
            throw new BusinessException(ErrorCodeConstants.DEP_TL_104, "产品定义缺失或缺少拆零单位编码");
        }
        if (line != null && StringUtils.isNotEmpty(line.getUnitCode())) {
            return line.getUnitCode().trim();
        }
        return product.getSplitUnitCode().trim();
    }

    /**
     * 将领域 SUPPLY_REQUEST_INVALID_ARGUMENT 映射为科室退领业务错误码。
     *
     * @param ex          领域工作流异常
     * @param defaultCode 映射目标业务错误码
     * @return 映射后的业务异常
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * 同意编排异常映射：重复同意 → DEP_TL_104，其余参数类 → DEP_TL_003。
     *
     * @param ex 领域同意流程异常
     * @return 映射后的业务异常
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.DEP_TL_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_TL_003, msg);
        }
        return ex;
    }

    /**
     * 建单/改单库位类领域异常映射为 DEP_TL_002 或 DEP_TL_001。
     *
     * @param ex 领域建单/改单异常
     * @return 映射后的业务异常
     */
    private BusinessException mapDeptIssueReturnLocationException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("库房") || msg.contains("位置") || msg.contains("药房") || msg.contains("科室"))) {
                return new BusinessException(ErrorCodeConstants.DEP_TL_002, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_TL_001, msg);
        }
        return ex;
    }
}
