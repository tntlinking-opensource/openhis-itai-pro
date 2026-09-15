/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.purchaserequest.service;

import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestDetailLineDto;
import com.openhis.health.application.inventory.department.purchaserequest.dto.DeptPurchaseRequestListRowDto;
import com.openhis.health.application.inventory.department.purchaserequest.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.department.purchaserequest.mapper.InventoryDepartmentPurchaseRequestMapper;
import com.openhis.health.application.inventory.department.purchaserequest.request.AgreeDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.CreateDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.GetDeptPurchaseRequestDetailRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.LoadDeptPurchaseRequestFormInitRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.PageDeptPurchaseRequestsRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.PageProductsForDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.RejectDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.RemoveDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.SubmitDeptPurchaseRequestApprovalRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.UpdateDeptPurchaseRequestRequest;
import com.openhis.health.application.inventory.department.purchaserequest.request.WithdrawDeptPurchaseRequestApprovalRequest;
import com.openhis.health.application.inventory.department.purchaserequest.response.AgreeDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.CreateDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.GetDeptPurchaseRequestDetailResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.LoadDeptPurchaseRequestFormInitResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.PageDeptPurchaseRequestsResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.PageProductsForDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.RejectDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.RemoveDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.SubmitDeptPurchaseRequestApprovalResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.UpdateDeptPurchaseRequestResponse;
import com.openhis.health.application.inventory.department.purchaserequest.response.WithdrawDeptPurchaseRequestApprovalResponse;
import com.openhis.health.application.inventory.warehouse.support.ProductDefinitionPurchasePageLoader;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionPageQuery;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryDepartmentConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateDeptPurchaseRequestCommand;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 科室采购申请单（{@link SupplyType#DEPT_PURCHASE_REQUEST}）应用编排（AS-INV-DEP-007）。
 *
 * <p>职责边界：建单/修订/审批/删除与产品定义级选品；<strong>不</strong>调用 {@code createPurchaseOrder}、
 * InventoryItem 或 SupplyDelivery。同意仅 {@code agreeRequest}，不触发库存变动。</p>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/inventoryDepartmentPurchaseRequest/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentPurchaseRequestAppService {

    /** 机构管辖解析。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 操作人/制单人解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;
    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;
    /** 医务人员查询（列表制单人/审批人 enrich）。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 平台角色收窄医务人员（药师/库管经手人下拉）。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    /** 供应请求领域写服务。 */
    private final SupplyRequestDomainService supplyRequestDomainService;
    /** 供应请求读服务。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** 库位读服务。 */
    private final LocationQueryService locationQueryService;
    /** 管辖库位解析。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    /** 采购选品分页加载器。 */
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    /** 产品定义展示术语装配。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 收费项目定义查询（选品进价 enrich）。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    /** 科室采购申请装配器。 */
    private final InventoryDepartmentPurchaseRequestMapper purchaseRequestMapper;

    /**
     * 科室采购申请页初始化（IF-DEP-CPR-01）。
     *
     * @param request 可为空；机构上下文由服务端 Resolver 解析
     * @return 科室药房/状态/物品表下拉
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_101} 机构上下文缺失或主数据加载失败
     */
    public LoadDeptPurchaseRequestFormInitResponse loadDeptPurchaseRequestFormInit(LoadDeptPurchaseRequestFormInitRequest request) {
        // Gate：会话机构上下文须存在
        managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEP_CPR_101, "机构上下文缺失");
        try {
            // Pre：拉取启用科室药房并经管辖收窄；经手人取全体在职医务人员（不限药师/库管）
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> gatedDepartmentPharmacies =
                this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<OptionDto> deptOpts = purchaseRequestMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies);
            List<OptionDto> deptOptsWithAll = new ArrayList<>();
            // —— 列表筛「全部科室药房」：value=0 与 pageDeptPurchaseRequests.purposeLocationId 约定一致，非 Location 主键
            deptOptsWithAll.add(new OptionDto(0L, "0", "全部"));
            deptOptsWithAll.addAll(deptOpts);
            List<Practitioner> handlers =
                this.practitionerRoleScopeLoader.listAllActivePractitionersForInventoryDocHandlerSelect();
            // Asm：经手人写入 pharmacistOptions（前端与库管选项合并；库管位留空）
            return purchaseRequestMapper.toLoadDeptPurchaseRequestFormInitResponse(
                deptOptsWithAll,
                purchaseRequestMapper.toOptionDtoListSupplyStatuses(),
                purchaseRequestMapper.toOptionDtoListPurchaseItemTableTypes(),
                purchaseRequestMapper.toOptionDtoListFromPractitioners(handlers),
                List.of());
        } catch (RuntimeException ex) {
            // —— 主数据加载失败时收口为 DEP_CPR_101，避免裸异常透出
            log.warn("loadDeptPurchaseRequestFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_101, "科室采购申请页主数据加载失败");
        }
    }

    /**
     * 分页查询科室采购申请单列表（IF-DEP-CPR-02）。
     *
     * @param request 含库位筛选、状态、关键字与分页窗口
     * @return 分页列表结果
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} 参数非法；
     *                            {@link ErrorCodeConstants#DEP_CPR_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#DEP_CPR_102} 列表查询失败
     */
    public PageDeptPurchaseRequestsResponse pageDeptPurchaseRequests(PageDeptPurchaseRequestsRequest request) {
        // Gate：请求体、分页窗口、目的库位与机构上下文
        if (request == null) {
            // —— 列表查询须带筛选与分页窗口
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            // —— purposeLocationId 必填；0=全部可见科室药房
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_CPR_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_CPR_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        // Pre：组装供应单据列表查询条件
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_PURCHASE_REQUEST.getCode());
        if (Long.valueOf(InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID)
            .equals(request.getPurposeLocationId())) {
            // —— 「全部科室药房」：展开为当前用户可见 id 集合；无可见库时用占位 id 避免空 IN 全表扫描
            if (allowedDepartmentPharmacies.isEmpty()) {
                // —— 管辖下无可选科室药房：占位 -1 使查询结果为空集
                listQuery.setPurposeLocationIds(
                    List.of(InventoryDepartmentConstants.LIST_FILTER_NO_VISIBLE_DEPARTMENT_PHARMACY_PLACEHOLDER_ID));
            } else {
                // —— 展开为可见科室药房 id 列表
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedDepartmentPharmacies));
            }
        } else {
            // —— 单库筛选：须落在管辖门控内，防横向窥探其它科室药房单据
            this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            // —— 状态须为 SupplyStatus 持久化 code（B.10.8.1），禁止排序号中转
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                // —— 非法状态码拒绝，避免下游把垃圾码当筛选条件
                throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        // —— 申请时刻窗：兼容 applyTimeFrom/To 与 AbstractPageQuery 的 startDate/endDate
        LocalDateTime rangeStart = request.getApplyTimeFrom();
        if (rangeStart == null) {
            // —— 未传 applyTimeFrom 时回落父类 startDate
            rangeStart = request.getStartDate();
        }
        listQuery.setStartDate(rangeStart);
        LocalDateTime rangeEnd = request.getApplyTimeTo();
        if (rangeEnd == null) {
            // —— 未传 applyTimeTo 时回落父类 endDate
            rangeEnd = request.getEndDate();
        }
        listQuery.setEndDate(rangeEnd);
        try {
            // Cmd：按 bus_no 去重分页查询供应请求行
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<DeptPurchaseRequestListRowDto> records =
                purchaseRequestMapper.toDeptPurchaseRequestListAggregated(busNos, lines);
            // —— 展示名依赖主数据，在应用层批量解析，避免 Mapper 注入 QueryService（B.6.1）
            this.enrichDeptPurchaseRequestListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合行映射为分页响应
            return purchaseRequestMapper.toPageDeptPurchaseRequestsAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            // —— 列表聚合查询失败时收口为 DEP_CPR_102
            log.warn("pageDeptPurchaseRequests failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_102, "科室采购申请单列表查询失败");
        }
    }

    /**
     * 按业务单号查询科室采购申请单详情（IF-DEP-CPR-03）。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} busNo 为空；
     *                            {@link ErrorCodeConstants#DEP_CPR_103} 单据不存在或类型不匹配
     */
    public GetDeptPurchaseRequestDetailResponse getDeptPurchaseRequestDetail(GetDeptPurchaseRequestDetailRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "busNo 不能为空");
        }
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_103, "科室采购申请单不存在");
        }
        // —— 类型守卫：防止借详情接口读取其它供应类型单据
        this.assertDeptPurchaseRequest(lines);
        // —— 按物品表+主键去重后批量拉产品定义，避免 N+1（与订货详情同构）
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
            // —— 产品读模型批量加载失败时降级为空映射，详情仍可展示供应请求持久化字段
            log.warn("batchGetProductByIds degraded for dept purchase request detail", ex);
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
        GetDeptPurchaseRequestDetailResponse res = purchaseRequestMapper.toGetDeptPurchaseRequestDetailResponse(lines, products);
        // —— 将行级 catalog 编辑提示写回详情 DTO，供前端多单位槽价编辑
        for (DeptPurchaseRequestDetailLineDto dto : res.getLines()) {
            if (dto == null || dto.getId() == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto hint = hints.get(dto.getId());
            if (hint != null) {
                dto.setProductCatalog(hint);
            }
        }
        // Asm：与 IF-DEP-CPR-03 同构，从产品读模型回填品名/规格/厂家与多单位目录提示
        this.enrichDeptPurchaseRequestDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichDeptPurchaseRequestDetailLines(res.getLines());
        return res;
    }

    /**
     * 新建科室采购申请单（IF-DEP-CPR-04）。
     *
     * @param request 含科室药房、制单时刻与非空明细
     * @return 新建业务单号
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} 参数或库位校验失败；
     *                            {@link ErrorCodeConstants#DEP_CPR_101} 机构或操作人上下文缺失
     */
    public CreateDeptPurchaseRequestResponse createDeptPurchaseRequest(CreateDeptPurchaseRequestRequest request) {
        // Gate：请求体、明细非空、目的科室药房与制单时刻不可缺（BR-DEP-CPR-001/002）
        if (request == null) {
            // —— 空请求体无法解析目的库与明细
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            // —— 申请单须至少一行产品定义级明细
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "采购申请明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            // —— 单端点目的科室药房为建单必填
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "目的科室药房不能为空");
        }
        if (request.getOccurrenceTime() == null) {
            // —— 头级制单时刻将写入各供应请求行 occurrence_time
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "制单时刻不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_CPR_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_CPR_101, "用户未登录");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_CPR_101, "当前用户未绑定医务人员档案，无法制单");
        try {
            // Pre：Mapper 内 requireSupplyRequestTypeForLocationId 推导 purposeTypeEnum（科室药房）
            CreateDeptPurchaseRequestCommand cmd =
                purchaseRequestMapper.toCreateDeptPurchaseRequestCommand(request, operatorId, applicantId);
            // Cmd：领域 createDeptPurchaseRequest 落供应写模型（定价在领域内处理）
            String busNo = supplyRequestDomainService.createDeptPurchaseRequest(cmd);
            // Asm：映射新建响应
            return purchaseRequestMapper.toCreateDeptPurchaseRequestResponse(busNo);
        } catch (IllegalArgumentException ex) {
            // —— Mapper/领域非法参数收口为应用层 DEP_CPR_001
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "科室采购申请参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 编辑科室采购申请单（IF-DEP-CPR-05）。
     *
     * @param request 含 busNo、rowVersion、制单时刻与非空明细
     * @return 业务单号
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} 参数非法；
     *                            {@link ErrorCodeConstants#DEP_CPR_004} 当前状态不可编辑；
     *                            {@link ErrorCodeConstants#DEP_CPR_105} 并发冲突
     */
    public UpdateDeptPurchaseRequestResponse updateDeptPurchaseRequest(UpdateDeptPurchaseRequestRequest request) {
        // Gate：busNo、rowVersion、明细、制单时刻与库位不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            // —— 修订必须指定既有业务单号
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            // —— 修订为全量替换，明细不可空
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "采购申请明细不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            // —— 目的科室药房不可缺（且后续校验须与存量一致）
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "目的科室药房不能为空");
        }
        if (request.getOccurrenceTime() == null) {
            // —— 头级制单时刻仍须带回并写入各行
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "制单时刻不能为空");
        }
        if (request.getRowVersion() == null) {
            // —— 乐观锁版本必填，冲突时 DEP_CPR_105
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "rowVersion 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_CPR_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_103, "科室采购申请单不存在");
        }
        this.assertDeptPurchaseRequest(existing);
        SupplyRequestModel head = existing.get(0);
        // —— 目的科室药房为单据头关键字段，修订不允许改库
        if (!Objects.equals(head.getPurposeLocationId(), request.getPurposeLocationId())) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "目的科室药房不可修改");
        }
        // Pre：读侧 verifyAbleEdit——已提交/已同意等态禁止覆盖明细
        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_004, "当前状态不允许编辑科室采购申请单");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_CPR_101, "用户未登录");
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_CPR_101, "当前用户未绑定医务人员档案，无法制单");
        try {
            // Pre：Mapper 内 requireSupplyRequestTypeForLocationId 推导 purposeTypeEnum（科室药房）
            CreateDeptPurchaseRequestCommand cmd =
                purchaseRequestMapper.toCreateDeptPurchaseRequestCommandForUpdate(request, operatorId, applicantId);
            // Cmd：领域 createDeptPurchaseRequest 同号修订
            String busNo = supplyRequestDomainService.createDeptPurchaseRequest(cmd);
            // Asm：映射修订响应
            return purchaseRequestMapper.toUpdateDeptPurchaseRequestResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "科室采购申请参数不合法：" + ex.getMessage());
        } catch (BusinessException ex) {
            // —— 修订路径领域异常映射：版本冲突提升为 DEP_CPR_105
            throw this.mapRevisionException(ex);
        } catch (OptimisticLockingFailureException ex) {
            // —— JPA @Version 冲突：与订货修订同口径，避免静默丢更新
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_105, "科室采购申请单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     * 提交科室采购申请审批（IF-DEP-CPR-06）。
     *
     * @param request 须含 busNo
     * @return 提交后状态
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} busNo 为空；
     *                            {@link ErrorCodeConstants#DEP_CPR_003} 状态不允许提交；
     *                            {@link ErrorCodeConstants#DEP_CPR_101} 操作人未绑定医务人员
     */
    public SubmitDeptPurchaseRequestApprovalResponse submitDeptPurchaseRequestApproval(
        SubmitDeptPurchaseRequestApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        this.preAssertDeptPurchaseRequestForWorkflow(request.getBusNo().trim());
        Long applicantId = currentPractitionerResolver.requirePractitionerId(
            ErrorCodeConstants.DEP_CPR_101, "当前用户未绑定医务人员档案，无法提交审批");
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(
                purchaseRequestMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_CPR_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            // —— 领域非法参数映射为科室采购申请提交场景错误码
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_CPR_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        // Asm：映射提交后状态
        return purchaseRequestMapper.toSubmitDeptPurchaseRequestApprovalResponse(st);
    }

    /**
     * 撤回科室采购申请审批（IF-DEP-CPR-07）。
     *
     * @param request 须含 busNo
     * @return 撤回后状态
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} busNo 为空；
     *                            {@link ErrorCodeConstants#DEP_CPR_003} 状态不允许撤回
     */
    public WithdrawDeptPurchaseRequestApprovalResponse withdrawDeptPurchaseRequestApproval(
        WithdrawDeptPurchaseRequestApprovalRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        this.preAssertDeptPurchaseRequestForWorkflow(request.getBusNo().trim());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(
                purchaseRequestMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_CPR_101, "用户未登录")));
        } catch (BusinessException ex) {
            // —— 领域非法参数映射为科室采购申请撤回场景错误码
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_CPR_003);
        }
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        // Asm：映射撤回后状态
        return purchaseRequestMapper.toWithdrawDeptPurchaseRequestApprovalResponse(st);
    }

    /**
     * 审批同意科室采购申请（IF-DEP-CPR-08）：仅置 {@link SupplyStatus#AGREE}，不改库存。
     *
     * @param request 须含 busNo
     * @return 同意成功标志（与药库订货 agree 同构，仅 {@code success}）
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} busNo 为空；
     *                            {@link ErrorCodeConstants#DEP_CPR_103} 单据不存在；
     *                            {@link ErrorCodeConstants#DEP_CPR_104} 重复同意；
     *                            {@link ErrorCodeConstants#DEP_CPR_003} 状态不允许
     */
    public AgreeDeptPurchaseRequestResponse agreeDeptPurchaseRequest(AgreeDeptPurchaseRequestRequest request) {
        // Gate：busNo 不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "busNo 不能为空");
        }
        String busNoTrim = request.getBusNo().trim();
        // Pre：类型守卫 + 读侧校验未重复同意
        this.preAssertDeptPurchaseRequestForWorkflow(busNoTrim);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(busNoTrim);
        // —— 读侧校验未重复同意（与订货 agree 前置校验同构，但无后续发放链）
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_104, "科室采购申请单已同意，禁止重复同意");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_CPR_101, "用户未登录");
        try {
            // Cmd：领域 agreeRequest——同意终点，禁止扩展订货/库存编排
            supplyRequestDomainService.agreeRequest(purchaseRequestMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            // —— 同意路径专用：「已同意」语义提升为 DEP_CPR_104
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            // —— JPA @Version 冲突：与修订同口径
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_105, "科室采购申请单已被并发修改，请刷新后重试", ex);
        }
        // Asm：映射同意响应（仅 success，与订货同构）
        return purchaseRequestMapper.toAgreeDeptPurchaseRequestResponse();
    }

    /**
     * 驳回科室采购申请（IF-DEP-CPR-09）。
     *
     * @param request 须含 busNo 与驳回原因
     * @return 驳回成功标志
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} 参数非法；
     *                            {@link ErrorCodeConstants#DEP_CPR_003} 状态不允许驳回
     */
    public RejectDeptPurchaseRequestResponse rejectDeptPurchaseRequest(RejectDeptPurchaseRequestRequest request) {
        // Gate：busNo 与驳回原因不可为空
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "busNo 不能为空");
        }
        if (StringUtils.isEmpty(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "驳回原因不能为空");
        }
        this.preAssertDeptPurchaseRequestForWorkflow(request.getBusNo().trim());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_CPR_101, "用户未登录");
        try {
            // Cmd：领域驳回
            supplyRequestDomainService.rejectRequest(purchaseRequestMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            // —— 领域非法参数映射为科室采购申请驳回场景错误码
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_CPR_003);
        }
        // Asm：映射驳回响应
        return purchaseRequestMapper.toRejectDeptPurchaseRequestResponse();
    }

    /**
     * 删除科室采购申请单（IF-DEP-CPR-10）。
     *
     * @param request 须含 busNo
     * @return 删除成功标志
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} busNo 为空；
     *                            {@link ErrorCodeConstants#DEP_CPR_005} 删除被阻断
     */
    public RemoveDeptPurchaseRequestResponse removeDeptPurchaseRequest(RemoveDeptPurchaseRequestRequest request) {
        // Gate：请求与 busNo 不可为空
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        this.preAssertDeptPurchaseRequestForWorkflow(request.getBusNo().trim());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.DEP_CPR_101, "用户未登录");
        try {
            // Cmd：领域逻辑删除同号全部明细行
            supplyRequestDomainService.removeByBusNo(purchaseRequestMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            // —— 领域非法参数映射为科室采购申请删除场景错误码
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.DEP_CPR_005);
        }
        // Asm：映射删除响应
        return purchaseRequestMapper.toRemoveDeptPurchaseRequestResponse();
    }

    /**
     * 科室采购申请选品分页（IF-DEP-CPR-11）：产品定义级，与药库订货选品同构。
     *
     * @param request 分页窗口与选品筛选条件
     * @return 产品定义展示行分页
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} 分页或库位参数非法；
     *                            {@link ErrorCodeConstants#DEP_CPR_102} 选品查询失败
     */
    public PageProductsForDeptPurchaseRequestResponse pageProductsForDeptPurchaseRequest(
        PageProductsForDeptPurchaseRequestRequest request) {
        // Gate：分页窗口、目的科室药房与机构上下文
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEP_CPR_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEP_CPR_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
        this.assertProductTableNameWhitelistForDeptPurchaseSelection(request.getProductTableName());
        // Pre：组装产品定义分页查询
        ProductDefinitionPageQuery query = purchaseRequestMapper.toProductDefinitionPageQuery(request);
        try {
            // Cmd：分页查询采购选品
            Page<ProductDefinitionDisplayDto> page = productDefinitionPurchasePageLoader.pageForPurchase(query);
            PageProductsForDeptPurchaseRequestResponse response =
                purchaseRequestMapper.toPageProductsForDeptPurchaseRequestResponse(page);
            this.enrichDeptPurchaseProductSelectionRows(response.getContent());
            // Asm：返回富化后的选品分页
            return response;
        } catch (RuntimeException ex) {
            log.warn("pageProductsForDeptPurchaseRequest failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_102, "科室采购申请选品查询失败");
        }
    }

    /**
     * 列表聚合行展示名批量富化（制单人/审批人/科室药房/状态文案）。
     *
     * @param rows  当前页聚合行
     * @param lines 同页 busNo 对应的供应请求明细
     */
    private void enrichDeptPurchaseRequestListDisplayNames(
        List<DeptPurchaseRequestListRowDto> rows,
        List<SupplyRequestModel> lines) {
        if (rows == null || rows.isEmpty() || lines == null || lines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = this.buildDeptPurchaseRequestHeadLineByBusNo(lines);
        // —— 从各单号头行提取 applicantId/approverId，供后续批量查名
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
        for (DeptPurchaseRequestListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeLocationIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> purposeLocationNameById = new HashMap<>();
        // —— 科室药房名称按主键去重后逐条解析
        for (Long locationId : purposeLocationIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
            locOpt.ifPresent(loc -> {
                if (StringUtils.isNotEmpty(loc.getName())) {
                    purposeLocationNameById.put(locationId, loc.getName().trim());
                }
            });
        }
        // —— 将解析到的展示名写回聚合行；未命中主数据时保持 null，由前端降级
        for (DeptPurchaseRequestListRowDto row : rows) {
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
     * 科室采购申请详情：补齐项目名称、规格、厂家与行单位展示名。
     *
     * @param lines           详情明细行
     * @param products        产品读模型映射
     * @param terminologyMaps 选品术语
     */
    private void enrichDeptPurchaseRequestDetailProductDisplay(
        List<DeptPurchaseRequestDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps) {
        if (lines == null || lines.isEmpty() || products == null || products.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        // —— 逐行匹配读模型 compositeKey，补齐品名/规格/厂家与单位展示
        for (DeptPurchaseRequestDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getItemTable().trim(), dto.getItemId());
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
                // —— 多单位槽展示名与选品术语对齐
                this.productDefinitionDisplayTerminologyAssembler.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 详情行槽价 enrich：锚供应请求行持久化行价（BR-PRICE-SRC-001）。
     *
     * @param lines 详情行列表
     */
    private void enrichDeptPurchaseRequestDetailLines(List<DeptPurchaseRequestDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (DeptPurchaseRequestDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            // —— 锚供应请求行持久化行价写回 catalog 单位槽（BR-PRICE-SRC-001）
            ProductDefinitionCatalogUnitBandsAssembler.enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice(
                cat, dto.getPrice(), dto.getUnitCode(), null);
        }
    }

    /**
     * 采购选品行富化：读 PURCHASE 进价写 purchasePrice 与单位槽。
     *
     * @param rows 选品分页 content
     */
    private void enrichDeptPurchaseProductSelectionRows(List<ProductDefinitionItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        // —— 先收集本页涉及的 productTableName+id，批量拉读模型
        for (ProductDefinitionItemDto row : rows) {
            if (row != null && StringUtils.isNotEmpty(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(row.getProductDefinitionId());
                lookup.setProductTableName(row.getProductTableName().trim());
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                products = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                // —— 产品读模型批量加载失败时降级，选品仍可展示基础字段
                log.warn("batchGetProductByIds failed for dept purchase product selection rows", ex);
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
                    ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(pr, terminologyMaps);
                ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(bands, pr, purchasePerPackage);
                row.setUnitBands(bands);
                if (pr != null) {
                    row.setCategoryCodeDictText(pr.getCategoryCodeDictText());
                }
            } else {
                // —— 无产品主键的行不展示进价
                row.setPurchasePrice(null);
            }
            // —— 科室采购选品不展示可用库存，与订货选品字段同构但语义不同
            row.setAvailableQuantity(null);
        }
    }

    /**
     * 岗位可见且启用中的科室药房位置 id 集合。
     *
     * @return 经 ManagedLocation Gate 过滤后的科室药房 id
     */
    private Set<Long> resolveGatedDepartmentPharmacyLocationIdsForGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveDepartmentPharmacies());
    }

    /**
     * 断言给定科室药房主键在允许集合内且为启用中的科室药房形态。
     *
     * @param locationId 位置主键
     * @param allowed    岗位可见科室药房 id 集合
     */
    private void assertDepartmentPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.DEP_CPR_001, "目的科室药房不在当前用户可选科室药房范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "目的科室药房不在当前用户可选科室药房范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        // —— 目的位置须为启用中的科室药房形态
        if (locOpt.isEmpty()
            || !LocationForm.DEPARTMENT_PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "目的位置须为启用中的科室药房");
        }
    }

    /**
     * 业务单号非空校验。
     *
     * @param busNo 业务单号
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_001} busNo 为空
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "busNo 不能为空");
        }
    }

    /**
     * 审批/删除等工作流前类型守卫：按单号回读并断言为科室采购申请单。
     *
     * @param busNo 业务单号（已 trim）
     * @throws BusinessException {@link ErrorCodeConstants#DEP_CPR_103} 单据不存在或类型非科室采购申请单
     */
    private void preAssertDeptPurchaseRequestForWorkflow(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_103, "科室采购申请单不存在");
        }
        this.assertDeptPurchaseRequest(lines);
    }

    /**
     * 断言多行共享同一 {@link SupplyType#DEPT_PURCHASE_REQUEST} 类型。
     *
     * @param lines 供应请求行
     */
    private void assertDeptPurchaseRequest(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            // —— 任一行 typeEnum 非科室采购申请即拒绝，防止跨类型单号误读
            if (!SupplyType.DEPT_PURCHASE_REQUEST.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEP_CPR_103, "单据类型非科室采购申请单");
            }
        }
    }

    /**
     * 选品接口物品表白名单门控。
     *
     * @param raw 请求 productTableName
     */
    private void assertProductTableNameWhitelistForDeptPurchaseSelection(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return;
        }
        String table = raw.trim();
        // —— 选品仅允许药品/耗材定义表
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.DEP_CPR_001, "productTableName 非法");
        }
    }

    /**
     * 读头行状态枚举（提交/撤回等接口出参用）。
     *
     * @param busNo 业务单号（已 trim）
     * @return 头行 {@link SupplyStatus}；单据不存在时 null
     */
    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, lines.get(0).getStatusEnum());
    }

    /**
     * 按单号选取头行：同号多行时取 apply_time 最大者。
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
            // —— 同号多行时保留 apply_time 较新者作为头行代表
            if (curApply != null && (prevApply == null || curApply.isAfter(prevApply))) {
                best.put(bn, line);
            }
        }
        return best;
    }

    /**
     * 修订路径异常映射：版本冲突 → DEP_CPR_105。
     *
     * @param ex 领域业务异常
     * @return 映射后的异常
     */
    private BusinessException mapRevisionException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            // —— 领域消息含版本/并发语义时提升为 DEP_CPR_105
            if (msg != null && (msg.contains("版本") || msg.contains("并发") || msg.contains("刷新"))) {
                return new BusinessException(ErrorCodeConstants.DEP_CPR_105, msg, ex);
            }
        }
        if (ErrorCodeConstants.DEP_CPR_105.equals(ex.getErrorCode())) {
            return new BusinessException(ErrorCodeConstants.DEP_CPR_105, "科室采购申请单已被修改或版本不匹配，请刷新后重试", ex);
        }
        return ex;
    }

    /**
     * 领域非法参数映射为应用层默认错误码。
     *
     * @param ex          领域业务异常
     * @param defaultCode 映射目标错误码（如 DEP_CPR_003 / DEP_CPR_005）
     * @return 映射后的异常；未命中 {@link ErrorCodeConstants#SUPPLY_REQUEST_INVALID_ARGUMENT} 时返回原异常
     */
    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * 同意路径专用：非法参数中「已同意」语义提升为 DEP_CPR_104。
     *
     * @param ex 领域业务异常
     * @return 映射后的异常
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.DEP_CPR_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.DEP_CPR_003, msg);
        }
        return ex;
    }
}
