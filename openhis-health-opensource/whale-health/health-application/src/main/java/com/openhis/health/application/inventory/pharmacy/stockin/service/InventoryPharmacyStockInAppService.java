/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.stockin.service;

import com.openhis.health.application.shared.inventory.InventoryListPractitionerNameResolver;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInDetailLineDto;
import com.openhis.health.application.inventory.pharmacy.stockin.dto.PharmacyStockInListRowDto;
import com.openhis.health.application.inventory.pharmacy.stockin.mapper.InventoryPharmacyStockInMapper;
import com.openhis.health.application.inventory.pharmacy.stockin.request.*;
import com.openhis.health.application.inventory.pharmacy.stockin.response.*;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.application.basic.administration.support.PractitionerRoleScopeLoader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InventoryPharmacyConstants;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 药房入库单（{@link SupplyType#PHARMACY_STOCK_IN}）应用编排（AS-INV-PHA-001）。
 *
 * <p>本服务<strong>不支持</strong>手工新建/改单/审批（{@link ErrorCodeConstants#PHA_IN_002}）；
 * 单据由药库出库（AS-INV-WHS-003）同意后系统自动生成且业务态已为 {@link SupplyStatus#AGREE}，
 * 本页仅列表/详情只读。库存落账与追溯在出库同意相位完成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPharmacyStockInAppService {

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final InventoryPharmacyStockInMapper pharmacyStockInMapper;
    private final InventoryListPractitionerNameResolver inventoryListPractitionerNameResolver;

    /**
     * 加载页主数据（库位、人员、状态等下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 表单初始化选项集合
     */
    public LoadPharmacyStockInFormInitResponse loadPharmacyStockInFormInit(LoadPharmacyStockInFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位与人员
        // Pre：拉取启用库位/人员并经管辖 Gate 收窄
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.PHA_IN_101, "机构上下文缺失");
        try {
            List<Location> pharmacies = locationQueryService.listAllActivePharmacies();
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForInventoryDocHandlerRole(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            return pharmacyStockInMapper.toLoadPharmacyStockInFormInitResponse(
                pharmacyStockInMapper.toOptionDtoListFromLocations(gatedPharmacies),
                pharmacyStockInMapper.toOptionDtoListFromLocations(gatedWarehouses),
                pharmacyStockInMapper.toOptionDtoListInboundItemTableTypes(),
                pharmacyStockInMapper.toOptionDtoListSupplyStatuses(),
                pharmacyStockInMapper.toOptionDtoListFromPractitioners(pharmacists),
                pharmacyStockInMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadPharmacyStockInFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_IN_101, "药房入库页主数据加载失败");
        }
    }

    /**
     * 分页查询单据列表。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     */
    public PagePharmacyStockInsResponse pagePharmacyStockIns(PagePharmacyStockInsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        // Pre：组装供应单据列表查询条件
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.PHA_IN_001, "分页参数不能为空");
        this.validateOptionalPharmacyStockInTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.PHA_IN_101, "机构上下文缺失");
        Set<Long> allowedPharmacies = this.resolveGatedPharmacyLocationIdsForPharmacyGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.PHARMACY_STOCK_IN.getCode());
        // purposeLocationId=0：当前用户可见的全部药房；否则单药房且须在管辖门控内
        if (Long.valueOf(InventoryPharmacyConstants.ALL_PHARMACIES_LIST_FILTER_LOCATION_ID)
            .equals(request.getPurposeLocationId())) {
            if (allowedPharmacies.isEmpty()) {
                listQuery.setPurposeLocationIds(
                    List.of(InventoryPharmacyConstants.LIST_FILTER_NO_VISIBLE_PHARMACY_PLACEHOLDER_ID));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedPharmacies));
            }
        } else {
            // 单药房筛选（防横向越权）
            this.assertPharmacyLocationAllowed(request.getPurposeLocationId(), allowedPharmacies);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (request.getSourceLocationId() != null) {
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "statusEnum 非法");
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
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<PharmacyStockInListRowDto> records = this.pharmacyStockInMapper.toPharmacyStockInListAggregated(busNos, lines);
            this.enrichPharmacyStockInListSourceLocationNames(records);
            this.enrichPharmacyStockInListStatusEnumText(records);
            this.enrichPharmacyStockInListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            return this.pharmacyStockInMapper.toPagePharmacyStockInsResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pagePharmacyStockIns failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.PHA_IN_102, "药房入库单列表查询失败");
        }
    }

    /**
     * 按业务单号查询单据详情。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     */
    public GetPharmacyStockInDetailResponse getPharmacyStockInDetail(GetPharmacyStockInDetailRequest request) {
        // Gate：busNo 不可为空
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_103, "药房入库单不存在");
        }
        this.assertPharmacyStockIn(lines);
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
            log.warn("batchGetProductByIds degraded for pharmacy stock in detail", ex);
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
        GetPharmacyStockInDetailResponse res = pharmacyStockInMapper.toGetPharmacyStockInDetailResponse(lines, hints);
        // —— 与药库出库/进货单详情同构：写模型行仅含 item 引用，展示名/规格/厂家须从产品读模型回填。——
        this.enrichPharmacyStockInDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichPharmacyStockInDetailLines(res.getLines());
        return res;
    }

    /**
     * 不支持手工新建药房入库单（须由药库出库 agree 生成壳单）。
     *
     * @param request 单头、明细与库房门禁字段
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public CreatePharmacyStockInResponse createPharmacyStockIn(CreatePharmacyStockInRequest request) {
        // Gate：入库壳单只读，拒绝一切手工新建
        return this.rejectManualPharmacyStockInWrite("新建");
    }

    /**
     * 不支持手工修订药房入库单。
     *
     * @param request 含 busNo 与明细
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public UpdatePharmacyStockInResponse updatePharmacyStockIn(UpdatePharmacyStockInRequest request) {
        // Gate：入库壳单只读，拒绝手工修订
        return this.rejectManualPharmacyStockInWrite("修订");
    }

    /**
     * 入库壳单已终态同意，禁止提交审批流。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public SubmitPharmacyStockInApprovalResponse submitPharmacyStockInApproval(SubmitPharmacyStockInApprovalRequest request) {
        // Gate：壳单已 AGREE，无独立审批链
        return this.rejectManualPharmacyStockInWrite("提交审批");
    }

    /**
     * 入库壳单已终态同意，禁止撤回。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public WithdrawPharmacyStockInApprovalResponse withdrawPharmacyStockInApproval(WithdrawPharmacyStockInApprovalRequest request) {
        // Gate：壳单已 AGREE，禁止撤回
        return this.rejectManualPharmacyStockInWrite("撤回审批");
    }

    /**
     * 库存已在药库出库 agree 时落账，本接口禁止再次审批同意。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public AgreePharmacyStockInResponse agreePharmacyStockIn(AgreePharmacyStockInRequest request) {
        // Gate：库存已在出库 agree 落账，禁止二次同意
        return this.rejectManualPharmacyStockInWrite("审批同意");
    }

    /**
     * 系统入库壳单不允许驳回（无独立审批链）。
     *
     * @param request 含 busNo 与驳回原因
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public RejectPharmacyStockInResponse rejectPharmacyStockIn(RejectPharmacyStockInRequest request) {
        // Gate：系统壳单无驳回路径
        return this.rejectManualPharmacyStockInWrite("驳回");
    }

    /**
     * 系统入库壳单不允许删除（追溯绑定出库单）。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public RemovePharmacyStockInResponse removePharmacyStockIn(RemovePharmacyStockInRequest request) {
        // Gate：壳单须保留追溯链，禁止删除
        return this.rejectManualPharmacyStockInWrite("删除");
    }

    /**
     * 不支持手工选品（入库单仅由出库 agree 自动生成）。
     *
     * @param request 分页窗口与选品筛选条件
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    public PageProductsForPharmacyStockInResponse pageProductsForPharmacyStockIn(PageProductsForPharmacyStockInRequest request) {
        // Gate：无手工建单路径，拒绝选品
        return this.rejectManualPharmacyStockInWrite("选品");
    }

    /**
     * 药房入库列表：批量解析来源库位 id 并回填 {@code sourceLocationName}。
     *
     * @param rows 本页列表行；空则跳过
     */
    private void enrichPharmacyStockInListSourceLocationNames(List<PharmacyStockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 先收集本页全部来源库位 id，再批量查名称，避免列表行数次的 location 点查。——
        Set<Long> sourceIds = new HashSet<>();
        for (PharmacyStockInListRowDto row : rows) {
            if (row != null && row.getSourceLocationId() != null) {
                sourceIds.add(row.getSourceLocationId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        for (Long sid : sourceIds) {
            locationQueryService.findOptionalById(sid).ifPresent(loc -> {
                if (StringUtils.isNotEmpty(loc.getName())) {
                    nameById.put(sid, loc.getName().trim());
                }
            });
        }
        // —— 回填展示列：未命中名称时保持 null，由前端决定是否展示占位。——
        for (PharmacyStockInListRowDto row : rows) {
            if (row == null || row.getSourceLocationId() == null) {
                continue;
            }
            row.setSourceLocationName(nameById.get(row.getSourceLocationId()));
        }
    }

    /**
     * 药房入库列表：将持久化 {@code statusEnum} 解析为 {@code statusEnumText} 展示文案。
     *
     * @param rows 本页列表行；非法码保持 text 为空
     */
    private void enrichPharmacyStockInListStatusEnumText(List<PharmacyStockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (PharmacyStockInListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getStatusEnum())) {
                continue;
            }
            // —— 列表行 statusEnum 为持久化 code：仅解析成功时写中文，非法码保持 text 为空。——
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    /**
     * 药房入库列表行 enrich：批量解析制单人/审批人姓名（IF-PHA-IN-02 侧栏卡片展示）。
     *
     * @param rows        当前页聚合列表行
     * @param detailLines 当前页供应请求明细（非空门禁；主键以 rows 为准）
     */
    private void enrichPharmacyStockInListDisplayNames(
        List<PharmacyStockInListRowDto> rows,
        List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (PharmacyStockInListRowDto row : rows) {
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
        Map<Long, String> practitionerNameById = this.inventoryListPractitionerNameResolver.resolveNamesByIds(practitionerIds);
        for (PharmacyStockInListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getApplicantId() != null) {
                String creatorName = practitionerNameById.get(row.getApplicantId());
                if (creatorName != null) {
                    row.setCreatorName(creatorName);
                }
            }
            if (row.getApproverId() != null) {
                String approverName = practitionerNameById.get(row.getApproverId());
                if (approverName != null) {
                    row.setApproverName(approverName);
                }
            }
        }
    }

    /**
     * 药房入库详情：补齐项目名称、规格、厂家与行单位展示名（与药库出库 IF-WHS-SO-03 同构）。
     *
     * @param lines            详情明细行
     * @param products         产品读模型映射（表名+主键复合键）
     * @param terminologyMaps  选品术语（单位展示名等）
     */
    private void enrichPharmacyStockInDetailProductDisplay(
        List<PharmacyStockInDetailLineDto> lines,
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
        for (PharmacyStockInDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isEmpty(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            // —— 壳单详情：前端表格「项目名称/规格/厂家」读这三列，不能只依赖 productCatalog。——
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
     * 详情行槽价：仅锚 {@code SupplyRequest} 持久化行价（**BR-PRICE-SRC-001**）。
     *
     * @param lines 详情行列表，可为 null 或空
     */
    private void enrichPharmacyStockInDetailLines(List<PharmacyStockInDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (PharmacyStockInDetailLineDto dto : lines) {
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
     * 经岗位管辖 Gate 收窄后的启用中药房位置 id 集合。
     *
     * @return 当前用户可选药房主键集合
     */
    private Set<Long> resolveGatedPharmacyLocationIdsForPharmacyGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActivePharmacies());
    }

    /**
     * 断言给定药房主键在允许集合内且为启用中的药房形态。
     *
     * @param locationId 位置主键
     * @param allowed    岗位可见药房 id 集合
     */
    private void assertPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.PHA_IN_001, "目的药房不在当前用户可选药房范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "目的药房不在当前用户可选药房范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "目的位置须为启用中的药房");
        }
    }

    /**
     * 断言多行共享同一 {@code PHARMACY_STOCK_IN} 类型（详情/列表读侧一致性）。
     *
     * @param lines 供应请求行
     */
    private void assertPharmacyStockIn(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_STOCK_IN.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.PHA_IN_103, "单据类型非药房入库单");
            }
        }
    }

    /**
     * 列表可选 type 收窄：若传入则须为药房入库类型持久化码。
     *
     * @param typeEnumCode 请求 typeEnum，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_001} 非空白但无法解析或不是药房入库类型
     */
    private void validateOptionalPharmacyStockInTypeEnum(String typeEnumCode) {
        if (StringUtils.isEmpty(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "typeEnum 非法");
        }
        if (declared != SupplyType.PHARMACY_STOCK_IN) {
            throw new BusinessException(ErrorCodeConstants.PHA_IN_001, "typeEnum 必须为药房入库单类型");
        }
    }

    /**
     * 药房入库执行单仅允许由药库出库 agree 生成壳单；拒绝一切手工写路径。
     *
     * <p>泛型返回仅为满足调用方 {@code return} 语法；方法体始终抛出 {@link BusinessException}，永不正常返回。</p>
     *
     * @param actionLabel 操作名称（如「新建」「审批同意」），用于提示文案
     * @param <T>         调用方期望的 Response 类型（占位，不会实际实例化）
     * @return 不返回；编译器占位
     * @throws BusinessException {@link ErrorCodeConstants#PHA_IN_002}
     */
    private <T> T rejectManualPharmacyStockInWrite(String actionLabel) {
        String label = actionLabel;
        if (label == null) {
            label = "操作";
        }
        throw new BusinessException(ErrorCodeConstants.PHA_IN_002,
            "药房入库单由药库出库同意后系统自动生成，不支持手工" + label);
    }
}
