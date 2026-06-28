/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inventory.warehouse.stockout.service;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.module.common.pagination.PagedResultBuilder;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.integration.support.insurance.inventory.InventoryMedicareUploadSupport;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.inventory.warehouse.stockout.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutDetailLineDto;
import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutLineDto;
import com.openhis.health.application.inventory.warehouse.stockout.dto.StockOutListRowDto;
import com.openhis.health.application.inventory.warehouse.stockout.mapper.InventoryWarehouseStockOutMapper;
import com.openhis.health.application.inventory.warehouse.stockout.request.*;
import com.openhis.health.application.inventory.warehouse.stockout.response.*;
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionSelectionTerminologySupport;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand;
import com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand;
import com.openhis.health.domain.workflow.supplyrequest.command.CreateRequisitionCommand;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
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
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import com.openhis.health.integration.insurance.inventory.command.InventoryUploadAfterTransferCommand;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 药库出库单（{@link SupplyType#CABINET_STOCK_OUT}）应用编排。
 *
 * <p>审批链维护供应请求；<strong>同意</strong>后依次：领域落已同意态 → 出库发放 → 源库扣减 / 目的库（常为药房）增加 → 追溯码，
 * 并可生成对侧药房入库壳单。列表/门禁同时约束源库 {@code source_location_id} 与目的库 {@code purpose_location_id}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseStockOutAppService {

    private static final String INV_CHG_TYPE_TRANSFER_OUT = "102";

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
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;
    private final InventoryWarehouseStockOutMapper stockOutMapper;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 加载库房单据页主数据（管辖药库、枚举选项、制单人员等）。

     *

     * @param request 页初始化请求；机构上下文由服务端解析

     * @return 下拉选项与字典项

     */
    public LoadStockOutFormInitResponse loadStockOutFormInit(LoadStockOutFormInitRequest request) {
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.WHS_SO_101, "机构上下文缺失");
        try {
            // Gate：机构会话上下文
            // Pre：装载管辖范围内启用位置与角色人员
            // Asm：Mapper 映射页初始化响应
            // —— 门控：管辖库位非空时与启用位置求交；管辖 empty 时不拼 location_id 谓词。——
            List<Location> pharmacies = locationQueryService.listAllActivePharmacies();
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            return stockOutMapper.toLoadStockOutFormInitResponse(
                stockOutMapper.toOptionDtoListFromLocations(gatedWarehouses),
                stockOutMapper.toOptionDtoListFromLocations(gatedPharmacies),
                stockOutMapper.toOptionDtoListInboundItemTableTypes(),
                stockOutMapper.toOptionDtoListSupplyStatuses(),
                stockOutMapper.toOptionDtoListFromPractitioners(pharmacists),
                stockOutMapper.toOptionDtoListFromPractitioners(warehouseKeepers));
        } catch (RuntimeException ex) {
            log.warn("loadStockOutFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_SO_102, "出库页主数据加载失败");
        }
    }

    /**
     * 分页查询库房单据列表（按 busNo 聚合展示）。

     *

     * @param request 含分页窗口、库房与状态筛选

     * @return 聚合后的列表行与分页元数据

     */
    public PageStockOutsResponse pageStockOuts(PageStockOutsRequest request) {
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "请求不能为空");
        }
        // Pre：组装列表查询与库房门禁
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "sourceLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_SO_001, "分页参数不能为空");
        this.validateOptionalStockOutTypeEnum(request.getTypeEnum());
        // —— 机构上下文 + 岗位可见药库集合（sourceLocationId=0 与单库校验共用白名单）。——
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_SO_101, "机构上下文缺失");
        Set<Long> allowedWarehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_STOCK_OUT.getCode());
        // —— sourceLocationId=0：多药库 IN 查询；无可见药库时用占位 id 收窄为空页（避免无约束列表）。——
        if (Long.valueOf(0L).equals(request.getSourceLocationId())) {
            if (allowedWarehouses.isEmpty()) {
                listQuery.setSourceLocationIds(List.of(-1L));
            } else {
                listQuery.setSourceLocationIds(new ArrayList<>(allowedWarehouses));
            }
        } else {
            this.assertWarehouseLocationAllowed(request.getSourceLocationId(), allowedWarehouses);
            listQuery.setSourceLocationId(request.getSourceLocationId());
        }
        if (request.getPurposeLocationId() != null) {
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        if (StringUtils.isNotBlank(request.getStatusEnum())) {
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "statusEnum 非法");
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
            List<StockOutListRowDto> records = this.stockOutMapper.toStockOutListAggregated(busNos, lines);
            // —— 补全目的位置展示名（到何处）。——
            this.enrichStockOutListPurposeLocationNames(records);
            this.enrichStockOutListStatusEnumText(records);
            this.enrichStockOutListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合 busNo 行并映射分页响应
            return this.stockOutMapper.toPageStockOutsAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageStockOuts failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_SO_102, "出库单列表查询失败");
        }
    }

    /**
     * 按 busNo 回读单据详情（含明细行与展示增强）。

     *

     * @param request 含业务单号 busNo

     * @return 单头与明细

     */
    public GetStockOutDetailResponse getStockOutDetail(GetStockOutDetailRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            // Gate：busNo 非空
            // Pre：按 busNo 回读供应请求行
            // Pre：批量装载产品读模型
            // Asm：Mapper 映射详情并增强展示字段
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "busNo 不能为空");
        }
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_103, "出库单不存在");
        }
        this.assertCabinetStockOut(lines);
        // —— 批量加载产品读模型，装配各明细行 catalog hints（查询失败时降级为空映射，仍返回写模型字段）。——
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
            products = productDefinitionBatchLoader.loadAsMap(lookups);
        } catch (RuntimeException ex) {
            log.warn("batchGetProductByIds degraded for stock out detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
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
        GetStockOutDetailResponse res = stockOutMapper.toGetStockOutDetailResponse(lines, hints);
        this.enrichStockOutDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichStockOutDetailLines(res.getLines());
        return res;
    }

    /**
     * 新建库房单据草稿并落库供应请求。

     *

     * @param request 单头、明细与库房门禁字段

     * @return 新建后的 busNo

     */
    public CreateStockOutResponse createStockOut(CreateStockOutRequest request) {
        // Gate：请求体、明细与库房/供应商门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_SO_101, "机构上下文缺失");
        this.assertStockOutLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
        this.assertStockOutTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_SO_001);
        // —— 源库可用量与草稿行拆零量汇总比较。——
        this.assertStockOutDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        if (StringUtils.isNotBlank(request.getOriginalBusNo())) {
            this.validateOriginalPharmacyRequisitionForStockOut(request.getOriginalBusNo().trim(), request.getSourceLocationId());
        }
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "经手人不能为空");
        }
        this.assertStockOutLinesOccurrencePresent(request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_SO_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_SO_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        // —— 领域 createStockOut 落供应写模型（head + 明细行，类型 CABINET_STOCK_OUT）。——
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            String busNo = supplyRequestDomainService.createStockOut(
                stockOutMapper.toCreateStockOutCommand(request, operatorId, applicantId, null));
            return stockOutMapper.toCreateStockOutResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库参数不合法：" + ex.getMessage());
        }
    }

    /**
     *
     * @param request 修订请求；须含 {@code busNo}、非空明细及位置字段
     * @return 修订后单号（与请求同号）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_001} 参数/可用量非法；{@link ErrorCodeConstants#WHS_SO_002} 来源请领不可引用；{@link ErrorCodeConstants#WHS_SO_004} 不可编辑；
     *                            {@link ErrorCodeConstants#WHS_SO_101} 未登录
     */
    public UpdateStockOutResponse updateStockOut(UpdateStockOutRequest request) {
        // Gate：busNo、明细与可编辑态校验
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "源位置与目标位置不能为空");
        }
        // —— 机构 + 源/目的库位形态（修订与新建门控同口径）。——
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_SO_101, "机构上下文缺失");
        this.assertStockOutLocationGate(request.getSourceLocationId(), request.getPurposeLocationId());
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
            throw new BusinessException(ErrorCodeConstants.WHS_SO_004, "当前状态不允许编辑出库单");
        }
        this.assertStockOutTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_SO_001);
        // —— 修订草稿同样须满足源库可用量校验（与新建同构）。——
        this.assertStockOutDraftLinesWithinSourceAvailableInventory(request.getSourceLocationId(), request.getLines());
        if (StringUtils.isNotBlank(request.getOriginalBusNo())) {
            this.validateOriginalPharmacyRequisitionForStockOut(request.getOriginalBusNo().trim(), request.getSourceLocationId());
        }
        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "经手人不能为空");
        }
        this.assertStockOutLinesOccurrencePresent(request.getLines());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_SO_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_SO_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        // —— 领域 createStockOut 同号修订（子行全量对齐与软删语义由域层约束）。——
        try {
            // Cmd：组装 Create 命令并调用领域写服务
            String busNo = supplyRequestDomainService.createStockOut(
                stockOutMapper.toCreateStockOutCommand(request, operatorId, applicantId));
            return stockOutMapper.toUpdateStockOutResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库参数不合法：" + ex.getMessage());
        }
    }

    /**
     * 提交单据进入待审批态。

     *

     * @param request 含业务单号 busNo

     * @return 提交后的展示状态

     */
    public SubmitStockOutApprovalResponse submitStockOutApproval(SubmitStockOutApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_SO_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        // —— 工作流提交审批（非法参数类错误经 mapSupplyWorkflowException 回落接口码）。——
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(
                stockOutMapper.toSubmitApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_SO_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_SO_003);
        }
        // Asm：映射提交响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return stockOutMapper.toSubmitStockOutApprovalResponse(st);
    }

    /**
     * 撤回审批（回到可编辑草稿态）。

     *

     * @param request 含业务单号 busNo

     * @return 撤回后的展示状态

     */
    public WithdrawStockOutApprovalResponse withdrawStockOutApproval(WithdrawStockOutApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(
                stockOutMapper.toWithdrawApprovalCommand(request,
                    currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_SO_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_SO_003);
        }
        // Asm：映射撤回响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return stockOutMapper.toWithdrawStockOutApprovalResponse(st);
    }

    /** 同意：供应请求已同意 → 发放 → 源库扣减/目的库增加 → 追溯；含防重复同意与完成态发放门禁。 */
    public AgreeStockOutResponse agreeStockOut(AgreeStockOutRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "busNo 不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_SO_101, "用户未登录");

        // —— 同意出库单 ——
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        // —— 加载同意前快照，校验类型并防重复同意（与 supplyRequestValidation 配合）。——
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_103, "出库单不存在");
        }
        this.assertCabinetStockOut(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "出库单已同意，禁止重复同意");
        }

        // —— 领域状态机同意出库；失败映射为业务码（含「已同意」类短路）。——
        try {
            supplyRequestDomainService.agreeRequest(stockOutMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_105, "出库单已被并发修改，请刷新后重试", ex);
        }

        // —— 强制读回：与进货同意链一致，保证后续发放/库存读到已落库的 AGREE 行。——
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertCabinetStockOut(agreedLines);
        // —— 回读 AGREE 行后校验状态、持久化主键与数量，保证后续发放/库存编排输入合法。——
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "同意后回读状态异常，中止出库编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "出库明细缺少持久化主键，无法完成编排");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "出库明细数量无效，无法完成编排");
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
            throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "供应请求行已存在完成态发放，禁止重复同意出库");
        }

        // —— 发放主数据落库（类型固定药库出库）。——
        try {
            supplyDeliveryDomainService.createSupplyDelivery(
                stockOutMapper.toCreateSupplyDeliveryCommandForStockOut(agreedLines, operatorId));
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "出库发放落库失败：" + ex.getMessage());
        }

        // —— 批量产品读模型（拆零换算、展示名；单点异常记日志，缺键在后续换算处显式失败）。——
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
                productByKey = productDefinitionBatchLoader.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed during stock out inventory assemble", ex);
            }
        }

        // —— 内存编排：解析拆零量、源库存匹配键、合并扣减表、目的侧入账计划与追溯项（空白 trace 不入列）。——
        SupplyRequestModel head = agreedLines.get(0);
        String busNoTrim = request.getBusNo().trim();
        String traceOperationType = TraceNumberRecordOperationType.supplyTypeCode(SupplyType.CABINET_STOCK_OUT);
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems = new ArrayList<>();
        Map<Long, BigDecimal> decreaseMerged = new HashMap<>();
        List<StockOutDestIncr> destIncrs = new ArrayList<>();
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
                line, ErrorCodeConstants.WHS_SO_104, "供应请求行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();

            String resolvedLot = this.resolveLotNumberForStockOutInventory(line.getLotNumber());
            InventoryMatchKey srcKey = new InventoryMatchKey();
            srcKey.setLotNumber(resolvedLot);
            srcKey.setLocationId(sourceLocationId);
            srcKey.setItemTable(line.getItemTable());
            srcKey.setItemId(line.getItemId());
            Long sourceInventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(srcKey).orElse(null);
            if (sourceInventoryId == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "源库未找到匹配库存行，无法扣减");
            }
            decreaseMerged.merge(sourceInventoryId, splitQty, BigDecimal::add);

            StockOutDestIncr op = new StockOutDestIncr();
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

        // —— 源库按库存行 id 合并扣减（先扣后加，避免先加后扣窗口）。——
        try {
            inventoryItemDomainService.decreaseQuantitiesMerged(operatorId, decreaseMerged);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "源库库存扣减失败：" + ex.getMessage());
        }

        // —— 目的库逐行入账；命中既有行则加量，否则按四元组新建新库存项（与进货 Mapper 命令同构）。——
        for (StockOutDestIncr op : destIncrs) {
            SupplyRequestModel line = op.line;
            InventoryMatchKey dstKey = new InventoryMatchKey();
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
                        throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "源库库存行缺少供应商标识，无法在目的库新建账面");
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
                    // —— 供应行未带效期时承袭源库库存生产日期/效期（出库行常不落 start_time/end_time）。——
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
                throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "目的库库存增加失败：" + ex.getMessage());
            }
        }

        // —— 对侧药房入库壳单须先于追溯落库，失败则整单回滚。——
        // 壳单制单人优先沿用出库头行已落库的申请人（医务人员主键）；历史脏数据缺省时回落当前会话医务人员，禁止用用户主键冒充。
        Long shellApplicantId = head.getApplicantId();
        if (shellApplicantId == null) {
            shellApplicantId = this.requireWarehousePreparerPractitionerId(
                ErrorCodeConstants.WHS_SO_101,
                "用户未登录",
                "当前用户未绑定医务人员档案，无法生成药房入库壳单制单人");
        }
        String generatedInBusNo;
        try {
            CreateRequisitionCommand shellCmd = stockOutMapper.toCreateRequisitionCommandForShellPharmacyStockIn(
                agreedLines, busNoTrim, operatorId, shellApplicantId);
            generatedInBusNo = supplyRequestDomainService.createRequisition(shellCmd);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "同步生成药房入库壳单失败：" + ex.getMessage());
        }

        // —— 壳单成功后写入追溯出库明细；空白 trace 已在组装阶段跳过。——
        if (!traceItems.isEmpty()) {
            try {
                AppendTraceRecordsCommand traceCmd = new AppendTraceRecordsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                traceNumberRecordDomainService.appendTraceRecords(traceCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "追溯码出库记录失败：" + ex.getMessage());
            }
        }
        tryUploadMedicareInventoryAfterTransferAgree(request, agreedLines);
        return stockOutMapper.toAgreeStockOutResponse(
            busNoTrim, SupplyStatus.AGREE.getCode(), generatedInBusNo);
    }

    private void tryUploadMedicareInventoryAfterTransferAgree(
        AgreeStockOutRequest request,
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
        InventoryMedicareUploadSupport.uploadIfEnabled(inventoryUploadExtension, () -> {
            InventoryUploadAfterTransferCommand command = new InventoryUploadAfterTransferCommand()
                .setTransferBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setSupplyTypeCode(SupplyType.CABINET_STOCK_OUT.getCode())
                .setInvChgType(INV_CHG_TYPE_TRANSFER_OUT)
                .setContractBusNo(request.getContractBusNo());
            inventoryUploadExtension.uploadAfterTransferAgree(command);
        });
    }

    /**
     * 驳回单据并记录原因。

     *

     * @param request 含 busNo 与驳回原因

     * @return 是否驳回成功

     */
    public RejectStockOutResponse rejectStockOut(RejectStockOutRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getChangeReason()) && StringUtils.isBlank(request.getChangeReasonCode())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "驳回原因不能为空");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_SO_101, "用户未登录");
        // —— 领域驳回请求。——
        try {
            supplyRequestDomainService.rejectRequest(stockOutMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_SO_003);
        }
        // Asm：映射驳回响应
        return stockOutMapper.toRejectStockOutResponse(true);
    }

    /**
     * 删除单据（仅允许草稿/可删态）。

     *

     * @param request 含业务单号 busNo

     * @return 是否删除成功

     */
    public RemoveStockOutResponse removeStockOut(RemoveStockOutRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentPractitionerResolver.requireOperatorId(ErrorCodeConstants.WHS_SO_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删
            supplyRequestDomainService.removeByBusNo(stockOutMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_SO_005);
        }
        // Asm：映射删除响应
        return stockOutMapper.toRemoveStockOutResponse(true);
    }

    /**
     * 分页查询单据选品（库存目录可用量）。

     *

     * @param request 含库房、物品表与分页参数

     * @return 选品行与分页元数据

     */
    public PageProductsForStockOutResponse pageProductsForStockOut(PageProductsForStockOutRequest request) {
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_SO_001, "分页参数不能为空");
        if (request.getSourceLocationId() == null) {
            // Gate：分页窗口、机构与库房门禁
            // Pre：组装库存目录分页查询
            // Cmd：查询可用库存行
            // Asm：映射选品分页并增强价格
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "sourceLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_SO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertProductTableNameWhitelistForStockOutSelection(request.getProductTableName());
        // —— 查询：源库库存分页（物品表白名单），再批量拼产品定义、批号维进价 LOT_NUMBER_COST 与目录槽。——
        InventoryItemPageQuery invQuery = new InventoryItemPageQuery();
        invQuery.setLocationId(request.getSourceLocationId());
        invQuery.setItemTable(request.getProductTableName().trim());
        PageQueryBinder.bind(request, invQuery);
        Pageable pageable = PageRequest.of(
            request.springPageIndex(), request.getBoundedPageSize(), Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        try {
            Page<InventoryItemModel> page = inventoryItemQueryService.queryInventoryItemsByPage(invQuery, pageable);
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null || inv.getItemId() == null || StringUtils.isBlank(inv.getItemTable())) {
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
                    products = productDefinitionBatchLoader.loadAsMap(lookups);
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
                this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
            List<ProductDefinitionItemDto> records = new ArrayList<>();
            for (InventoryItemModel inv : page.getContent()) {
                if (inv == null) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(inv.getItemTable(), inv.getItemId());
                ProductDefinitionDisplayDto pr = products.get(key);
                ProductDefinitionItemDto dto = stockOutMapper.toProductDefinitionItemDtoFromInventory(inv, pr);
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
                if (StringUtils.isNotBlank(inv.getLotNumber())) {
                    lotCostUnit = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                        inv.getItemTable(), inv.getItemId(), inv.getLotNumber().trim());
                }
                dto.setPurchaseUnitPrice(lotCostUnit);
                ProductDefinitionCatalogEditorHintsDto hints =
                    ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                if (hints != null) {
                    List<ProductDefinitionCatalogUnitBandDto> bands = hints.getUnitBands();
                    dto.setUnitBands(bands);
                    BigDecimal packageAnchor = lotCostUnit;
                    if (packageAnchor == null && StringUtils.isNotBlank(inv.getItemTable()) && inv.getItemId() != null) {
                        packageAnchor = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                            inv.getItemTable().trim(), inv.getItemId());
                    }
                    ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                        bands, pr, packageAnchor);
                }
                records.add(dto);
            }
            // —— B.10.11：行已映射为 DTO，分页元数据仍取自同一 Spring Page（禁止手写 getNumber()+1）。——
            return PagedResultBuilder.build(new PageProductsForStockOutResponse(), records, page);
        } catch (RuntimeException ex) {
            log.warn("pageProductsForStockOut failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_SO_102, "出库选品查询失败");
        }
    }

    /**
     * 分页查询可关联来源单据（已同意态供引用）。

     *

     * @param request 含来源位置与分页参数

     * @return 可关联单据行

     */
    public PageLinkableRequisitionsResponse pageLinkableRequisitions(PageLinkableRequisitionsRequest request) {
        if (request == null || request.getSourceLocationId() == null) {
            // Gate：来源位置与分页窗口
            // Pre：组装可关联来源单查询
            // Cmd：分页查询已同意来源单
            // Asm：映射分页响应
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "sourceLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_SO_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_SO_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(request.getSourceLocationId(), allowed);
        // —— 读模型：请领行锚定「目的药库 = 当前出库源药库」，由投影器维护与供应事件一致。——
        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.PHARMACY_REQUISITION.getCode());
        query.setPurposeLocationId(request.getSourceLocationId());
        PageQueryBinder.bind(request, query);
        query.setApplyTimeStart(request.getStartDate());
        query.setApplyTimeEnd(request.getEndDate());
        try {
            Page<WarehouseDocumentDetailQueryModel> page = supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            return stockOutMapper.toPageLinkableRequisitionsResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageLinkableRequisitions failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_SO_102, "可关联请领单查询失败");
        }
    }

    private void enrichStockOutListDisplayNames(List<StockOutListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (StockOutListRowDto row : rows) {
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
            if (pr != null && StringUtils.isNotBlank(pr.getName())) {
                practitionerNameById.put(pid, pr.getName().trim());
            }
        }
        for (StockOutListRowDto row : rows) {
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

    private void enrichStockOutDetailProductDisplay(
        List<StockOutDetailLineDto> lines,
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
        for (StockOutDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isBlank(dto.getItemTable()) || dto.getItemId() == null) {
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
                this.productDefinitionSelectionTerminologySupport.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    private void enrichStockOutListPurposeLocationNames(List<StockOutListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        // —— 目的药房 id 去重后批量查名，避免列表对 location 逐行点查。——
        Set<Long> purposeIds = new HashSet<>();
        for (StockOutListRowDto row : rows) {
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
            if (StringUtils.isNotBlank(loc.getName())) {
                nameById.put(pid, loc.getName().trim());
            }
        }
        // —— 回填列表展示列：未在 nameById 命中时保持 null。——
        for (StockOutListRowDto row : rows) {
            if (row == null || row.getPurposeLocationId() == null) {
                continue;
            }
            row.setPurposeLocationName(nameById.get(row.getPurposeLocationId()));
        }
    }

    private void enrichStockOutListStatusEnumText(List<StockOutListRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (StockOutListRowDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getStatusEnum())) {
                continue;
            }
            // —— 列表行 statusEnum 为持久化 code：解析失败则展示文案留空。——
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
            if (st != null) {
                row.setStatusEnumText(st.getDisplay());
            }
        }
    }

    private void enrichStockOutDetailLines(List<StockOutDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (StockOutDetailLineDto dto : lines) {
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
     * 新建/编辑入口位置门控：源须为岗位可见且启用中的药库，目的须为岗位可见且启用中的药房。
     *
     * @param sourceLocationId  源药库主键
     * @param purposeLocationId 目的药房主键
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_001} 位置越权或形态非法
     */
    private void assertStockOutLocationGate(Long sourceLocationId, Long purposeLocationId) {
        Set<Long> warehouses = this.resolveGatedWarehouseLocationIdsForWarehouseGate();
        this.assertWarehouseLocationAllowed(sourceLocationId, warehouses);
        Set<Long> pharmacies = this.resolveGatedPharmacyLocationIdsForPharmacyGate();
        this.assertPharmacyLocationAllowed(purposeLocationId, pharmacies);
    }

    private Set<Long> resolveGatedWarehouseLocationIdsForWarehouseGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    /**
     * 解析当前用户可见的启用中药房 id 集合（策略同源 {@link #resolveGatedWarehouseLocationIdsForWarehouseGate}）。
     *
     * @return 药房位置主键集合，可能为空
     */
    private Set<Long> resolveGatedPharmacyLocationIdsForPharmacyGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActivePharmacies());
    }

    /**
     * 断言给定药库主键落在门控集合且为启用中药库形态。
     *
     * @param locationId 待校验库位
     * @param allowed    门控白名单
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_001} 越权或形态非法
     */
    private void assertWarehouseLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_SO_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "药库位置不在当前用户可选药库范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.WAREHOUSE.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "源位置须为启用中的药库");
        }
    }

    /**
     * 断言给定药房主键落在门控集合且为启用中药房形态。
     *
     * @param locationId 待校验库位
     * @param allowed    门控白名单
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_001} 越权或形态非法
     */
    private void assertPharmacyLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_SO_001, "目的药房不在当前用户可选药房范围");
        if (locationId == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "目的药房不在当前用户可选药房范围");
        }
        Optional<Location> locOpt = locationQueryService.findOptionalById(locationId);
        if (locOpt.isEmpty()
            || !LocationForm.PHARMACY.getCode().equals(locOpt.get().getFormEnum())
            || !LocationStatus.ACTIVE.getCode().equals(locOpt.get().getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "目的位置须为启用中的药房");
        }
    }

    /**
     * 库房出库单制单人（{@code wkf_supply_request.applicant_id}）：取当前登录用户绑定医务人员主键；
     * 与命令 {@code operatorId}（用户主键、库存与审计侧操作人）区分，不经请求体代传。
     *
     * @param notLoggedInErrorCode   未登录或缺用户主键时错误码
     * @param notLoggedInMessage     未登录提示
     * @param noPractitionerMessage  已登录但未绑定医务人员时的提示
     * @return 非空医务人员主键
     * @throws BusinessException 无登录用户或未绑定医务人员
     */
    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        return this.currentPractitionerResolver.requirePractitionerId(notLoggedInErrorCode, noPractitionerMessage);
    }

    /**
     * 出库明细行须携带制单时间 {@code occurrence_time}（由前端传入）；草稿阶段不写申请时间。
     */
    private void assertStockOutLinesOccurrencePresent(List<StockOutLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (StockOutLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库明细制单时间不能为空");
            }
        }
    }

    /**
     * 业务单号非空门禁（多处写接口复用）。
     *
     * @param busNo 业务单号
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_001} 空白
     */
    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "busNo 不能为空");
        }
    }

    /**
     * 写路径前置：按单号加载供应行；无数据视为出库单不存在，再断言类型为药库出库。
     *
     * @param busNo 业务单号，须已由 {@link #gateBusNo(String)} 校验非空
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_103} 不存在或类型非出库
     */
    private void preAssertCabinetStockOut(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_103, "出库单不存在");
        }
        this.assertCabinetStockOut(lines);
    }

    /**
     * 断言供应请求行集合均为药库出库类型（读模型/写模型混用路径的公共守卫）。
     *
     * @param lines 行集合，可为 null
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_103} 存在非出库类型行
     */
    private void assertCabinetStockOut(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_STOCK_OUT.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_103, "单据类型非药库出库单");
            }
        }
    }

    /**
     * 列表筛选可选校验：若传入 {@code typeEnum}，则必须为 {@link SupplyType#CABINET_STOCK_OUT}（防前端误传其它供应类型）。
     *
     * @param typeEnumCode 请求筛选类型 code，可 null 或空白
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_001} 非法类型
     */
    private void validateOptionalStockOutTypeEnum(String typeEnumCode) {
        if (StringUtils.isBlank(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "typeEnum 非法");
        }
        if (declared != SupplyType.CABINET_STOCK_OUT) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "typeEnum 必须为药库出库单类型");
        }
    }

    /**
     * 校验 {@code originalBusNo} 对应的药房请领单存在、已同意，且至少一行目的药库与当前出库源药库一致。
     *
     * @param originalBusNo    请领业务单号，须非空白
     * @param sourceWarehouseId 当前出库源药库主键
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_002} 引用不合法
     */
    private void validateOriginalPharmacyRequisitionForStockOut(String originalBusNo, Long sourceWarehouseId) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> reqLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (reqLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_002, "来源请领单不存在");
        }
        boolean matchedPurpose = false;
        for (SupplyRequestModel line : reqLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.PHARMACY_REQUISITION.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_002, "来源单据类型非药房请领单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_002, "来源请领单未同意，不可引用");
            }
            if (Objects.equals(sourceWarehouseId, line.getPurposeLocationId())) {
                matchedPurpose = true;
            }
        }
        if (!matchedPurpose) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_002, "来源请领单与当前源药库不匹配");
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
     * @param sourceLocationId 出库来源药库
     * @param lines            草稿明细
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_001} 字段缺失或可用量不足
     */
    private void assertStockOutDraftLinesWithinSourceAvailableInventory(Long sourceLocationId, List<StockOutLineDto> lines) {
        if (sourceLocationId == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<Long> locationIds = List.of(sourceLocationId);
        // —— 阶段 A：带 inventoryItemId —— 行级门禁，避免仅靠批号字符串聚合时把可用量算成 0 或算错批。
        Map<Long, BigDecimal> requestedByInventoryItemId = new LinkedHashMap<>();
        Map<Long, StockOutLineDto> sampleLineByInventoryItemId = new LinkedHashMap<>();
        for (StockOutLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() == null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.WHS_SO_001, "出库明细数量须为正数");
            Long invId = line.getInventoryItemId();
            // —— 同一库存行多明细：出库拆零数量先累加再与该行可用量比（与入库阶段 A 对称）。——
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
                StockOutLineDto sample = sampleLineByInventoryItemId.get(invId);
                if (row == null || sample == null) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_001,
                        "所选库存行当前不可用、已锁定或已过期，请刷新选品后再试");
                }
                if (!Objects.equals(row.getLocationId(), sourceLocationId)) {
                    throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "所选库存行与当前源药库不一致");
                }
                String rowTable = "";
                if (row.getItemTable() != null) {
                    rowTable = row.getItemTable().trim();
                }
                if (!rowTable.equals(sample.getItemTable().trim())) {
                    throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "所选库存行物品表与明细不一致");
                }
                if (!Objects.equals(row.getItemId(), sample.getItemId())) {
                    throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "所选库存行物品主键与明细不一致");
                }
                String lineLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(sample.getLotNumber());
                if (!lineLotKey.isEmpty()) {
                    // —— 明细声明批号则必须与库存行归一化批号一致，防止串批出库。——
                    String rowLotKey = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(row.getLotNumber());
                    if (!lineLotKey.equals(rowLotKey)) {
                        throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "明细批号与所选库存行批号不一致");
                    }
                }
                BigDecimal available = row.getQuantity();
                if (available == null) {
                    available = BigDecimal.ZERO;
                }
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_001,
                        "源库该库存行可用量不足，请减少出库数量或刷新库存后再试");
                }
            }
        }
        // —— 阶段 B：无 inventoryItemId —— 在固定来源库位 + 物品表下用「物品 id → 归一化批号」二维 Map 聚合，与 DM 四列唯一一致。
        Map<String, Map<Long, Map<String, BigDecimal>>> requestedByLotByTable = new HashMap<>();
        Map<String, Map<Long, BigDecimal>> requestedWithoutLotByTable = new HashMap<>();
        for (StockOutLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getInventoryItemId() != null) {
                continue;
            }
            if (line.getItemId() == null || StringUtils.isBlank(line.getItemTable())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库明细须包含有效物品表与主键");
            }
            if (StringUtils.isBlank(line.getUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "出库明细单位不能为空");
            }
            String table = line.getItemTable().trim();
            Long itemId = line.getItemId();
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireMedicationOrDeviceTableAndPositiveSplitQuantity(
                table, line.getItemQuantity(), ErrorCodeConstants.WHS_SO_001, "出库明细数量须为正数");
            String lotKeyPart = InventoryItemQueryService.normalizeLotNumberForAvailabilityMatch(line.getLotNumber());
            if (lotKeyPart.isEmpty()) {
                // 出库行未约束到具体批号：用该物品在来源库全部可用批之和做上限（与入库阶段 B「无批号」语义一致）。
                Map<Long, BigDecimal> perItem =
                    requestedWithoutLotByTable.computeIfAbsent(table, t -> new HashMap<>());
                perItem.merge(itemId, splitQty, BigDecimal::add);
            } else {
                // 出库行约束到批号：在固定库位 + 表名下按物品 id 与归一化批号累加请求量。
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
                            ErrorCodeConstants.WHS_SO_001,
                            "源库该批号可用量不足，请减少出库数量或刷新库存后再试");
                    }
                }
            }
            // —— 无批号维请求：与按品纵向汇总的可用总量比（允许多批混扣）。——
            for (Map.Entry<Long, BigDecimal> want : wantedWithoutLot.entrySet()) {
                BigDecimal requested = want.getValue();
                BigDecimal available = availableByItemId.getOrDefault(want.getKey(), BigDecimal.ZERO);
                if (requested.compareTo(available) > 0) {
                    throw new BusinessException(
                        ErrorCodeConstants.WHS_SO_001,
                        "源库可用量不足，请减少出库数量或刷新库存后再试");
                }
            }
        }
    }

    /**
     * 读取同号出库单首行状态枚举（提交/撤回后用于响应装配；无行或状态码空白时返回 null）。
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
        if (StringUtils.isBlank(statusCode)) {
            return null;
        }
        return EnumUtil.fromCode(SupplyStatus.class, statusCode.trim());
    }

    /**
     * 新建/编辑草稿：追溯码总串与逗号分段长度宽限校验（与进货同源常量 {@link GeneralSharedConstants#TRACE_NO_WIDE_MAX_LENGTH}）。
     *
     * @param lines     出库明细，可 null
     * @param errorCode 失败时业务错误码（新建与修订可区分）
     * @throws BusinessException 超长
     */
    private void assertStockOutTraceNosWithinWideLimit(List<StockOutLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (StockOutLineDto line : lines) {
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

    /**
     * 将批号规范为库存五元组匹配用字符串（空批号用空串与领域查询约定一致）。
     *
     * @param lotNumber 原始批号，可 null
     * @return trim 后字符串；空白则为空串
     */
    private String resolveLotNumberForStockOutInventory(String lotNumber) {
        if (StringUtils.isBlank(lotNumber)) {
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
     * @throws BusinessException {@link ErrorCodeConstants#WHS_SO_104} 产品定义缺失
     */
    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || StringUtils.isBlank(line.getItemTable()) || line.getItemId() == null) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isBlank(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_104, "产品定义缺失，无法解析展示名称");
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
        if (line != null && StringUtils.isNotBlank(line.getSourceTypeEnum())) {
            return line.getSourceTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotBlank(head.getSourceTypeEnum())) {
            return head.getSourceTypeEnum().trim();
        }
        return SupplyRequestType.CABINET.getCode();
    }

    private void assertProductTableNameWhitelistForStockOutSelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "productTableName 不能为空");
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.WHS_SO_001, "productTableName 非法");
        }
    }

    /**
     * 供应工作流领域异常到出库接口错误码的归一映射（非法参数类错误回落到接口默认码）。
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
     * @return 非法参数类：按文案含「已同意」映射为 {@link ErrorCodeConstants#WHS_SO_104}，否则为 {@link ErrorCodeConstants#WHS_SO_003}；其它错误码原样返回 {@code ex}
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.WHS_SO_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_SO_003, msg);
        }
        return ex;
    }

    /**
     * 同意出库编排内部结构：承载目的侧入账所需字段（在源库合并扣减之后逐条消费）。
     */
    private static final class StockOutDestIncr {
        private SupplyRequestModel line;
        private Long purposeLocationId;
        private BigDecimal splitQty;
        private String resolvedLot;
        private String productName;
        private String splitUnitCode;
    /** 目的侧新建库存行时冗余分类/检索码；与 agree 循环内批量读模型快照一致。 */
        private ProductDefinitionDisplayDto productDefinition;
    /** 源库命中行上的供应商标识，用于目的库首次建账 {@code supplier_id}（四元组定位不收窄供应商）。 */
        private Long sourceInventorySupplierId;
    /** 源库命中行生产日期，目的库新建时供应行未带 {@code start_time} 则承袭。 */
        private LocalDateTime sourceInventoryProductionDate;
    /** 源库命中行效期，目的库新建时供应行未带 {@code end_time} 则承袭。 */
        private LocalDateTime sourceInventoryExpirationDate;
    }
}
