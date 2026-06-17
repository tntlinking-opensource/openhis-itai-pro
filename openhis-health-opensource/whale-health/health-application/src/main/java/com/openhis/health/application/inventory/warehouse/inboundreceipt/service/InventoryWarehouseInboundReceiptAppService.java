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

package com.openhis.health.application.inventory.warehouse.inboundreceipt.service;

import com.openhis.health.application.insurance.inventory.InventoryMedicareUploadSupport;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptDetailLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.ProductDefinitionItemDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptListRowDto;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.mapper.InventoryWarehouseInboundReceiptMapper;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.request.*;
import com.openhis.health.application.inventory.warehouse.inboundreceipt.response.*;
import com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.module.common.validator.CurrentOperatorResolver;
import com.openhis.health.module.common.validator.PageWindowGate;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierDetailQueryModel;
import com.openhis.health.domain.custom.supplier.queries.model.SupplierListItemQueryModel;
import com.openhis.health.domain.custom.supplier.service.SupplierQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogEditorHintsAssembler;
import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogEditorHintsDto;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.custom.tracenumberrecord.command.AppendTraceRecordsCommand;
import com.openhis.health.domain.custom.tracenumberrecord.service.TraceNumberRecordDomainService;
import com.openhis.health.module.querysupport.administration.PractitionerRoleScopeLoader;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.module.querysupport.product.ProductDefinitionBatchLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionPurchasePageLoader;
import com.openhis.health.module.querysupport.product.ProductDefinitionSelectionTerminologySupport;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionSelectionTerminologyMaps;
import com.openhis.health.module.querysupport.product.ProductDefinitionPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.WarehouseDocumentDetailQueryModel;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.module.common.pagination.PageQueryBinder;
import com.openhis.health.domain.share.enums.SupplierStatus;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.health.domain.share.enums.SupplyRequestType;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.health.domain.share.enums.TraceNumberRecordOperationType;
import com.openhis.health.domain.share.enums.SupplyStatus;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemDomainService;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService;
import com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.VerifyAbleEditQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestValidationModel;
import com.openhis.health.domain.workflow.supplyrequest.support.SupplyPersistedSplitQuantity;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.chs.jilin.port.InventoryInsurancePort;
import com.openhis.whale.chs.jilin.port.model.UploadAfterInboundCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.commons.lang3.StringUtils;
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
import java.util.Optional;
import java.util.Set;

