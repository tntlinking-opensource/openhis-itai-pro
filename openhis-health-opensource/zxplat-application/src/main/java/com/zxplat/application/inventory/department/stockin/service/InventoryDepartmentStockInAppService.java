/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.stockin.service;

import com.zxplat.application.shared.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import com.zxplat.application.shared.pagination.PageQueryBinder;
import com.zxplat.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.application.inventory.department.stockin.dto.StockInDetailLineDto;
import com.zxplat.application.inventory.department.stockin.dto.StockInListRowDto;
import com.zxplat.application.inventory.department.stockin.mapper.InventoryDepartmentStockInMapper;
import com.zxplat.application.inventory.department.stockin.request.*;
import com.zxplat.application.inventory.department.stockin.response.*;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.administration.practitioner.service.PractitionerQueryService;
import com.zxplat.application.shared.practitioner.PractitionerRoleScopeLoader;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.InventoryDepartmentConstants;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.enums.LocationStatus;
import com.zxplat.domain.share.enums.SupplyStatus;
import com.zxplat.domain.share.enums.SupplyType;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 科室入库单（{@link SupplyType#DEPT_STOCK_IN}）应用编排（只读壳单台账 · IF-DEPT-IN · DM §6.1.9）。
 *
 * <p>本服务<strong>不支持</strong>手工新建/改单/审批（{@link ErrorCodeConstants#DEPT_IN_002}）；
 * 单据由药库出库（AS-INV-WHS-003）同意后系统自动生成且业务态已为 {@link SupplyStatus#AGREE}，
 * 本页仅列表/详情只读。库存落账与追溯在出库同意相位完成。</p>
 *
 * <p>库位门禁：目的侧须为启用中的 {@link LocationForm#DEPARTMENT_PHARMACY}（{@code listAllActiveDepartmentPharmacies}）。</p>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} → {@code POST /api/app/inventoryDepartmentStockIn/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryDepartmentStockInAppService {

    /** 会话机构上下文解析，列表/详情 Gate 用。 */
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    /** 分页窗口 Gate（pageNo/pageSize 必填场景）。 */
    private final PageWindowGate pageWindowGate;
    /** 供应请求读侧：列表聚合、按 busNo 详情。 */
    private final SupplyRequestQueryService supplyRequestQueryService;
    /** 库位读侧：科室药房/药库下拉与名称回填。 */
    private final LocationQueryService locationQueryService;
    /** 机构内药师/库管人员下拉。 */
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    /** 医务人员读侧：列表制单人/审批人姓名回填。 */
    private final PractitionerQueryService practitionerQueryService;
    /** 岗位管辖库位 Gate（科室药房 id 收窄）。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    /** 产品定义批量读（详情展示 enrich）。 */
    /** 选品术语与单位展示名。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    /** 请求/响应与读模型装配。 */
    private final InventoryDepartmentStockInMapper stockInMapper;

    /**
     * 加载页主数据（库位、人员、状态等下拉）。
     *
     * @param request 可为空；当前无额外筛选字段
     * @return 表单初始化选项集合
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_101} 机构上下文缺失或主数据加载失败
     */
    public LoadStockInFormInitResponse loadStockInFormInit(LoadStockInFormInitRequest request) {
        // Gate：会话机构上下文须存在，否则无法按管辖收窄库位
        // Pre：拉取启用库位并经管辖 Gate 收窄；经手人取全体在职医务人员（不限药师/库管）
        // Asm：经手人写入 pharmacistOptions（前端与库管选项合并；库管位留空）
        managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.DEPT_IN_101, "机构上下文缺失");
        try {
            List<Location> departmentPharmacies = locationQueryService.listAllActiveDepartmentPharmacies();
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedDepartmentPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(departmentPharmacies);
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> handlers =
                this.practitionerRoleScopeLoader.listAllActivePractitionersForInventoryDocHandlerSelect();
            return stockInMapper.toLoadStockInFormInitResponse(
                stockInMapper.toOptionDtoListFromLocations(gatedDepartmentPharmacies),
                stockInMapper.toOptionDtoListFromLocations(gatedWarehouses),
                stockInMapper.toOptionDtoListInboundItemTableTypes(),
                stockInMapper.toOptionDtoListSupplyStatuses(),
                stockInMapper.toOptionDtoListFromPractitioners(handlers),
                List.of());
        } catch (RuntimeException ex) {
            log.warn("loadStockInFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_101, "科室入库页主数据加载失败");
        }
    }

    /**
     * 分页查询单据列表。
     *
     * @param request 含库位筛选、状态与分页窗口
     * @return 分页列表结果
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_001} 参数非法；
     *     {@link ErrorCodeConstants#DEPT_IN_101} 机构上下文缺失；
     *     {@link ErrorCodeConstants#DEPT_IN_102} 列表查询失败
     */
    public PageStockInsResponse pageStockIns(PageStockInsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        // Pre：组装供应单据列表查询条件
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "请求不能为空");
        }
        if (request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.DEPT_IN_001, "分页参数不能为空");
        this.validateOptionalStockInTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.DEPT_IN_101, "机构上下文缺失");
        Set<Long> allowedDepartmentPharmacies = this.resolveGatedDepartmentPharmacyLocationIdsForGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.DEPT_STOCK_IN.getCode());
        // purposeLocationId=0：当前用户可见的全部科室药房；否则单科室药房且须在管辖门控内
        if (Long.valueOf(InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID)
            .equals(request.getPurposeLocationId())) {
            if (allowedDepartmentPharmacies.isEmpty()) {
                listQuery.setPurposeLocationIds(
                    List.of(InventoryDepartmentConstants.LIST_FILTER_NO_VISIBLE_DEPARTMENT_PHARMACY_PLACEHOLDER_ID));
            } else {
                listQuery.setPurposeLocationIds(new ArrayList<>(allowedDepartmentPharmacies));
            }
        } else {
            // 单科室药房筛选（防横向越权）
            this.assertDepartmentPharmacyLocationAllowed(request.getPurposeLocationId(), allowedDepartmentPharmacies);
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (request.getSourceLocationId() != null) {
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "statusEnum 非法");
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
            List<StockInListRowDto> records = this.stockInMapper.toStockInListAggregated(busNos, lines);
            this.enrichStockInListSourceLocationNames(records);
            this.enrichStockInListStatusEnumText(records);
            // —— 壳单侧栏与出库同构：制单人/审批人姓名；历史壳缺审批人时从 originalBusNo 出库头补全。——
            this.enrichStockInListDisplayNames(records);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合分页结果并映射为列表响应
            return this.stockInMapper.toPageStockInsResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageStockIns failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_102, "科室入库单列表查询失败");
        }
    }

    /**
     * 按业务单号查询单据详情。
     *
     * @param request 须含非空 busNo
     * @return 头信息与明细行
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_001} busNo 为空；
     *     {@link ErrorCodeConstants#DEPT_IN_103} 单不存在或类型不符
     */
    public GetStockInDetailResponse getStockInDetail(GetStockInDetailRequest request) {
        // Gate：busNo 不可为空
        // Pre：按单号回读供应请求行并批量预取产品展示信息
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_103, "科室入库单不存在");
        }
        this.assertStockIn(lines);
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
            log.warn("batchGetProductByIds degraded for department stock in detail", ex);
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
        GetStockInDetailResponse res = stockInMapper.toGetStockInDetailResponse(lines, hints);
        // Asm：补齐产品展示名与行槽价后返回详情
        // —— 与药库出库/进货单详情同构：写模型行仅含 item 引用，展示名/规格/厂家须从产品读模型回填。——
        this.enrichStockInDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichStockInDetailLines(res.getLines());
        return res;
    }

    /**
     * 不支持手工新建科室入库单（须由药库出库 agree 生成壳单）。
     *
     * @param request 单头、明细与库房门禁字段
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public CreateStockInResponse createStockIn(CreateStockInRequest request) {
        // Gate：入库壳单只读，拒绝一切手工新建
        return this.rejectManualStockInWrite("新建");
    }

    /**
     * 不支持手工修订科室入库单。
     *
     * @param request 含 busNo 与明细
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public UpdateStockInResponse updateStockIn(UpdateStockInRequest request) {
        // Gate：入库壳单只读，拒绝手工修订
        return this.rejectManualStockInWrite("修订");
    }

    /**
     * 入库壳单已终态同意，禁止提交审批流。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public SubmitStockInApprovalResponse submitStockInApproval(SubmitStockInApprovalRequest request) {
        // Gate：壳单已 AGREE，无独立审批链
        return this.rejectManualStockInWrite("提交审批");
    }

    /**
     * 入库壳单已终态同意，禁止撤回。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public WithdrawStockInApprovalResponse withdrawStockInApproval(WithdrawStockInApprovalRequest request) {
        // Gate：壳单已 AGREE，禁止撤回
        return this.rejectManualStockInWrite("撤回审批");
    }

    /**
     * 库存已在药库出库 agree 时落账，本接口禁止再次审批同意。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public AgreeStockInResponse agreeStockIn(AgreeStockInRequest request) {
        // Gate：库存已在出库 agree 落账，禁止二次同意
        return this.rejectManualStockInWrite("审批同意");
    }

    /**
     * 系统入库壳单不允许驳回（无独立审批链）。
     *
     * @param request 含 busNo 与驳回原因
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public RejectStockInResponse rejectStockIn(RejectStockInRequest request) {
        // Gate：系统壳单无驳回路径
        return this.rejectManualStockInWrite("驳回");
    }

    /**
     * 系统入库壳单不允许删除（追溯绑定出库单）。
     *
     * @param request 须含 busNo
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public RemoveStockInResponse removeStockIn(RemoveStockInRequest request) {
        // Gate：壳单须保留追溯链，禁止删除
        return this.rejectManualStockInWrite("删除");
    }

    /**
     * 不支持手工选品（入库单仅由出库 agree 自动生成）。
     *
     * @param request 分页窗口与选品筛选条件
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    public PageProductsForStockInResponse pageProductsForStockIn(PageProductsForStockInRequest request) {
        // Gate：无手工建单路径，拒绝选品
        return this.rejectManualStockInWrite("选品");
    }

    /**
     * 科室入库列表：批量解析来源库位 id 并回填 {@code sourceLocationName}。
     *
     * @param rows 本页列表行；空则跳过
     */
    private void enrichStockInListSourceLocationNames(List<StockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 先收集本页全部来源库位 id，再批量查名称，避免列表行数次的 location 点查。——
        Set<Long> sourceIds = new HashSet<>();
        for (StockInListRowDto row : rows) {
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
        for (StockInListRowDto row : rows) {
            if (row == null || row.getSourceLocationId() == null) {
                continue;
            }
            row.setSourceLocationName(nameById.get(row.getSourceLocationId()));
        }
    }

    /**
     * 科室入库列表：将持久化 {@code statusEnum} 解析为 {@code statusEnumText} 展示文案。
     *
     * @param rows 本页列表行；非法码保持 text 为空
     */
    private void enrichStockInListStatusEnumText(List<StockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (StockInListRowDto row : rows) {
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
     * 科室入库列表：补全制单人/审批人展示名；历史壳单缺审批人时从关联出库单头回填 id 后再解析姓名。
     *
     * @param rows 本页列表行
     */
    private void enrichStockInListDisplayNames(List<StockInListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 历史壳单未落审批人：按 originalBusNo 回读出库头，补 applicant/approver（只改列表 DTO，不写库）。——
        Set<String> outboundBusNos = new HashSet<>();
        for (StockInListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            boolean missingPeople = row.getApplicantId() == null || row.getApproverId() == null;
            if (!missingPeople) {
                continue;
            }
            if (StringUtils.isEmpty(row.getOriginalBusNo())) {
                continue;
            }
            outboundBusNos.add(row.getOriginalBusNo().trim());
        }
        Map<String, SupplyRequestModel> outboundHeadByBusNo = new HashMap<>();
        for (String outBusNo : outboundBusNos) {
            SupplyByBusNoQuery q = new SupplyByBusNoQuery();
            q.setBusNo(outBusNo);
            List<SupplyRequestModel> outLines = this.supplyRequestQueryService.getSupplyByBusNo(q);
            if (outLines == null || outLines.isEmpty()) {
                continue;
            }
            SupplyRequestModel head = outLines.get(0);
            if (head != null) {
                outboundHeadByBusNo.put(outBusNo, head);
            }
        }
        for (StockInListRowDto row : rows) {
            if (row == null || StringUtils.isEmpty(row.getOriginalBusNo())) {
                continue;
            }
            SupplyRequestModel outHead = outboundHeadByBusNo.get(row.getOriginalBusNo().trim());
            if (outHead == null) {
                continue;
            }
            if (row.getApplicantId() == null && outHead.getApplicantId() != null) {
                row.setApplicantId(outHead.getApplicantId());
            }
            if (row.getApproverId() == null && outHead.getApproverId() != null) {
                row.setApproverId(outHead.getApproverId());
            }
            if (row.getApprovalTime() == null && outHead.getApprovalTime() != null) {
                row.setApprovalTime(outHead.getApprovalTime());
            }
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (StockInListRowDto row : rows) {
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
        for (StockInListRowDto row : rows) {
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
     * 科室入库详情：补齐项目名称、规格、厂家与行单位展示名（与药库出库 IF-WHS-SO-03 同构）。
     *
     * @param lines            详情明细行
     * @param products         产品读模型映射（表名+主键复合键）
     * @param terminologyMaps  选品术语（单位展示名等）
     */
    private void enrichStockInDetailProductDisplay(
        List<StockInDetailLineDto> lines,
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
        for (StockInDetailLineDto dto : lines) {
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
    private void enrichStockInDetailLines(List<StockInDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (StockInDetailLineDto dto : lines) {
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
     * 经岗位管辖 Gate 收窄后的启用科室药房位置 id 集合。
     *
     * @return 当前用户可选科室药房主键集合
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
            locationId, allowed, ErrorCodeConstants.DEPT_IN_001, "目的科室药房不在当前用户可选范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "目的科室药房不在当前用户可选范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.DEPARTMENT_PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "目的位置须为启用中的科室药房");
        }
    }

    /**
     * 断言多行共享同一 {@code DEPT_STOCK_IN} 类型（详情/列表读侧一致性）。
     *
     * @param lines 供应请求行
     */
    private void assertStockIn(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.DEPT_STOCK_IN.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.DEPT_IN_103, "单据类型非科室入库单");
            }
        }
    }

    /**
     * 列表可选 type 收窄：若传入则须为科室入库类型持久化码。
     *
     * @param typeEnumCode 请求 typeEnum，可 blank
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_001} 非空白但无法解析或不是科室入库类型
     */
    private void validateOptionalStockInTypeEnum(String typeEnumCode) {
        if (StringUtils.isEmpty(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "typeEnum 非法");
        }
        if (declared != SupplyType.DEPT_STOCK_IN) {
            throw new BusinessException(ErrorCodeConstants.DEPT_IN_001, "typeEnum 必须为科室入库单类型");
        }
    }

    /**
     * 科室入库壳单仅允许由药库出库 agree 生成；拒绝一切手工写路径。
     *
     * <p>泛型返回仅为满足调用方 {@code return} 语法；方法体始终抛出 {@link BusinessException}，永不正常返回。</p>
     *
     * @param actionLabel 操作名称（如「新建」「审批同意」），用于提示文案
     * @param <T>         调用方期望的 Response 类型（占位，不会实际实例化）
     * @return 不返回；编译器占位
     * @throws BusinessException {@link ErrorCodeConstants#DEPT_IN_002}
     */
    private <T> T rejectManualStockInWrite(String actionLabel) {
        String label = actionLabel;
        if (label == null) {
            label = "操作";
        }
        throw new BusinessException(ErrorCodeConstants.DEPT_IN_002,
            "科室入库单由药库出库同意后系统自动生成，不支持手工" + label);
    }
}
