/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.deptissue.service;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueDetailLineDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueLineDto;
import com.openhis.health.application.inventory.department.deptissue.dto.DeptIssueListRowDto;
import com.openhis.health.application.inventory.department.deptissue.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.deptissue.mapper.InventoryDepartmentDeptIssueMapper;
import com.openhis.health.application.inventory.department.deptissue.request.*;
import com.openhis.health.application.inventory.department.deptissue.response.*;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.application.shared.tracenumber.TraceNumberSupplyAgreeTraceOrchestrator;
import com.openhis.health.application.shared.tracenumber.RecordTraceFromSupplyLineSpec;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryDepartmentConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.OrganizationType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
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
import com.openhis.health.infrastructure.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.infrastructure.integration.insurance.inventory.ExtensionIn.InventoryUploadAfterDeptIssueExtensionIn;
import com.openhis.whale.core.application.dto.OptionDto;
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
 * 科室领用单（{@link SupplyType#DEPT_ISSUE}，DM-WKF-006）应用编排（IF-DEP-IS · AS-INV-DEP-004）。
 *
 * <p>单端点科室药房扣减：建单/改单 → 提交 → 管辖库位 agree → {@code createSupplyDelivery} + 库存扣减。
 * 库位须为 {@link LocationForm#DEPARTMENT_PHARMACY}；{@code source_*} 恒 NULL。
 * 领用科室 {@code consumingOrganizationId} 由前端手选行政科室落库，与科室药房解耦。</p>
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link SupplyRequestDomainService} / {@link SupplyRequestQueryService}：领用单写模型与列表/详情/可编辑态门禁</li>
 *   <li>{@link SupplyDeliveryDomainService} / {@link SupplyDeliveryQueryService}：同意发放与幂等门禁</li>
 *   <li>{@link InventoryItemDomainService} / {@link InventoryItemQueryService}：同意扣减与选品可用量</li>
 *   <li>{@link LocationQueryService} / {@link OrganizationQueryService}：科室药房与上级科室只读 enrich</li>
 *   <li>{@link PractitionerRoleScopeLoader}：init 药师下拉（agree 不按岗位门禁）</li>
 *   <li>{@link CurrentManagedLocationIdsResolver}：建单/同意等写路径科室药房管辖 Gate</li>
 *   <li>{@link TraceNumberSupplyAgreeTraceOrchestrator}：条件追溯写出库轨迹</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/inventoryDepartmentDeptIssue/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentDeptIssueAppService {

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
    /** 库存项写模型（同意扣减）。 */
    private final InventoryItemDomainService inventoryItemDomainService;
    /** 库存项读模型（选品/可用量/匹配键）。 */
    private final InventoryItemQueryService inventoryItemQueryService;
    private final InventoryItemProductSelectionPageLoader inventoryItemProductSelectionPageLoader;
    /** 库位读模型（科室药房门控与展示名）。 */
    private final LocationQueryService locationQueryService;
    /** 组织读模型（领用科室展示名）。 */
    private final OrganizationQueryService organizationQueryService;
    /** 医务人员读模型（列表制单/审批人 enrich）。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 岗位范围加载（init 药师下拉）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    /** 管辖库位 Gate（列表/建单/选品/同意）。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    /** 产品定义批量读（详情/选品 enrich）。 */
    /** 选品术语与单位展示名。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** HTTP 契约与领域命令装配。 */
    private final InventoryDepartmentDeptIssueMapper deptIssueMapper;
    /** 追溯码写出（同意条件分支）。 */
    private final TraceNumberSupplyAgreeTraceOrchestrator traceNumberSupplyAgreeTraceOrchestrator;
    /** 选品当前价读（新开/选品场景，非历史单据槽价）。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 医保进销存第三方上报扩展点。 */
    private final InventoryThirdPartyUploadExtension inventoryUploadExtension;

    /**
     * 加载页主数据（科室药房含上级科室、人员、状态等下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 表单初始化选项集合
     */
    public LoadDeptIssueFormInitResponse loadDeptIssueFormInit(LoadDeptIssueFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位与人员
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEP_IS_101, "机构上下文缺失");
        try {
            // Pre：拉取启用库位/人员并经管辖 Gate 收窄
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> gatedDepartmentPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            // Asm：下拉选项写入初始化响应
            return deptIssueMapper.toLoadDeptIssueFormInitResponse(
                deptIssueMapper.toDeptPharmacyOptionDtoListFromLocations(gatedDepartmentPharmacies),
                this.buildActiveDepartmentOrganizationOptions(),
                deptIssueMapper.toOptionDtoListProductTableTypes(),
                deptIssueMapper.toOptionDtoListSupplyStatuses(),
                deptIssueMapper.toOptionDtoListFromPractitioners(pharmacists));
        } catch (RuntimeException ex) {
            log.warn("loadDeptIssueFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_IS_101, "领用页主数据加载失败");
        }
    }

    /**
     * 分页查询单据列表。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     */
    public PageDeptIssuesResponse pageDeptIssues(PageDeptIssuesRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_IS_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_IS_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        // Pre：组装供应单据列表查询条件
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_ISSUE.getCode());
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
                throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
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
            // Cmd：按 busNo 去重分页查询供应请求行
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<DeptIssueListRowDto> records = this.deptIssueMapper.toDeptIssueListAggregated(busNos, lines);
            this.enrichDeptIssueListPurposeLocationNames(records);
            this.enrichDeptIssueListOrganizationNames(records);
            this.enrichDeptIssueListStatusEnumText(records);
            this.enrichDeptIssueListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合行映射为分页响应
            return this.deptIssueMapper.toPageDeptIssuesAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageDeptIssues failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_IS_102, "领用单列表查询失败");
        }
    }

    /**
     * 按业务单号查询单据详情。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     */
    public GetDeptIssueDetailResponse getDeptIssueDetail(GetDeptIssueDetailRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "busNo 不能为空");
        }
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_103, "领用单不存在");
        }
        this.assertDeptIssue(lines);
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
            log.warn("batchGetProductByIds degraded for dept issue detail", ex);
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
        GetDeptIssueDetailResponse res = deptIssueMapper.toGetDeptIssueDetailResponse(lines, hints);
        SupplyRequestModel head = lines.get(0);
        if (head.getPurposeLocationId() != null) {
            Optional<Location> locOpt = locationQueryService.findOptionalById(head.getPurposeLocationId());
            if (locOpt.isPresent() && StringUtils.isNotEmpty(locOpt.get().getName())) {
                res.setPurposeLocationName(locOpt.get().getName().trim());
            }
        }
        this.enrichDeptIssueDetailConsumingOrganizationName(res);
        this.enrichDeptIssueDetailProductDisplay(res.getLines(), products, terminologyMaps);
        // Asm：行上 end_time 为空时，按科室药房+品批从库存补齐有效期（选品/历史单回显）
        this.enrichDeptIssueDetailLineExpiryFromInventory(head.getPurposeLocationId(), res.getLines());
        // Asm：槽价按落库行价 enrich（BR-PRICE-SRC-001 历史业务，禁止覆盖为当前目录价）
        this.enrichDeptIssueDetailLinesCatalogPricing(res.getLines());
        return res;
    }

    /**
     * 新建单据（草稿/待提交）。
     *
     * @param request 含库位、明细行等业务字段
     * @return 新建业务单号
     */
    public CreateDeptIssueResponse createDeptIssue(CreateDeptIssueRequest request) {
        // Gate：请求体、库位与明细不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "purposeLocationId 不能为空");
        }
        if (request.getConsumingOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "consumingOrganizationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_IS_101, "机构上下文缺失");
        this.assertDeptIssuePurposeLocationGate(request.getPurposeLocationId());
        this.assertConsumingOrganizationGate(request.getConsumingOrganizationId());
        this.assertDeptIssueLinesFields(request.getLines());
        this.assertDeptIssueDraftLinesWithinPurposeAvailableInventory(request.getPurposeLocationId(), request.getLines());
        // Pre：写行未带 endTime 时从库存批号效期回填，保证详情「有效期」可回显
        this.fillDeptIssueLineEndTimeFromInventory(request.getPurposeLocationId(), request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_IS_101, "用户未登录");
        Long applicantId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_IS_101, "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：领域建单并分配 busNo
            String busNo = supplyRequestPricedWriteOrchestrator.createDeptIssue(
                deptIssueMapper.toCreateDeptIssueCommand(request, operatorId, applicantId, null));
            // Asm：返回新建 busNo
            return deptIssueMapper.toCreateDeptIssueResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            throw this.mapDeptIssueLocationException(ex);
        }
    }

    /**
     * 更新可编辑态科室领用单（全量明细覆盖，同号软删重插）。
     *
     * @param request 含 busNo、明细与库位
     * @return 更新后的 busNo（与请求相同）
     */
    public UpdateDeptIssueResponse updateDeptIssue(UpdateDeptIssueRequest request) {
        // Gate：busNo、明细与库位不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "purposeLocationId 不能为空");
        }
        if (request.getConsumingOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "consumingOrganizationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_IS_101, "机构上下文缺失");
        this.assertDeptIssuePurposeLocationGate(request.getPurposeLocationId());
        this.assertConsumingOrganizationGate(request.getConsumingOrganizationId());
        // Pre：回读存量并校验工作流可编辑态
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_103, "领用单不存在");
        }
        this.assertDeptIssue(existing);
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_004, "当前状态不允许编辑领用单");
        }
        this.assertDeptIssueLinesFields(request.getLines());
        this.assertDeptIssueDraftLinesWithinPurposeAvailableInventory(request.getPurposeLocationId(), request.getLines());
        // Pre：写行未带 endTime 时从库存批号效期回填
        this.fillDeptIssueLineEndTimeFromInventory(request.getPurposeLocationId(), request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_IS_101, "用户未登录");
        Long applicantId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_IS_101, "当前用户未绑定医务人员档案，无法制单");
        try {
            // Cmd：同号全量对齐改单（领域软删重插）
            String busNo = supplyRequestPricedWriteOrchestrator.createDeptIssue(
                deptIssueMapper.toCreateDeptIssueCommand(request, operatorId, applicantId));
            // Asm：返回 busNo
            return deptIssueMapper.toUpdateDeptIssueResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_105, "领用单已被并发修改，请刷新后重试", ex);
        } catch (BusinessException ex) {
            throw this.mapDeptIssueLocationException(ex);
        }
    }

    /**
     * 提交单据审批。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public SubmitDeptIssueApprovalResponse submitDeptIssueApproval(SubmitDeptIssueApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_IS_101, "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批，状态迁移至待审核
            supplyRequestDomainService.submitApproval(
                deptIssueMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_IS_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_IS_003);
        }
        // Asm：回读提交后状态写入响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return deptIssueMapper.toSubmitDeptIssueApprovalResponse(st);
    }

    /**
     * 撤回单据审批（回到可编辑态）。
     *
     * @param request 须含 busNo
     * @return 空成功体
     */
    public WithdrawDeptIssueApprovalResponse withdrawDeptIssueApproval(WithdrawDeptIssueApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批，单据回到可编辑态
            supplyRequestDomainService.withdrawApproval(
                deptIssueMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_IS_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_IS_003);
        }
        // Asm：回读撤回后状态写入响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return deptIssueMapper.toWithdrawDeptIssueApprovalResponse(st);
    }

    /**
     * 科室领用单同意编排：领域落已同意态 → 领用发放 → 科室药房扣减 → 条件追溯。
     *
     * <p>须管辖单据科室药房（不按药师岗位）；发放命令为 {@code createSupplyDelivery}。重复 agree、发放已完成、库存不足等场景抛
     * {@link ErrorCodeConstants#DEP_IS_104}。</p>
     *
     * @param request 含 busNo；须未重复同意
     * @return 同意后 busNo 与状态 code
     */
    public AgreeDeptIssueResponse agreeDeptIssue(AgreeDeptIssueRequest request) {
        // Gate：busNo、机构上下文与操作人
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "busNo 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEP_IS_101, "机构上下文缺失");
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_IS_101, "用户未登录");

        // Pre：回读领用单并校验未重复同意
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_103, "领用单不存在");
        }
        this.assertDeptIssue(beforeLines);
        // Gate：同意仅校验管辖库位，能管该科室药房即可审批
        this.assertDeptIssuePurposeLocationGate(beforeLines.get(0).getPurposeLocationId());
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "领用单已同意，禁止重复同意");
        }

        // Cmd：领域状态机同意领用单
        try {
            supplyRequestDomainService.agreeRequest(deptIssueMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_105, "领用单已被并发修改，请刷新后重试", ex);
        }

        // Pre：强制读回 AGREE 行，保证发放/库存编排输入合法
        this.supplyRequestDomainService.flushPendingChanges();
        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertDeptIssue(agreedLines);
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "同意后回读状态异常，中止领用编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "领用明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "领用明细数量无效，无法完成编排");
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
            throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "供应请求行已存在完成态发放，禁止重复同意领用");
        }

        // Cmd：领用发放落库
        try {
            supplyDeliveryDomainService.createSupplyDelivery(
                deptIssueMapper.toCreateSupplyDeliveryCommand(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "领用发放落库失败：" + ex.getMessage());
        }

        // Pre：批量产品读模型，供追溯写编排单位换算
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
                log.warn("batchGetProductByIds failed during dept issue inventory assemble", ex);
            }
        }

        // Cmd：逐行在 purpose 科室药房扣减库存（allowNegative=false）
        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        List<RecordTraceFromSupplyLineSpec> traceCommands = new ArrayList<>();

        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.DEP_IS_104, "供应请求行 item_quantity 须为拆零正数量");
            String resolvedLot = this.resolveLotNumberForDeptIssueInventory(line.getLotNumber());
            InventoryMatchKey matchKey = new InventoryMatchKey();
            matchKey.setSupplierId(line.getSupplierId());
            matchKey.setLotNumber(resolvedLot);
            matchKey.setLocationId(purposeLocationId);
            matchKey.setItemTable(line.getItemTable());
            matchKey.setItemId(line.getItemId());
            Long inventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(matchKey).orElse(null);
            if (inventoryId == null) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "科室药房未找到匹配库存行，无法扣减");
            }
            try {
                inventoryItemDomainService.decreaseQuantity(
                    deptIssueMapper.toDecreaseQuantityCommand(inventoryId, splitQty, false, operatorId));
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_104, "领用库存扣减失败：" + ex.getMessage());
            }

            String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            String splitUnitCode = line.getUnitCode();
            if (product != null && StringUtils.isNotEmpty(product.getSplitUnitCode())) {
                splitUnitCode = product.getSplitUnitCode().trim();
            }

            if (StringUtils.isNotEmpty(line.getTraceNo())) {
                String lt = this.resolvePurposeLocationTypeCode(line, head);
                RecordTraceFromSupplyLineSpec traceCmd = new RecordTraceFromSupplyLineSpec();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setSupplyTypeCode(SupplyType.DEPT_ISSUE.getCode());
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
                traceCmd.setRecordDirectionEnum(com.openhis.health.domain.share.enums.TraceDirection.OUT.getCode());
                traceCommands.add(traceCmd);
            }
        }

        if (!traceCommands.isEmpty()) {
            try {
                // 消耗侧可选扫：盖不住由 orchestrator 跳过写追溯，同意主流程不因扫码失败回滚
                traceNumberSupplyAgreeTraceOrchestrator.recordSingleEndLines(traceCommands);
            } catch (BusinessException ex) {
                log.warn("追溯码领用记录跳过（不挡同意）：{}", ex.getMessage());
            }
        }

        tryUploadMedicareInventoryAfterDeptIssueAgree(request, agreedLines);

        // Asm：映射同意响应
        return deptIssueMapper.toAgreeDeptIssueResponse(
            busNoTrim, SupplyStatus.AGREE.getCode());
    }

    /**
     * 领用 agree 后尝试上报医保 3502 其他出库（集成层降级，失败不阻断主流程）。
     *
     * @param request     agree 请求
     * @param agreedLines 已同意领用明细行
     */
    private void tryUploadMedicareInventoryAfterDeptIssueAgree(
        AgreeDeptIssueRequest request,
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
            InventoryUploadAfterDeptIssueExtensionIn command = new InventoryUploadAfterDeptIssueExtensionIn()
                .setDeptIssueBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setContractBusNo(request.getContractBusNo());
            inventoryUploadExtension.uploadAfterDeptIssueAgree(command);
        });
    }

    /**
     * 驳回单据。
     *
     * @param request 须含 busNo 与驳回原因
     * @return 驳回成功标志
     */
    public RejectDeptIssueResponse rejectDeptIssue(RejectDeptIssueRequest request) {
        // Gate：busNo 与驳回原因不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getChangeReason()) && StringUtils.isEmpty(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "驳回原因不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_IS_101, "用户未登录");
        try {
            // Cmd：领域驳回，须携带驳回原因
            supplyRequestDomainService.rejectRequest(deptIssueMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_IS_003);
        }
        // Asm：返回驳回成功标志
        return deptIssueMapper.toRejectDeptIssueResponse(true);
    }

    /**
     * 删除单据（仅草稿/可删态）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     */
    public RemoveDeptIssueResponse removeDeptIssue(RemoveDeptIssueRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_IS_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删（仅可删态）
            supplyRequestDomainService.removeByBusNo(deptIssueMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_IS_005);
        }
        // Asm：返回删除成功标志
        return deptIssueMapper.toRemoveDeptIssueResponse(true);
    }

    /**
     * 分页查询选品（采购目录 + 可选库存）。
     *
     * @param request 分页窗口与选品筛选条件
     * @return 产品定义展示行分页
     */
    public PageProductsForDeptIssueResponse pageProductsForDeptIssue(PageProductsForDeptIssueRequest request) {
        // Gate：分页窗口、机构与药房门禁
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_IS_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_IS_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForDeptIssueSelection(request.getProductTableName());
        // Pre：组装库存目录分页查询
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getPurposeLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        // —— 前台可选：true 仅返回选品库位账面量>0 的批号行；false/未传不过滤数量。——
        invQuery.setPositiveQuantityOnly(request.getPositiveQuantityOnly());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            // Cmd：分页查询现库库存行
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
                    log.warn("batchGetProductByIds failed for dept issue selection", ex);
                }
            }
            String itemTable = request.getProductTableName().trim();
            List<Long> pageItemIds = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv != null && inv.getItemId() != null) {
                    pageItemIds.add(inv.getItemId());
                }
            }
            Map<Long, Map<String, BigDecimal>> availableQtyByItemIdAndNormLot = new HashMap<>();
            if (!pageItemIds.isEmpty()) {
                List<InventoryCatalogAvailabilityRowModel> availRows =
                    this.inventoryItemQueryService.findAvailableCatalogRowsForOrder(
                        itemTable, pageItemIds, List.of(request.getPurposeLocationId()));
                for (InventoryCatalogAvailabilityRowModel ar : availRows) {
                    if (ar == null || ar.getItemId() == null || ar.getQuantity() == null) {
                        continue;
                    }
                    if (!Objects.equals(ar.getLocationId(), request.getPurposeLocationId())) {
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
                ProductDefinitionItemDto dto = deptIssueMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
                if (inv.getItemId() != null) {
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
            // Asm：映射选品分页并增强可用量与价带
            return PagedResultBuilder.build(new PageProductsForDeptIssueResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForDeptIssue failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_IS_102, "领用选品查询失败");
        }
    }

    /**
     * 列表行回填科室药房展示名。
     *
     * @param rows 列表聚合行
     */
    private void enrichDeptIssueListPurposeLocationNames(List<DeptIssueListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> purposeIds = new HashSet<>();
        for (DeptIssueListRowDto row : rows) {
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
        for (DeptIssueListRowDto row : rows) {
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
    private void enrichDeptIssueListOrganizationNames(List<DeptIssueListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        Set<Long> orgIds = new HashSet<>();
        for (DeptIssueListRowDto row : rows) {
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
        for (DeptIssueListRowDto row : rows) {
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
    private void enrichDeptIssueListStatusEnumText(List<DeptIssueListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (DeptIssueListRowDto row : rows) {
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
    private void enrichDeptIssueListDisplayNames(List<DeptIssueListRowDto> rows, List<SupplyRequestModel> detailLines) {
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
        for (DeptIssueListRowDto row : rows) {
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
    private void enrichDeptIssueDetailProductDisplay(
        List<DeptIssueDetailLineDto> lines,
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
        for (DeptIssueDetailLineDto dto : lines) {
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
     * 详情行 {@code endTime} 为空时，按科室药房+品批从库存项补齐有效期（只读 enrich，不写库）。
     *
     * @param purposeLocationId 科室药房库位
     * @param lines             详情明细
     */
    private void enrichDeptIssueDetailLineExpiryFromInventory(
        Long purposeLocationId,
        List<DeptIssueDetailLineDto> lines) {
        if (purposeLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        for (DeptIssueDetailLineDto dto : lines) {
            if (dto == null || dto.getEndTime() != null) {
                continue;
            }
            if (StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            LocalDateTime expiry = this.resolveInventoryExpirationDate(
                purposeLocationId, dto.getItemTable().trim(), dto.getItemId(), dto.getLotNumber());
            if (expiry != null) {
                dto.setEndTime(expiry);
            }
        }
    }

    /**
     * 写模型行未带 {@code endTime} 时，从库存批号效期回填后再落库。
     *
     * @param purposeLocationId 科室药房库位
     * @param lines             写模型明细
     */
    private void fillDeptIssueLineEndTimeFromInventory(Long purposeLocationId, List<DeptIssueLineDto> lines) {
        if (purposeLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        for (DeptIssueLineDto line : lines) {
            if (line == null || line.getEndTime() != null) {
                continue;
            }
            if (StringUtils.isEmpty(line.getItemTable()) || line.getItemId() == null) {
                continue;
            }
            LocalDateTime expiry = this.resolveInventoryExpirationDate(
                purposeLocationId, line.getItemTable().trim(), line.getItemId(), line.getLotNumber());
            if (expiry != null) {
                line.setEndTime(expiry);
            }
        }
    }

    /**
     * 解析库存项批号效期（科室药房 + 品 + 批号）。
     *
     * @param locationId 库位
     * @param itemTable  物品表
     * @param itemId     物品定义主键
     * @param lotNumber  批号，可空
     * @return 效期；未找到或库存未维护时 null
     */
    private LocalDateTime resolveInventoryExpirationDate(
        Long locationId,
        String itemTable,
        Long itemId,
        String lotNumber) {
        InventoryItemQuery query = new InventoryItemQuery();
        query.setLocationId(locationId);
        query.setItemTable(itemTable);
        query.setItemId(itemId);
        if (StringUtils.isNotEmpty(lotNumber)) {
            query.setLotNumber(lotNumber.trim());
        }
        List<InventoryItemModel> rows = this.inventoryItemQueryService.findInventoryItemByItemInfo(query);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (InventoryItemModel row : rows) {
            if (row != null && row.getExpirationDate() != null) {
                return row.getExpirationDate();
            }
        }
        return null;
    }

    /**
     * 详情明细目录价带与持久化行价对齐（BR-PRICE-SRC-001：仅用 supplyLine.price）。
     *
     * @param lines 详情明细行
     */
    private void enrichDeptIssueDetailLinesCatalogPricing(List<DeptIssueDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (DeptIssueDetailLineDto dto : lines) {
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
     * 校验领用科室药房在管辖 Gate 内且为启用中科室药房形态。
     *
     * @param purposeLocationId 科室药房位置 id
     */
    private void assertDeptIssuePurposeLocationGate(Long purposeLocationId) {
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(purposeLocationId, allowedDepartmentPharmacies);
    }

    /**
     * 校验手选领用科室：须存在、启用且类型为行政科室（{@link OrganizationType#DEPARTMENT}）。
     *
     * @param consumingOrganizationId 领用科室 Organization 主键
     */
    private void assertConsumingOrganizationGate(Long consumingOrganizationId) {
        if (consumingOrganizationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "consumingOrganizationId 不能为空");
        }
        Organization organization = this.organizationQueryService.loadOrganizationByIdForDisplay(consumingOrganizationId);
        if (organization == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_002, "领用科室不存在");
        }
        if (!OrganizationType.DEPARTMENT.getCode().equals(organization.getTypeEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_002, "领用科室须为行政科室");
        }
        if (!PublicationStatus.ACTIVE.getCode().equals(organization.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_002, "领用科室未启用");
        }
        if (!Boolean.TRUE.equals(organization.getActiveFlag())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_002, "领用科室未启用");
        }
    }

    /**
     * 装配启用行政科室下拉（{@link OrganizationType#DEPARTMENT} + {@link PublicationStatus#ACTIVE}）。
     *
     * @return 行政科室 OptionDto 列表
     */
    private List<OptionDto> buildActiveDepartmentOrganizationOptions() {
        List<OptionDto> options = new ArrayList<>();
        for (Organization organization : this.organizationQueryService.getOrganizationTree()) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            if (!OrganizationType.DEPARTMENT.getCode().equals(organization.getTypeEnum())) {
                continue;
            }
            Long id = organization.getId();
            String label = organization.getName();
            if (StringUtils.isEmpty(label)) {
                label = String.valueOf(id);
            }
            options.add(new OptionDto(id, String.valueOf(id), label));
        }
        return options;
    }

    /**
     * 详情响应回填领用科室展示名（优先使用 mapper 已解析的落库/回落 id，勿覆盖 consumingOrganizationId）。
     *
     * @param response 领用单详情响应
     */
    private void enrichDeptIssueDetailConsumingOrganizationName(GetDeptIssueDetailResponse response) {
        if (response == null || StringUtils.isEmpty(response.getConsumingOrganizationId())) {
            return;
        }
        try {
            Long orgId = Long.valueOf(response.getConsumingOrganizationId().trim());
            Map<Long, String> orgNames = this.organizationQueryService.findOrganizationNamesByIds(List.of(orgId));
            response.setConsumingOrganizationName(orgNames.get(orgId));
        } catch (NumberFormatException ignored) {
            // 非法 id 跳过名称 enrich
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
            locationId, allowed, ErrorCodeConstants.DEP_IS_002, "科室药房不在当前用户可选范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_002, "科室药房不在当前用户可选范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.DEPARTMENT_PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_002, "领用位置须为启用中的科室药房");
        }
    }

    /**
     * 校验领用明细必填字段与物品表白名单。
     *
     * @param lines 草稿或保存明细
     */
    private void assertDeptIssueLinesFields(List<DeptIssueLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (DeptIssueLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用明细单位不能为空");
            }
            if (line.getItemQuantity() == null || line.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用数量须为正数");
            }
            String table = line.getItemTable().trim();
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
                && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "物品表非法");
            }
        }
    }

    /**
     * 草稿行可用量校验：在 purpose 科室药房按物品 + 归一化批号聚合后与可用量比。
     *
     * @param purposeLocationId 科室药房
     * @param lines             草稿明细
     */
    private void assertDeptIssueDraftLinesWithinPurposeAvailableInventory(Long purposeLocationId, List<DeptIssueLineDto> lines) {
        if (purposeLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(purposeLocationId);
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (DeptIssueLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用明细须包含有效物品表与主键");
            }
            if (StringUtils.isEmpty(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "领用明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.DEP_IS_001, "领用明细数量须为正数");
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
                if (!Objects.equals(row.getLocationId(), purposeLocationId)) {
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
                            ErrorCodeConstants.DEP_IS_001,
                            "科室药房该批号可用量不足，请减少领用数量或刷新库存后再试");
                    }
                }
            }
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.DEP_IS_001,
                        "科室药房可用量不足，请减少领用数量或刷新库存后再试");
                }
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
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "busNo 不能为空");
        }
    }

    /**
     * 断言供应请求行均为科室领用类型。
     *
     * @param lines 按 busNo 查出的供应请求行
     */
    private void assertDeptIssue(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_ISSUE.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_IS_103, "单据类型非科室领用单");
            }
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
     * 选品物品表须为药品或器械定义表。
     *
     * @param raw 请求中的 productTableName
     */
    private void assertProductTableNameWhitelistForDeptIssueSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.DEP_IS_001, "productTableName 非法");
        }
    }

    /**
     * 库存匹配用批号规范化（空批号统一为空串）。
     *
     * @param lotNumber 原始批号
     * @return 去空白后的批号或空串
     */
    private String resolveLotNumberForDeptIssueInventory(String lotNumber) {
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
     * 将领域 SUPPLY_REQUEST_INVALID_ARGUMENT 映射为科室领用业务错误码。
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
     * 同意编排异常映射：重复同意 → DEP_IS_104，其余参数类 → DEP_IS_003。
     *
     * @param ex 领域同意流程异常
     * @return 映射后的业务异常
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.DEP_IS_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_IS_003, msg);
        }
        return ex;
    }

    /**
     * 建单/改单库位类领域异常映射为 DEP_IS_002 或 DEP_IS_001。
     *
     * @param ex 领域建单/改单异常
     * @return 映射后的业务异常
     */
    private BusinessException mapDeptIssueLocationException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("库房") || msg.contains("位置") || msg.contains("药房") || msg.contains("科室"))) {
                return new BusinessException(ErrorCodeConstants.DEP_IS_002, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_IS_001, msg);
        }
        return ex;
    }
}