/**
 * 药库进货单（{@link SupplyType#CABINET_INBOUND_RECEIPT}）应用编排。
 *
 * <p>审批链维护供应请求；<strong>同意</strong>后依次：领域落已同意态 → 入库发放（SupplyDelivery）→ 库存入账（InventoryItem），
 * 并可补批号维进价/售价、追溯码。撤回/驳回不涉及库存侧写。</p>
 * <p>列表/门禁以 {@code purpose_location_id}（目的药库）为锚点；类型/状态等枚举入参使用 {@code getCode()} 下推领域。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryWarehouseInboundReceiptAppService {

    @Autowired(required = false)
    private InventoryInsurancePort inventoryInsurancePort;

    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final CurrentOperatorResolver currentOperatorResolver;
    private final PageWindowGate pageWindowGate;
    private final SupplyRequestDomainService supplyRequestDomainService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final SupplyDeliveryDomainService supplyDeliveryDomainService;
    private final InventoryItemDomainService inventoryItemDomainService;
    private final InventoryItemQueryService inventoryItemQueryService;
    private final LocationQueryService locationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final PractitionerRoleScopeLoader practitionerRoleScopeLoader;
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;
    private final SupplierQueryService supplierQueryService;
    private final ProductDefinitionBatchLoader productDefinitionBatchLoader;
    private final ProductDefinitionPurchasePageLoader productDefinitionPurchasePageLoader;
    private final ProductDefinitionSelectionTerminologySupport productDefinitionSelectionTerminologySupport;
    private final InventoryWarehouseInboundReceiptMapper inboundReceiptMapper;
    private final SupplyDeliveryQueryService supplyDeliveryQueryService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final TraceNumberRecordDomainService traceNumberRecordDomainService;
    /**
     * 加载库房单据页主数据（管辖药库、枚举选项、制单人员等）。
     *
     * @param request 页初始化请求；机构上下文由服务端解析
     * @return 下拉选项与字典项
     */
    public LoadInboundReceiptFormInitResponse loadInboundReceiptFormInit(LoadInboundReceiptFormInitRequest request) {
        // —— 经手人/库管等仍按会话机构收窄；药房与药库位置经 ICurrentUser#getManagedLocationIds 收窄（empty 时不收窄）。——
        Long resolvedOrg = managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        try {
            // Gate：机构会话上下文
            // Pre：装载管辖范围内启用位置与角色人员
            // Asm：Mapper 映射页初始化响应
            // —— 门控：管辖库位非空时与启用位置求交；管辖 empty 时不拼 location_id 谓词。——
            List<Location> pharmacies = locationQueryService.listAllActivePharmacies();
            List<Location> warehouses = locationQueryService.listAllActiveWarehouses();
            List<Location> gatedPharmacies = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(pharmacies);
            List<Location> gatedWarehouses = this.managedLocationIdsResolver.narrowActiveLocationsByManaged(warehouses);
            List<SupplierListItemQueryModel> suppliers =
                supplierQueryService.listSuppliersByTypeAndActive(
                    com.openhis.health.domain.share.enums.SupplierType.SUPPLIER.getCode());
            List<Practitioner> pharmacists = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_PHARMACIST, resolvedOrg);
            List<Practitioner> warehouseKeepers = this.practitionerRoleScopeLoader.listActivePractitionersForPlatformRoleInOrganization(
                WhalePlatformRoleCodeConstants.ROLE_WAREHOUSE, resolvedOrg);
            return inboundReceiptMapper.toLoadInboundReceiptFormInitResponse(
                inboundReceiptMapper.toOptionDtoListFromLocations(gatedPharmacies),
                inboundReceiptMapper.toOptionDtoListFromLocations(gatedWarehouses),
                inboundReceiptMapper.toOptionDtoListFromSuppliers(suppliers),
                inboundReceiptMapper.toOptionDtoListFromPractitioners(pharmacists),
                inboundReceiptMapper.toOptionDtoListFromPractitioners(warehouseKeepers),
                inboundReceiptMapper.toOptionDtoListInboundItemTableTypes(),
                inboundReceiptMapper.toOptionDtoListSupplyStatuses());
        } catch (RuntimeException ex) {
            log.warn("loadInboundReceiptFormInit failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "进货页主数据加载失败");
        }
    }

    /**
     *
     * @param request 查询条件；须含 {@code sourceLocationId}、{@code pageNo}、{@code pageSize}；可选 {@code statusEnum}、{@code typeEnum}（须为药库进货 code）、时段与关键字
     * @return 分页结果（{@code pageNo} 1-based，行内状态/类型等为持久化 code，B.10.11 / B.10.8.1）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 分页/位置参数非法、药库越权、{@code typeEnum} 非进货类型；
     *                            {@link ErrorCodeConstants#WHS_IN_101} 机构上下文缺失；{@link ErrorCodeConstants#WHS_IN_102} 列表聚合查询失败
     * @throws IllegalArgumentException {@code statusEnum} 非空但非法持久化 code 时由 {@link EnumUtil#fromCode} 抛出
     */
    public PageInboundReceiptsResponse pageInboundReceipts(PageInboundReceiptsRequest request) {
        // —— 门面入参硬校验：避免 null 穿透到领域查询构造，产生难以定位的 NPE 或空结果误判。——
        // Gate：请求体、分页窗口与机构上下文
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        // Pre：组装列表查询与库房门禁
        if (request.getSourceLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "sourceLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_IN_001, "分页参数不能为空");
        // —— 可选类型筛选项须解析为药库进货单，防止借列表接口窥探其它供应类型。——
        this.validateOptionalInboundReceiptTypeEnum(request.getTypeEnum());
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        if (request.getPurposeLocationId() != null) {
            // —— 可选按目的库再收窄列表：若传则必须与源库同属当前用户可见药库集合，防横向数据窥探。——
            this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        }

        SupplyRequestDocumentListQuery listQuery = new SupplyRequestDocumentListQuery();
        listQuery.setTypeEnum(SupplyType.CABINET_INBOUND_RECEIPT.getCode());
        listQuery.setSourceLocationId(request.getSourceLocationId());
        if (request.getPurposeLocationId() != null) {
            listQuery.setPurposeLocationId(request.getPurposeLocationId());
        }
        // —— 状态筛选为可选：仅当传入非空白 code 时才写入查询对象，否则列表不按状态谓词收窄。——
        if (StringUtils.isNotBlank(request.getStatusEnum())) {
            // —— 入参与库表一致：SupplyStatus 持久化 getCode()，禁止排序号中转（B.10.8.1）。——
            SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, request.getStatusEnum().trim());
            if (st == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "statusEnum 非法");
            }
            listQuery.setStatusEnum(st.getCode());
        }
        PageQueryBinder.bind(request, listQuery);
        // —— 申请时刻窗：兼容历史请求字段 applyTimeFrom/To 与 AbstractPageQuery 的 startDate/endDate（B.10.11）。——
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
            // Cmd：领域分页查询供应请求
            long mergedTotal = this.supplyRequestQueryService.countDistinctBusNo(listQuery);
            List<String> busNos = this.supplyRequestQueryService.listDistinctBusNoPage(listQuery);
            List<SupplyRequestModel> lines = this.supplyRequestQueryService.listByBusNosWithFilter(listQuery, busNos);
            List<InboundReceiptListRowDto> records = this.inboundReceiptMapper.toInboundReceiptListAggregated(busNos, lines);
            // —— 展示名依赖主数据查询，放在应用层批量解析，避免 Mapper 注入领域服务（B.2 / B.6.1）。——
            this.enrichInboundReceiptListDisplayNames(records, lines);
            int pageNo = request.getPageNo().intValue();
            int pageSize = listQuery.getBoundedPageSize();
            // Asm：聚合 busNo 行并映射分页响应
            return this.inboundReceiptMapper.toPageInboundReceiptsAggregatedResponse(mergedTotal, pageNo, pageSize, records);
        } catch (RuntimeException ex) {
            log.warn("pageInboundReceipts failed (wkf_supply_request aggregate path)", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "进货单列表查询失败");
        }
    }

    /**
     *
     * @param request 请求；{@code busNo} 非空（首尾空白会被去除）
     * @return 详情；头行中 {@code typeEnum}/{@code statusEnum} 等为持久化 {@code getCode()} 字符串（B.10.8.1）；产品名由读模型批量解析，失败时降级为空名
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 单号缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_103} 单据不存在或类型非药库进货单
     */
    public GetInboundReceiptDetailResponse getInboundReceiptDetail(GetInboundReceiptDetailRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            // Gate：busNo 非空
            // Pre：按 busNo 回读供应请求行
            // Pre：批量装载产品读模型
            // Asm：Mapper 映射详情并增强展示字段
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        // —— 按业务单号拉取同号全部供应请求行（写模型）：详情页与列表共用 wkf_supply_request 聚合视图。——
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(lines);

        // —— 按物品表+主键去重后一次性批量拉取产品定义，避免详情页对每行单独访问读模型（与 A.9.1「集合化查询」同向收敛）。——
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
            // —— 读模型偶发失败时降级为空映射：详情仍返回写模型字段，展示名/规格等由前端自行处理缺失态。——
            log.warn("batchGetProductByIds degraded for inbound receipt detail", ex);
            products = new HashMap<>();
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
        GetInboundReceiptDetailResponse res = inboundReceiptMapper.toGetInboundReceiptDetailResponse(lines, products);
        this.enrichInboundReceiptDetailProductDisplay(res.getLines(), products, terminologyMaps);
        this.enrichInboundReceiptDetailLines(res.getLines());
        return res;
    }

    /**
     * 进货详情：补齐行单位展示名与 catalog 单位槽 unitName（耗材等须走 UNIT_CODE 术语，B.10.8.1）。
     */
    private void enrichInboundReceiptDetailProductDisplay(
        List<InboundReceiptDetailLineDto> lines,
        Map<String, ProductDefinitionDisplayDto> products,
        ProductDefinitionSelectionTerminologyMaps terminologyMaps
    ) {
        if (lines == null || lines.isEmpty() || products == null || products.isEmpty()) {
            return;
        }
        Map<String, String> unitLabelByCode = null;
        if (terminologyMaps != null) {
            unitLabelByCode = terminologyMaps.getUnitLabelByCode();
        }
        for (InboundReceiptDetailLineDto dto : lines) {
            if (dto == null || StringUtils.isBlank(dto.getItemTable()) || dto.getItemId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(dto.getItemTable().trim(), dto.getItemId());
            ProductDefinitionDisplayDto pr = products.get(key);
            if (pr == null) {
                continue;
            }
            dto.setUnitCodeDictText(ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayName(
                unitLabelByCode, dto.getUnitCode(), pr));
            ProductDefinitionCatalogEditorHintsDto catalog = dto.getProductCatalog();
            if (catalog == null) {
                catalog = ProductDefinitionCatalogEditorHintsAssembler.fromDisplayDto(pr, terminologyMaps);
                dto.setProductCatalog(catalog);
            } else if (catalog.getUnitBands() != null && !catalog.getUnitBands().isEmpty()) {
                this.productDefinitionSelectionTerminologySupport.applyInboundUnitBandDisplayNames(
                    catalog.getUnitBands(), pr, terminologyMaps);
            }
        }
    }

    /**
     * 详情行槽价：目录 {@code PURCHASE}、批号 {@code LOT_NUMBER_COST} 原样落槽；{@code STOCK}/{@code SPLIT} 仅包装锚换算。
     *
     * @param lines 详情明细列表，可为 null
     */
    private void enrichInboundReceiptDetailLines(List<InboundReceiptDetailLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        for (InboundReceiptDetailLineDto dto : lines) {
            if (dto == null) {
                continue;
            }
            ProductDefinitionCatalogEditorHintsDto cat = dto.getProductCatalog();
            if (cat == null) {
                continue;
            }
            // —— 头行装配的 catalog：此处只补价与槽内单价，不改单位编码列。——
            // —— 目录 PURCHASE 为每包装进价；批号 LOT_NUMBER_COST 优先为包装锚。——
            BigDecimal purchase = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(
                dto.getItemTable(), dto.getItemId());
            cat.setPurchasePrice(purchase);
            List<ProductDefinitionCatalogUnitBandDto> bands = cat.getUnitBands();
            if (bands == null || bands.isEmpty()) {
                // —— 无槽列表则无法回填单价（仍保留 purchasePrice 展示列）。——
                continue;
            }
            BigDecimal lotPackagePrice = null;
            if (StringUtils.isNotBlank(dto.getLotNumber())) {
                lotPackagePrice = this.chargeItemDefinitionQueryService.resolveFirstLotNumberCostPriceValue(
                    dto.getItemTable(), dto.getItemId(), dto.getLotNumber().trim());
            }
            ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseDetailLineBandPrices(
                bands, null, purchase, lotPackagePrice, dto.getPrice(), dto.getUnitCode());
        }
    }

    /**
     *
     * @param request 请求体；须含非空明细、源/目的位置；位置类型由后端按库位推导。可选订货引用 {@code originalBusNo} 须通过订货门禁校验
     * @return 新单号与创建后状态（{@code statusEnum} 为 code，B.10.8.1）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数/位置/供应商不合法；{@link ErrorCodeConstants#WHS_IN_002} 来源订货单不可引用；
     *                            {@link ErrorCodeConstants#WHS_IN_101} 未登录或机构上下文缺失；Mapper 装配抛出 {@link IllegalArgumentException} 时包装为 {@code WHS_IN_001}
     */

    public CreateInboundReceiptResponse createInboundReceipt(CreateInboundReceiptRequest request) {
        // —— 入参形状：头字段 + 至少一行明细；源/目的库位必填（类型由 Mapper 按位置推导）。——
        // Gate：请求体、明细与库房/供应商门禁
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "源位置与目标位置不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        // —— 业务门禁：供应商启用态；追溯码片段长度上限（新建场景错误码 WHS_IN_001）。——
        this.validateSupplierOptional(request.getSupplierId());
        this.assertInboundReceiptTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_IN_001);

        // —— 可选订货引用：若填写则整单订货须已同意且类型为药库订货，防止引用草稿或错误类型单据。——
        String originalBusNo = request.getOriginalBusNo();
        if (StringUtils.isNotBlank(originalBusNo)) {
            this.validateOriginalPurchaseOrderForInboundReceipt(originalBusNo.trim());
        }

        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "经手人不能为空");
        }
        this.assertInboundReceiptLinesOccurrencePresent(request.getLines());

        // —— 领域写路径：Mapper 将 HTTP 装配为 CreateInboundReceiptCommand，领域服务负责取号与落多行。——
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");
        // —— 制单人（申请人/applicant_id）与会话用户绑定医务人员对齐，禁止沿用请求体代传（与列表按医务人员解析一致）。——
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        try {
            com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand cmd =
                inboundReceiptMapper.toCreateInboundReceiptCommand(request, operatorId, applicantId);
            String busNo = supplyRequestDomainService.createInboundReceipt(cmd);
            // Asm：返回 busNo
            return inboundReceiptMapper.toCreateInboundReceiptResponse(busNo);
        } catch (IllegalArgumentException ex) {
            // —— Mapper/枚举解析失败：统一包装为参数非法，避免把 IllegalArgumentException 原样透出 HTTP 层。——
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货参数不合法：" + ex.getMessage());
        }
    }

    /**
     *
     * @param request 请求体；须含 {@code busNo}、{@code rowVersion}、非空明细及与新建相同的头字段约束
     * @return 单号与修订后状态（{@code statusEnum} 为 code）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数非法或位置越权；{@link ErrorCodeConstants#WHS_IN_103} 单据不存在或类型不符；
     *                            {@link ErrorCodeConstants#WHS_IN_004} 当前状态不可编辑；{@link ErrorCodeConstants#WHS_IN_101} 未登录或机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_105} 并发冲突（乐观锁）；Mapper 装配 {@link IllegalArgumentException} 包装为 {@code WHS_IN_001}
     */
    
    public UpdateInboundReceiptResponse updateInboundReceipt(UpdateInboundReceiptRequest request) {
        // —— 与新建相同的头/明细非空约束，并额外要求 busNo + 乐观锁 rowVersion（待审核态全量替换语义）。——
        // Gate：busNo、明细与可编辑态校验
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货明细不能为空");
        }
        if (request.getSourceLocationId() == null || request.getPurposeLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "源位置与目标位置不能为空");
        }
        if (request.getRowVersion() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "rowVersion 不能为空");
        }

        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getSourceLocationId(), allowed);
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        // —— 目标单据须存在、类型为药库进货，且 verifyAbleEdit 为真（非待审核等状态直接拒绝）。——
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> existing = supplyRequestQueryService.getSupplyByBusNo(q);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(existing);

        VerifyAbleEditQuery editQuery = new VerifyAbleEditQuery();
        editQuery.setBusNo(request.getBusNo().trim());
        SupplyRequestModel head = existing.get(0);
        editQuery.setItemId(head.getItemId());
        Boolean editable = supplyRequestQueryService.verifyAbleEdit(editQuery);
        if (Boolean.FALSE.equals(editable)) {
            // —— 领域返回不可编辑：常见为已提交审批或已同意，禁止客户端强行覆盖明细。——
            throw new BusinessException(ErrorCodeConstants.WHS_IN_004, "当前状态不允许编辑进货单");
        }

        this.validateSupplierOptional(request.getSupplierId());
        this.assertInboundReceiptTraceNosWithinWideLimit(request.getLines(), ErrorCodeConstants.WHS_IN_004);

        if (request.getPractitionerId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "经手人不能为空");
        }
        this.assertInboundReceiptLinesOccurrencePresent(request.getLines());

        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法制单");
        // —— 修订须保留原订货引用快照：从存量首行读取，避免客户端篡改 originalBusNo 伪造来源。——
        String originalSnapshot = head.getOriginalBusNo();
        try {
            com.openhis.health.domain.workflow.supplyrequest.command.CreateInboundReceiptCommand cmd =
                inboundReceiptMapper.toCreateInboundReceiptCommandForUpdate(request, operatorId, applicantId, originalSnapshot);
            String busNo = supplyRequestDomainService.createInboundReceipt(cmd);
            // Asm：返回 busNo
            return inboundReceiptMapper.toUpdateInboundReceiptResponse(busNo);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货参数不合法：" + ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            // —— JPA 乐观锁冲突：提示用户刷新后重试，避免静默丢更新。——
            throw new BusinessException(ErrorCodeConstants.WHS_IN_105, "进货单已被并发修改，请刷新后重试", ex);
        }
    }

    /**
     *
     * @param request 请求；{@code busNo} 经 {@link #gateBusNo(String)} 校验
     * @return 是否成功及提交后头行状态 code（可能为空，见 Mapper 装配）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 请求或单号非法；{@link ErrorCodeConstants#WHS_IN_101} 未登录；
     *                            领域状态机拒绝时经 {@code WHS_IN_003} 映射（见 {@link #mapSupplyWorkflowException}）
     */
    
    public SubmitInboundReceiptApprovalResponse submitInboundReceiptApproval(SubmitInboundReceiptApprovalRequest request) {
        // Gate：busNo 与制单人员档案
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long applicantId = this.requireWarehousePreparerPractitionerId(
            ErrorCodeConstants.WHS_IN_101,
            "用户未登录",
            "当前用户未绑定医务人员档案，无法提交审批");
        // —— 提交审批仅驱动供应请求状态机，不涉及发放与库存；失败时映射为 WHS_IN_003 便于前端统一提示。——
        try {
            // Cmd：领域提交审批
            supplyRequestDomainService.submitApproval(
                inboundReceiptMapper.toSubmitApprovalCommand(request,
                    currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录"),
                    applicantId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_003);
        }
        // Asm：映射提交响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return inboundReceiptMapper.toSubmitInboundReceiptApprovalResponse(st);
    }

    
    /**
     * 撤回审批（回到可编辑草稿态）。
     *
     * @param request 含业务单号 busNo
     * @return 撤回后的展示状态
     */
    public WithdrawInboundReceiptApprovalResponse withdrawInboundReceiptApproval(WithdrawInboundReceiptApprovalRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        try {
            // Cmd：领域撤回审批
            supplyRequestDomainService.withdrawApproval(
                inboundReceiptMapper.toWithdrawApprovalCommand(request, currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录")));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_003);
        }
        // Asm：映射撤回响应
        SupplyStatus st = this.loadHeadSupplyStatus(request.getBusNo().trim());
        return inboundReceiptMapper.toWithdrawInboundReceiptApprovalResponse(st);
    }

    /** 同意：供应请求已同意 → 发放 → 库存/价表/追溯；含防重复同意与防重复完成态发放门禁。 */
    public AgreeInboundReceiptResponse agreeInboundReceipt(AgreeInboundReceiptRequest request) {
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");

        // —— 同意进货单 ——
        // —— 2. 同意前读校验：单据须存在、类型须为药库进货；读侧防重复同意（双保险，避免双入账）。——
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(request.getBusNo().trim());
        List<SupplyRequestModel> beforeLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (beforeLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(beforeLines);
        SupplyRequestValidationModel validation = supplyRequestQueryService.supplyRequestValidation(request.getBusNo().trim());
        if (Boolean.TRUE.equals(validation.getHasAgreed())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货单已同意，禁止重复同意");
        }

        // —— 3. 领域同意写：先落库供应请求状态为已同意，再编排发放与库存；领域须保证 save 在事件前（B.14）。——
        try {
            supplyRequestDomainService.agreeRequest(inboundReceiptMapper.toAgreeRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapAgreeException(ex);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_105, "进货单已被并发修改，请刷新后重试", ex);
        }

        // —— 同持久化单元内 flush：保证后续「同意态」回读与发放/库存编排读到与 agree 一致的数据库快照。——
        this.supplyRequestDomainService.flushPendingChanges();

        List<SupplyRequestModel> agreedLines = supplyRequestQueryService.getSupplyByBusNo(q);
        this.assertCabinetInboundReceipt(agreedLines);
        // —— 二次校验：防止读侧延迟或异常导致在错误状态下继续入库侧写。——
        for (SupplyRequestModel row : agreedLines) {
            if (row == null) {
                continue;
            }
            if (!SupplyStatus.AGREE.getCode().equals(row.getStatusEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "同意后回读状态异常，中止入库编排");
            }
            if (row.getId() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细缺少持久化主键，无法完成入库");
            }
            if (row.getItemQuantity() == null || row.getItemQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细数量无效，无法入账");
            }
        }

        // —— 收集本单全部供应请求行主键，批量查询是否已有完成态发放（禁止循环内逐条 exists）。——
        List<Long> requestIdsForDeliveryGate = new ArrayList<>();
        for (SupplyRequestModel row : agreedLines) {
            if (row != null && row.getId() != null) {
                requestIdsForDeliveryGate.add(row.getId());
            }
        }
        // —— 批量查询完成态发放，防重复同意入账。——
        Set<Long> duplicateCompletedRequestIds =
            supplyDeliveryQueryService.findRequestIdsHavingCompletedDelivery(requestIdsForDeliveryGate);
        if (!duplicateCompletedRequestIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "供应请求行已存在完成态发放，禁止重复同意入账");
        }

        // —— 4. 入库发放：记录与供应请求行关联的入库事实，须在库存增减之前落库（编排顺序）。——
        try {
            com.openhis.health.domain.workflow.supplydelivery.command.CreateSupplyDeliveryCommand deliveryCmd =
                inboundReceiptMapper.toCreateSupplyDeliveryCommandForInbound(agreedLines, operatorId);
            if (deliveryCmd.resolveItems().isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "入库发放明细为空，无法入账");
            }
            supplyDeliveryDomainService.createSupplyDelivery(deliveryCmd);
        } catch (BusinessException ex) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "入库发放落库失败：" + ex.getMessage());
        }

        // —— 5. 产品定义批量读：批号维定价换算与后续库存入账共用同一快照。——
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
                log.warn("batchGetProductByIds failed during inbound lot pricing assemble", ex);
            }
        }

        // —— 6. 批号维进价/售价补录：逐行按（物品表+物品 id+批号）探测是否已存在价线，已存在则跳过，避免重复插入。——
        SupplyRequestModel head = agreedLines.get(0);
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            if (StringUtils.isBlank(line.getItemTable()) || line.getItemId() == null) {
                continue;
            }
            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());
            ChargeItemDefinitionQueryCommand priceDedupQuery = new ChargeItemDefinitionQueryCommand();
            priceDedupQuery.setInstanceTable(line.getItemTable().trim());
            priceDedupQuery.setInstanceId(line.getItemId());
            priceDedupQuery.setLotNo(resolvedLot);
            if (chargeItemDefinitionQueryService.existsLotNumberCostOrLotNumberPriceForInstanceAndLot(priceDedupQuery)) {
                // —— 该实例与批号已维护过批号维成本/售价：不再重复写价表，防止同意路径多次执行时堆积重复价线。——
                continue;
            }
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable().trim(), line.getItemId()));
            BigDecimal priceVal = ProductDefinitionCatalogUnitBandsAssembler.resolveLotNumberPackageUnitPriceForInboundAgree(
                line.getPrice(), line.getUnitCode(), product);
            try {
                SavePriceLineCommand costCmd = new SavePriceLineCommand();
                costCmd.setOperatorId(operatorId);
                SavePriceLineCommand.Item costItem = new SavePriceLineCommand.Item();
                costItem.setInstanceTable(line.getItemTable().trim());
                costItem.setInstanceId(line.getItemId());
                costItem.setPriceTypeCode(PriceType.LOT_NUMBER_COST.getCode());
                costItem.setLotNo(resolvedLot);
                costItem.setPriceValue(priceVal);
                costCmd.setItem(costItem);
                chargeItemDefinitionDomainService.addLotPurchasePrice(costCmd);

                // —— LOT_NUMBER_PRICE 与进价同值落库，供门诊/收费下游；库房读路径不消费。——
                SavePriceLineCommand saleCmd = new SavePriceLineCommand();
                saleCmd.setOperatorId(operatorId);
                SavePriceLineCommand.Item saleItem = new SavePriceLineCommand.Item();
                saleItem.setInstanceTable(line.getItemTable().trim());
                saleItem.setInstanceId(line.getItemId());
                saleItem.setPriceTypeCode(PriceType.LOT_NUMBER_PRICE.getCode());
                saleItem.setLotNo(resolvedLot);
                saleItem.setPriceValue(priceVal);
                saleCmd.setItem(saleItem);
                chargeItemDefinitionDomainService.addLotSalePrice(saleCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "批号维定价补录失败：" + ex.getMessage());
            }
        }

        // —— 7. 逐行库存入账并拼装追溯子命令：同一循环内完成「账面变更 + 追溯项收集」，末尾再一次批量写追溯。——
        String busNoTrim = request.getBusNo().trim();
        String traceOperationType = TraceNumberRecordOperationType.supplyTypeCode(SupplyType.CABINET_INBOUND_RECEIPT);
        List<AppendTraceRecordsCommand.AppendTraceRecordItemCommand> traceItems = new ArrayList<>();
        for (SupplyRequestModel line : agreedLines) {
            if (line == null || line.getId() == null) {
                continue;
            }
            Long purposeLocationId = line.getPurposeLocationId();
            Long supplierId = line.getSupplierId();
            if (supplierId == null) {
                supplierId = head.getSupplierId();
            }
            if (supplierId == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "入库编排需要供应商信息，请先补全进货单头供应商");
            }
            line.setSupplierId(supplierId);

            String resolvedLot = this.resolveLotNumberForInventory(line.getLotNumber());
            String productName = this.resolveProductName(line, productByKey);
            ProductDefinitionDisplayDto product = productByKey.get(
                ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId()));
            BigDecimal splitQty = SupplyPersistedSplitQuantity.requireSupplyLineItemQuantityAsPersistedSplitUnit(
                line, ErrorCodeConstants.WHS_IN_104, "进货供应行 item_quantity 须为拆零正数量");
            if (product == null || StringUtils.isBlank(product.getSplitUnitCode())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "产品定义缺失或缺少拆零单位编码");
            }
            String splitUnitCode = product.getSplitUnitCode().trim();

            InventoryMatchKey key = new InventoryMatchKey();
            key.setSupplierId(supplierId);
            key.setLotNumber(resolvedLot);
            key.setLocationId(purposeLocationId);
            key.setItemTable(line.getItemTable());
            key.setItemId(line.getItemId());

            Long existingInventoryId = inventoryItemQueryService.findFirstIdByInventoryMatchKey(key).orElse(null);
            try {
                if (existingInventoryId != null) {
                    com.openhis.health.domain.workflow.inventoryitem.command.IncreaseQuantityCommand incCmd =
                        inboundReceiptMapper.toIncreaseQuantityCommand(existingInventoryId, splitQty, operatorId);
                    inventoryItemDomainService.increaseQuantity(incCmd);
                } else {
                    com.openhis.health.domain.workflow.inventoryitem.command.CreateInventoryItemCommand createCmd =
                        inboundReceiptMapper.toCreateInventoryItemCommand(
                            line, purposeLocationId, productName, resolvedLot, splitQty, splitUnitCode, operatorId, product);
                    inventoryItemDomainService.createInventoryItem(createCmd);
                }
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "库存入账失败：" + ex.getMessage());
            }

            if (StringUtils.isNotBlank(line.getTraceNo())) {
                // —— 追溯码允许多个片段：逗号分隔，逐片段生成一条 append 子命令（片段内 trim，空片段丢弃）。——
                String lt = this.resolvePurposeLocationTypeCode(line, head);
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
                    it.setLocationId(purposeLocationId);
                    it.setLocationStoreId(null);
                    it.setLotNumber(resolvedLot);
                    it.setTraceNo(traceNo);
                    it.setStatusEnum(TraceDirection.IN.getCode());
                    it.setUnitCode(splitUnitCode);
                    it.setOperationType(traceOperationType);
                    it.setBusNo(busNoTrim);
                    traceItems.add(it);
                }
            }
        }

        // —— 8. 追溯码批量落库：仅当本请求至少有一条非空 traceNo 时才调用领域服务，避免空命令噪声。——
        if (!traceItems.isEmpty()) {
            try {
                AppendTraceRecordsCommand traceCmd = new AppendTraceRecordsCommand();
                traceCmd.setOperatorId(operatorId);
                traceCmd.setItems(traceItems);
                traceNumberRecordDomainService.appendTraceRecords(traceCmd);
            } catch (BusinessException ex) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "追溯码入库记录失败：" + ex.getMessage());
            }
        }
        tryUploadMedicareInventoryAfterInboundAgree(request, agreedLines);
        return inboundReceiptMapper.toAgreeInboundReceiptResponse(true);
    }

    private void tryUploadMedicareInventoryAfterInboundAgree(
        AgreeInboundReceiptRequest request,
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
        InventoryMedicareUploadSupport.uploadIfPortPresent(inventoryInsurancePort, () -> {
            UploadAfterInboundCommand command = new UploadAfterInboundCommand()
                .setInboundBusNo(request.getBusNo())
                .setSupplyRequestLineIds(lineIds)
                .setContractBusNo(request.getContractBusNo());
            inventoryInsurancePort.uploadAfterInboundAgree(command);
        });
    }

    /**
     *
     * @param request 请求；{@code busNo}、{@code rejectReason} 均须非空白；{@code approverId} 可空，空时回落为当前操作人
     * @return 驳回落库成功时 {@code success=true}
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数非法；{@link ErrorCodeConstants#WHS_IN_101} 未登录；
     *                            领域拒绝时 {@code WHS_IN_003} 映射
     */
    
    public RejectInboundReceiptResponse rejectInboundReceipt(RejectInboundReceiptRequest request) {
        // Gate：busNo、驳回原因与操作人
        if (request == null || StringUtils.isBlank(request.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
        if (StringUtils.isBlank(request.getRejectReason())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "驳回原因不能为空");
        }
        // —— 驳回前仍须断言目标为药库进货单，避免对其它供应类型误走本入口。——

        // —— 审批人回落：与同意/提交路径一致，单岗可不传 approverId。——
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");
        Long approverId = request.getApproverId();
        if (approverId == null) {
            approverId = operatorId;
        }
        request.setApproverId(approverId);

        // —— 领域驳回：仅供应请求状态与原因，不写发放与库存；领域码 SUPPLY_REQUEST_INVALID_ARGUMENT 经 mapSupplyWorkflowException 收口为 WHS_IN_003。——
        try {
            supplyRequestDomainService.rejectRequest(inboundReceiptMapper.toRejectRequestCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_003);
        }
        // Asm：映射驳回响应
        return inboundReceiptMapper.toRejectInboundReceiptResponse(true);
    }

    /**
     *
     * @param request 请求；{@code busNo} 非空
     * @return 删除成功时 {@code success=true}
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 单号非法；{@link ErrorCodeConstants#WHS_IN_101} 未登录；
     *                            领域拒绝时 {@code WHS_IN_005} 映射
     */
    
    public RemoveInboundReceiptResponse removeInboundReceipt(RemoveInboundReceiptRequest request) {
        // Gate：busNo 与操作人
        if (request == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "请求不能为空");
        }
        this.gateBusNo(request.getBusNo());
        Long operatorId = currentOperatorResolver.requireOperatorId(ErrorCodeConstants.WHS_IN_101, "用户未登录");
        try {
            // Cmd：领域按 busNo 软删
            supplyRequestDomainService.removeByBusNo(
                inboundReceiptMapper.toRemoveByBusNoCommand(request, operatorId));
        } catch (BusinessException ex) {
            throw this.mapSupplyWorkflowException(ex, ErrorCodeConstants.WHS_IN_005);
        }
        return inboundReceiptMapper.toRemoveInboundReceiptResponse(true);
    }

    /**
     *
     * @param request 请求；须含 {@code pageNo}、{@code pageSize}（B.10.11）、{@code purposeLocationId}（目的药库：与建单 {@code purpose_location_id} 对齐之药库门禁）；
     *                可选 {@code productTableName}（非空时须为药品/耗材物理表名白名单）；其余筛选见 {@link PageProductsForInboundReceiptRequest}
     * @return 选品分页结果（1-based 页码元数据；行内 {@code purchasePrice} 为定价表 {@link PriceType#PURCHASE} 实例维采购进价；{@code availableQuantity} 不装配，恒为 {@code null}）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 分页或门禁参数非法、{@code productTableName} 非法；
     *                            {@link ErrorCodeConstants#WHS_IN_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_102} 读模型查询失败
     */
    public PageProductsForInboundReceiptResponse pageProductsForInboundReceipt(PageProductsForInboundReceiptRequest request) {
        // —— 选品分页：除 B.10.11 分页三要素外，目的库必填（药库白名单门禁与进货头目的库语义一致）；不在本路径按库位汇总可用库存。——
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_IN_001, "分页参数不能为空");
        if (request.getPurposeLocationId() == null) {
            // Gate：分页窗口、机构与库房门禁
            // Pre：组装库存目录分页查询
            // Cmd：查询可用库存行
            // Asm：映射选品分页并增强价格
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "purposeLocationId 不能为空");
        }
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);
        this.assertProductTableNameWhitelistForInboundSelection(request.getProductTableName());
        // —— 其余筛选字段由 Mapper 透传至读模型分页查询（keyword/category 等由读模型解释）。——
        ProductDefinitionPageQuery query = inboundReceiptMapper.toProductDefinitionPageQuery(request);
        try {
            Page<ProductDefinitionDisplayDto> page = productDefinitionPurchasePageLoader.pageForPurchase(query);
            PageProductsForInboundReceiptResponse res = inboundReceiptMapper.toPageProductsForInboundReceiptResponse(page);
            // —— B.10.11：PagedResult 使用 getContent() 获取列表数据。——
            this.enrichInboundProductSelectionRows(res.getContent());
            return res;
        } catch (RuntimeException ex) {
            log.warn("pageProductForPurchase failed for inbound receipt", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "采购选品查询失败");
        }
    }

    /**
     * 并保持 {@code availableQuantity} 为 {@code null}（进货选品不关联库存可用量）。
     *
     * @param rows 当前页 DTO 列表（已由 Mapper 将 {@code availableQuantity} 置空）
     */
    private void enrichInboundProductSelectionRows(List<ProductDefinitionItemDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ProductDefinitionItemDto row : rows) {
            if (row != null && StringUtils.isNotBlank(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                // —— 仅合法主键参与 batch：避免空表名污染读模型 IN 查询。——
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductDefinitionId(row.getProductDefinitionId());
                lookup.setProductTableName(row.getProductTableName().trim());
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> products = new HashMap<>();
        if (!lookups.isEmpty()) {
            try {
                // —— 一次拉本页全部产品定义：失败时降级空映射，各行仍可写 PURCHASE 价与空槽。——
                products = this.productDefinitionBatchLoader.loadAsMap(lookups);
            } catch (RuntimeException ex) {
                log.warn("batchGetProductByIds failed for inbound product selection rows", ex);
            }
        }
        ProductDefinitionSelectionTerminologyMaps terminologyMaps =
            this.productDefinitionSelectionTerminologySupport.loadSelectionTerminologyMaps();
        // —— 进货选品不展示目的库可用量：避免逐行打库存读路径；采购进价仍走定价表 PURCHASE。——
        for (ProductDefinitionItemDto row : rows) {
            if (row == null) {
                continue;
            }
            if (StringUtils.isNotBlank(row.getProductTableName()) && row.getProductDefinitionId() != null) {
                String table = row.getProductTableName().trim();
                Long defId = row.getProductDefinitionId();
                BigDecimal purchasePerPackage = this.chargeItemDefinitionQueryService.resolveFirstPurchasePriceValue(table, defId);
                row.setPurchasePrice(purchasePerPackage);
                String key = ProductDefinitionKeyUtil.buildCompositeKey(table, defId);
                ProductDefinitionDisplayDto pr = products.get(key);
                // —— 定价表 PURCHASE 为每包装进价：以此为锚写各槽 unitPrice（采购路径不取 SALE）。——
                List<ProductDefinitionCatalogUnitBandDto> bands =
                    ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBandsWithTerminology(
                        pr, terminologyMaps);
                ProductDefinitionCatalogUnitBandsAssembler.applyInboundWarehouseBandPrices(
                    bands, pr, purchasePerPackage);
                row.setUnitBands(bands);
                if (pr != null) {
                    row.setCategoryCodeDictText(pr.getCategoryCodeDictText());
                }
            } else {
                // —— 缺主键行：清空采购价，避免分页残留旧值误导。——
                row.setPurchasePrice(null);
            }
            // —— 与「不关联库存」契约一致：字段保留于 DTO 以兼容旧客户端，值恒为空。——
            row.setAvailableQuantity(null);
        }
    }

    private void assertProductTableNameWhitelistForInboundSelection(String raw) {
        if (StringUtils.isBlank(raw)) {
            return;
        }
        String table = raw.trim();
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "productTableName 非法");
        }
    }

    /**
     *
     * @param request 请求；须含 {@code purposeLocationId}、{@code pageNo}、{@code pageSize}；目的位置须属当前机构药库白名单
     * @return 订货候选分页（行内类型/状态等为持久化 code，B.10.8.1）
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_001} 参数缺失或位置越权；{@link ErrorCodeConstants#WHS_IN_101} 机构上下文缺失；
     *                            {@link ErrorCodeConstants#WHS_IN_102} 读模型查询失败
     */
    public PageLinkablePurchaseOrdersResponse pageLinkablePurchaseOrders(PageLinkablePurchaseOrdersRequest request) {
        // —— 订货候选：仅服务进货单头「originalBusNo」下拉，故强约束目的库与分页字段，防漏参导致读模型全表扫。——
        if (request == null || request.getPurposeLocationId() == null) {
            // Gate：来源位置与分页窗口
            // Pre：组装可关联来源单查询
            // Cmd：分页查询已同意来源单
            // Asm：映射分页响应
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "purposeLocationId 不能为空");
        }
        pageWindowGate.requireNonNullPageWindow(request, ErrorCodeConstants.WHS_IN_001, "分页参数不能为空");
        this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");
        Set<Long> allowed = this.resolveGatedWarehouseLocationIdsForInboundGate();
        this.assertLocationAllowed(request.getPurposeLocationId(), allowed);

        // —— 仅列出「已同意」的药库订货单行，供进货单关联 originalBusNo；类型与状态均用持久化 code（B.10.8.1）。——
        WarehouseDocumentDetailPageQuery query = new WarehouseDocumentDetailPageQuery();
        query.setTypeEnum(SupplyType.CABINET_PURCHASE_ORDER.getCode());
        query.setStatusEnum(SupplyStatus.AGREE.getCode());
        query.setPurposeLocationId(request.getPurposeLocationId());
        PageQueryBinder.bind(request, query);
        try {
            Page<WarehouseDocumentDetailQueryModel> page = supplyRequestQueryService.pageWarehouseDocumentDetail(query);
            return inboundReceiptMapper.toPageLinkablePurchaseOrdersResponse(page);
        } catch (RuntimeException ex) {
            log.warn("pageLinkablePurchaseOrders failed", ex);
            throw new BusinessException(ErrorCodeConstants.WHS_IN_102, "可关联订货单查询失败");
        }
    }

    private void gateBusNo(String busNo) {
        if (StringUtils.isBlank(busNo)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "busNo 不能为空");
        }
    }

    private void preAssertCabinetInboundReceipt(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo.trim());
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "进货单不存在");
        }
        this.assertCabinetInboundReceipt(lines);
    }

    private void assertCabinetInboundReceipt(List<SupplyRequestModel> lines) {
        for (SupplyRequestModel line : lines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_103, "单据类型非药库进货单");
            }
        }
    }

    /**
     * {@link CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive(java.util.List)} 与管辖库位同源收窄）。
     *
     * @return 允许作为进货源/目的/选品目的库、可关联订货目的库等门禁的药库 {@link Location} 主键集合；主数据未配置药库时可返回空集
     */
    private Set<Long> resolveGatedWarehouseLocationIdsForInboundGate() {
        return this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
    }

    private void assertLocationAllowed(Long locationId, Set<Long> allowed) {
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId, allowed, ErrorCodeConstants.WHS_IN_001, "药库位置不在当前用户可选药库范围");
        if (locationId == null || !allowed.contains(locationId)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "药库位置不在当前用户可选药库范围");
        }
    }

    private void validateOptionalInboundReceiptTypeEnum(String typeEnumCode) {
        if (StringUtils.isBlank(typeEnumCode)) {
            return;
        }
        SupplyType declared = EnumUtil.fromCode(SupplyType.class, typeEnumCode.trim());
        if (declared == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "typeEnum 非法");
        }
        if (declared != SupplyType.CABINET_INBOUND_RECEIPT) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "typeEnum 必须为药库进货单类型");
        }
    }

    private void validateSupplierOptional(Long supplierId) {
        if (supplierId == null) {
            return;
        }
        try {
            SupplierDetailQueryModel detail = supplierQueryService.findSupplierById(supplierId);
            if (!SupplierStatus.ACTIVE.getCode().equals(detail.getStatusEnum())) {
                // —— 主数据存在但未启用：进货单头不允许引用停用供应商，避免后续财务/对账口径混乱。——
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "供应商不可用");
            }
        } catch (BusinessException ex) {
            if (ErrorCodeConstants.SUPPLIER_NOT_FOUND.equals(ex.getErrorCode())) {
                // —— 将「未找到」统一收口为进货场景参数错误码，避免前端区分多套 NOT_FOUND。——
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "供应商不存在或不可用");
            }
            // —— 其它业务异常（如跨租户）原样上抛，由全局处理器记录。——
            throw ex;
        }
    }

    private void validateOriginalPurchaseOrderForInboundReceipt(String originalBusNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(originalBusNo);
        List<SupplyRequestModel> poLines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (poLines.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "来源订货单不存在");
        }
        // —— 订货单为多行聚合：任一行类型/状态不满足即拒绝引用，防止引用「部分同意」或混类型历史数据。——
        for (SupplyRequestModel line : poLines) {
            if (line == null) {
                continue;
            }
            if (!SupplyType.CABINET_PURCHASE_ORDER.getCode().equals(line.getTypeEnum())) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "来源单据类型不是药库订货单");
            }
            if (!SupplyStatus.AGREE.getCode().equals(line.getStatusEnum())) {
                // —— 未整单同意前不允许生成进货关联，避免数量/交期与真实订货事实不一致。——
                throw new BusinessException(ErrorCodeConstants.WHS_IN_002, "订货单未同意，禁止引用生成进货单");
            }
        }
    }

    private SupplyStatus loadHeadSupplyStatus(String busNo) {
        SupplyByBusNoQuery q = new SupplyByBusNoQuery();
        q.setBusNo(busNo);
        List<SupplyRequestModel> lines = supplyRequestQueryService.getSupplyByBusNo(q);
        if (lines.isEmpty()) {
            return null;
        }
        SupplyRequestModel head = lines.get(0);
        return EnumUtil.fromCode(SupplyStatus.class, head.getStatusEnum());
    }

    /**
     * 库存入账与批号维定价共用批号：须非空白，返回 {@code trim} 后字符串（与同意编排整单事务口径一致）。
     *
     * @param lotNumber 进货行批号
     * @return 去首尾空白后的批号
     * @throws BusinessException {@link ErrorCodeConstants#WHS_IN_104} 批号缺失无法入账
     */
    private String resolveLotNumberForInventory(String lotNumber) {
        if (StringUtils.isBlank(lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "批号不能为空，无法完成库存入账");
        }
        return lotNumber.trim();
    }

    private String resolveProductName(SupplyRequestModel line, Map<String, ProductDefinitionDisplayDto> productByKey) {
        if (line == null || line.getItemId() == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细缺少物品标识，无法解析产品名称");
        }
        if (StringUtils.isBlank(line.getItemTable())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "进货明细缺少物品表名，无法解析产品名称");
        }
        if (productByKey == null) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "产品主数据未加载，无法解析产品名称");
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(line.getItemTable(), line.getItemId());
        ProductDefinitionDisplayDto pr = productByKey.get(key);
        if (pr == null || StringUtils.isBlank(pr.getProductName())) {
            throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "产品定义中缺少名称，无法完成库存入账");
        }
        return pr.getProductName();
    }

    /**
     * 皆空时按行、头顺序取 {@code purposeLocationId}，经 {@link LocationQueryService#requireSupplyRequestTypeForLocationId(Long)} 推导；
     * 仍不可得时回落药库（{@link SupplyRequestType#CABINET}，B.10.8.1），兼容历史缺字段数据。
     *
     * @param line 当前进货明细行，可为 null
     * @param head 同号单据头行，可为 null
     * @return 非空、已 {@code trim} 的 {@link SupplyRequestType#getCode()} 字符串
     */
    private String resolvePurposeLocationTypeCode(SupplyRequestModel line, SupplyRequestModel head) {
        if (line != null && StringUtils.isNotBlank(line.getPurposeTypeEnum())) {
            return line.getPurposeTypeEnum().trim();
        }
        if (head != null && StringUtils.isNotBlank(head.getPurposeTypeEnum())) {
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
        return SupplyRequestType.CABINET.getCode();
    }

    /**
     * 库房进货单制单人（{@code wkf_supply_request.applicant_id}）：取当前登录用户绑定医务人员主键，与会话真源一致；
     * 与操作人（命令 {@code operatorId}，用户主键）区分，亦不经请求体代传。
     *
     * @param notLoggedInErrorCode    未登录或缺用户主键时错误码
     * @param notLoggedInMessage      未登录提示
     * @param noPractitionerMessage   已登录但未绑定医务人员时的提示
     * @return 非空医务人员主键
     * @throws BusinessException 无登录用户或未绑定医务人员
     */
    private Long requireWarehousePreparerPractitionerId(
        String notLoggedInErrorCode,
        String notLoggedInMessage,
        String noPractitionerMessage) {
        Long userId = this.currentOperatorResolver.requireOperatorId(notLoggedInErrorCode, notLoggedInMessage);
        Practitioner practitioner = this.practitionerQueryService.findByUserId(userId);
        if (practitioner == null) {
            throw new BusinessException(notLoggedInErrorCode, noPractitionerMessage);
        }
        if (practitioner.getId() == null) {
            throw new BusinessException(notLoggedInErrorCode, noPractitionerMessage);
        }
        return practitioner.getId();
    }

    /**
     * 进货明细行须携带制单时间 {@code occurrence_time}（由前端传入）；草稿阶段不写申请时间。
     */
    private void assertInboundReceiptLinesOccurrencePresent(List<InboundReceiptLineDto> lines) {
        if (lines == null) {
            return;
        }
        for (InboundReceiptLineDto line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getOccurrenceTime() == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_001, "进货明细制单时间不能为空");
            }
        }
    }

    private BusinessException mapSupplyWorkflowException(BusinessException ex, String defaultCode) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            return new BusinessException(defaultCode, ex.getMessage());
        }
        return ex;
    }

    /**
     * {@link ErrorCodeConstants#WHS_IN_104}，否则映射为 {@link ErrorCodeConstants#WHS_IN_003}；其它错误码原样返回。
     *
     * @param ex {@link SupplyRequestDomainService#agreeRequest} 等抛出的业务异常
     * @return 供调用方 {@code throw} 的异常实例
     */
    private BusinessException mapAgreeException(BusinessException ex) {
        if (ErrorCodeConstants.SUPPLY_REQUEST_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("已同意")) {
                return new BusinessException(ErrorCodeConstants.WHS_IN_104, msg);
            }
            return new BusinessException(ErrorCodeConstants.WHS_IN_003, msg);
        }
        return ex;
    }

    private void enrichInboundReceiptListDisplayNames(List<InboundReceiptListRowDto> rows, List<SupplyRequestModel> detailLines) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        if (detailLines == null || detailLines.isEmpty()) {
            return;
        }
        Map<String, Long> busNoToSupplierId = new HashMap<>();
        Map<String, Long> busNoToApplicantId = new HashMap<>();
        Map<String, Long> busNoToApproverId = new HashMap<>();
        Map<String, SupplyRequestModel> headByBusNo = this.buildInboundReceiptHeadLineByBusNo(detailLines);
        for (Map.Entry<String, SupplyRequestModel> e : headByBusNo.entrySet()) {
            SupplyRequestModel head = e.getValue();
            if (head == null) {
                continue;
            }
            String bn = e.getKey();
            if (head.getSupplierId() != null) {
                busNoToSupplierId.put(bn, head.getSupplierId());
            }
            if (head.getApplicantId() != null) {
                busNoToApplicantId.put(bn, head.getApplicantId());
            }
            if (head.getApproverId() != null) {
                busNoToApproverId.put(bn, head.getApproverId());
            }
        }
        // —— 供应商名称按主键去重后逐条解析；页大小有上界，此处 N+1 在可接受范围内（B.10.11 页大小上限）。——
        Map<Long, String> supplierNameById = new HashMap<>();
        for (Long supplierId : new HashSet<>(busNoToSupplierId.values())) {
            if (supplierId == null) {
                continue;
            }
            Optional<String> nameOpt = this.supplierQueryService.findSupplierNameIfPresent(supplierId);
            if (nameOpt.isPresent()) {
                supplierNameById.put(supplierId, nameOpt.get());
            }
        }
        Set<Long> practitionerIds = new HashSet<>();
        practitionerIds.addAll(busNoToApplicantId.values());
        practitionerIds.addAll(busNoToApproverId.values());
        // —— 申请人/审批人可能指向同一医务人员，先合并主键再查，减少重复 IO。——
        Map<Long, String> practitionerNameById = new HashMap<>();
        for (Long pid : practitionerIds) {
            if (pid == null) {
                continue;
            }
            Practitioner pr = this.practitionerQueryService.findById(pid);
            if (pr != null && StringUtils.isNotBlank(pr.getName())) {
                practitionerNameById.put(pid, pr.getName());
            }
        }
        Set<Long> purposeLocationIds = new HashSet<>();
        for (InboundReceiptListRowDto row : rows) {
            if (row != null && row.getPurposeLocationId() != null) {
                purposeLocationIds.add(row.getPurposeLocationId());
            }
        }
        Map<Long, String> purposeLocationNameById = new HashMap<>();
        for (Long locationId : purposeLocationIds) {
            Optional<Location> locOpt = this.locationQueryService.findOptionalById(locationId);
            locOpt.ifPresent(loc -> {
                if (StringUtils.isNotBlank(loc.getName())) {
                    purposeLocationNameById.put(locationId, loc.getName().trim());
                }
            });
        }
        // —— 将解析到的展示名写回聚合行；未命中主数据时保持 null，由前端自行降级展示。——
        for (InboundReceiptListRowDto row : rows) {
            if (row == null || StringUtils.isBlank(row.getBusNo())) {
                continue;
            }
            String bn = row.getBusNo().trim();
            Long sid = busNoToSupplierId.get(bn);
            if (sid != null) {
                String sn = supplierNameById.get(sid);
                if (sn != null) {
                    row.setSupplierName(sn);
                }
            }
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
            if (StringUtils.isNotBlank(row.getStatusEnum())) {
                SupplyStatus st = EnumUtil.fromCode(SupplyStatus.class, row.getStatusEnum().trim());
                if (st != null) {
                    row.setStatusEnumText(st.getDisplay());
                }
            }
        }
    }

    private Map<String, SupplyRequestModel> buildInboundReceiptHeadLineByBusNo(List<SupplyRequestModel> detailLines) {
        Map<String, SupplyRequestModel> best = new HashMap<>();
        if (detailLines == null) {
            return best;
        }
        for (SupplyRequestModel line : detailLines) {
            if (line == null || StringUtils.isBlank(line.getBusNo())) {
                continue;
            }
            String bn = line.getBusNo().trim();
            SupplyRequestModel cur = best.get(bn);
            SupplyRequestModel chosen = this.pickInboundReceiptHeadPreferNewerApply(line, cur);
            best.put(bn, chosen);
        }
        return best;
    }

    private SupplyRequestModel pickInboundReceiptHeadPreferNewerApply(SupplyRequestModel candidate, SupplyRequestModel incumbent) {
        if (incumbent == null) {
            return candidate;
        }
        LocalDateTime tc = candidate.getApplyTime();
        LocalDateTime ti = incumbent.getApplyTime();
        if (tc == null && ti == null) {
            return incumbent;
        }
        if (tc == null) {
            return incumbent;
        }
        if (ti == null) {
            return candidate;
        }
        if (tc.isAfter(ti)) {
            return candidate;
        }
        return incumbent;
    }

    private void assertInboundReceiptTraceNosWithinWideLimit(List<InboundReceiptLineDto> lines, String errorCode) {
        if (lines == null) {
            return;
        }
        int maxLen = GeneralSharedConstants.TRACE_NO_WIDE_MAX_LENGTH;
        for (InboundReceiptLineDto line : lines) {
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
}
