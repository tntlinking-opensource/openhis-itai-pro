/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.service;

import com.openhis.health.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailColumnDto;
import com.openhis.health.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailExportRowDto;
import com.openhis.health.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailListRowDto;
import com.openhis.health.application.inventory.periodbalancedetail.mapper.InventoryPeriodBalanceDetailMapper;
import com.openhis.health.application.inventory.periodbalancedetail.request.ExportPeriodBalanceDetailRequest;
import com.openhis.health.application.inventory.periodbalancedetail.request.LoadPeriodBalanceDetailFormInitRequest;
import com.openhis.health.application.inventory.periodbalancedetail.request.PagePeriodBalanceDetailRequest;
import com.openhis.health.application.inventory.periodbalancedetail.response.ExportPeriodBalanceDetailResponse;
import com.openhis.health.application.inventory.periodbalancedetail.response.LoadPeriodBalanceDetailFormInitResponse;
import com.openhis.health.application.inventory.periodbalancedetail.response.PagePeriodBalanceDetailResponse;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailAggregateRow;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailAssemblyContext;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailAssembler;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailPricingAssembler;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailLocationItemTableResolver;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailTypeWhitelistHelper;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailWindowContext;
import com.openhis.health.application.inventory.periodbalancedetail.support.PeriodBalanceDetailWindowResolver;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotBusNoOptionQueryModel;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.openhis.health.domain.document.inventoryitemsnapshot.service.DocInventoryItemSnapshotQueryService;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.MedicationDefinitionPageQueryModel;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryPeriodBalanceDetailConstants;
import com.openhis.health.domain.share.constants.InventoryPeriodBalanceDetailExportConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseSettlementLineQueryModel;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestSettlementLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.health.application.shared.validator.CurrentManagedLocationIdsResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.session.CurrentOperatorResolver;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 库房期初期末明细只读报表编排（AS-INV-RPT-001）。
 *
 * <p>职责边界：消费 DOC 快照 / InventoryItem 实时库存 / SupplyRequest / Dispense / 调价读侧；
 * 品种级 SUM、动态列与账平在应用层 support 完成；禁止写库存与触发备份。</p>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/inventoryPeriodBalanceDetail/{methodName}。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryPeriodBalanceDetailAppService {

    /** 操作员 Gate。 */
    private final CurrentOperatorResolver currentOperatorResolver;

    /** 管辖库位 Gate。 */
    private final CurrentManagedLocationIdsResolver managedLocationIdsResolver;

    /** 库位读侧。 */
    private final LocationQueryService locationQueryService;

    /** 快照读侧。 */
    private final DocInventoryItemSnapshotQueryService docInventoryItemSnapshotQueryService;

    /** 实时库存读侧。 */
    private final InventoryItemQueryService inventoryItemQueryService;

    /** 药品目录读侧。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 耗材目录读侧。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /** 供应动账读侧。 */
    private final SupplyRequestQueryService supplyRequestQueryService;

    /** 药品发药读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 耗材发药读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 调价历史读侧。 */
    private final PriceAdjustmentQueryService priceAdjustmentQueryService;

    /** 批号定价读侧。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 产品定义批量加载。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** Request/Response 映射。 */
    private final InventoryPeriodBalanceDetailMapper periodBalanceDetailMapper;

    /** 业务日窗解析。 */
    private final PeriodBalanceDetailWindowResolver windowSupport;

    /** 供应类型白名单。 */
    private final PeriodBalanceDetailTypeWhitelistHelper typeWhitelistSupport;

    /** 品种汇总与账平。 */
    private final PeriodBalanceDetailAssembler assemblySupport;

    /** 行额计算。 */
    private final PeriodBalanceDetailPricingAssembler pricingSupport;

    /** 库位 → 物品主表（药/耗）解析。 */
    private final PeriodBalanceDetailLocationItemTableResolver locationItemTableSupport;

    /**
     * 页面初始化：管辖库房下拉与备份单号降序列表。
     *
     * @param request 空壳请求
     * @return 初始化响应
     * @throws BusinessException {@code INV_SET_201} 初始化失败
     */
    public LoadPeriodBalanceDetailFormInitResponse loadPeriodBalanceDetailFormInit(
        @Valid LoadPeriodBalanceDetailFormInitRequest request
    ) {
        // Gate：登录与租户
        this.assertGateOperatorPresent();
        Long tenantId = this.requireTenantId();
        LoadPeriodBalanceDetailFormInitResponse response = new LoadPeriodBalanceDetailFormInitResponse();
        try {
            // Pre：管辖药库 + 药房 + 科室药房下拉
            response.setWarehouseOptions(this.buildWarehouseOptions());
        } catch (RuntimeException ex) {
            log.warn("库房期初期末明细初始化：库房下拉加载失败", ex);
            throw new BusinessException(ErrorCodeConstants.INV_SET_201, "页面加载失败", ex);
        }
        try {
            // Pre：租户备份单号 DISTINCT DESC（含抓拍时刻供前端展示）
            List<DocInventoryItemSnapshotBusNoOptionQueryModel> busNoRows =
                this.docInventoryItemSnapshotQueryService.findDistinctBusNoOptionsByTenant(tenantId);
            List<OptionDto> busNoOptions = new ArrayList<>();
            for (DocInventoryItemSnapshotBusNoOptionQueryModel row : busNoRows) {
                if (row == null || StringUtils.isEmpty(row.getBusNo())) {
                    continue;
                }
                OptionDto option = new OptionDto();
                option.setValue(row.getBusNo().trim());
                option.setLabel(row.getBusNo().trim());
                if (row.getSnapshotTime() != null) {
                    option.setBusNo(GeneralSharedConstants.TIME_HH_MM_SS_FORMATTER.format(row.getSnapshotTime()));
                }
                busNoOptions.add(option);
            }
            response.setBackupBusNoOptions(busNoOptions);
        } catch (RuntimeException ex) {
            log.warn("库房期初期末明细初始化：备份单号加载失败", ex);
            throw new BusinessException(ErrorCodeConstants.INV_SET_201, "页面加载失败", ex);
        }
        // Asm：映射初始化响应
        return response;
    }

    /**
     * 日结分页查询：全量目录主表 + 品种填数 + 账平 + 动态列。
     *
     * @param request 筛选与分页
     * @return 分页响应
     * @throws BusinessException {@code INV_SET_202}～{@code INV_SET_206}
     */
    public PagePeriodBalanceDetailResponse pagePeriodBalanceDetail(@Valid PagePeriodBalanceDetailRequest request) {
        // Gate：必填与管辖库位
        this.assertGateOperatorPresent();
        Long tenantId = this.requireTenantId();
        Location location = this.resolveGatedLocation(request.getLocationId());
        this.validatePageRequestBeforeItemTable(request);
        this.applyItemTableForLocation(request, location, tenantId);
        this.validatePageRequest(request);
        String formEnum = location.getFormEnum();
        PeriodBalanceDetailWindowContext windowContext =
            this.windowSupport.resolveContext(request.getStartBackupBusNo(), request.getEndBackupBusNo());
        // Pre：批量加载期间业务数据
        PeriodBalanceDetailAssemblyContext assemblyContext = this.loadAssemblyContext(
            request, tenantId, location, formEnum, windowContext, null);
        List<PeriodBalanceDetailColumnDto> columns = this.assemblySupport.resolveActiveColumns(formEnum);
        List<String> activeCodes = this.assemblySupport.extractActiveColumnCodes(columns);
        int responsePageNo = 1;
        if (request.getPageNo() != null) {
            responsePageNo = request.getPageNo().intValue();
        }
        int pageSize = request.getBoundedPageSize();
        // Asm：默认「仅有业务」先筛后分页；展示全目录时仍走目录分页
        FilteredPageSlice filteredSlice;
        if (this.isHideZeroBusiness(request.getIsBus())) {
            filteredSlice = this.pageFilteredBusinessRows(request, assemblyContext);
        } else {
            filteredSlice = this.pageCatalogRows(request, assemblyContext);
        }
        return this.periodBalanceDetailMapper.toPageResponse(
            filteredSlice.rows,
            filteredSlice.totalElements,
            responsePageNo,
            pageSize,
            formEnum,
            request.getItemTable(),
            columns,
            activeCodes
        );
    }

    /**
     * 导出 Excel：复用填数链路，动态列与屏显一致。
     *
     * @param request 导出筛选
     * @return 导出响应
     * @throws BusinessException {@code INV_SET_202}～{@code INV_SET_206}
     */
    public ExportPeriodBalanceDetailResponse exportPeriodBalanceDetail(@Valid ExportPeriodBalanceDetailRequest request) {
        // Gate：登录、租户与必填
        this.assertGateOperatorPresent();
        Long tenantId = this.requireTenantId();
        PagePeriodBalanceDetailRequest pageRequest = this.toPageRequest(request);
        Location location = this.resolveGatedLocation(pageRequest.getLocationId());
        this.validatePageRequestBeforeItemTable(pageRequest);
        this.applyItemTableForLocation(pageRequest, location, tenantId);
        this.validatePageRequest(pageRequest);
        String formEnum = location.getFormEnum();
        PeriodBalanceDetailWindowContext windowContext =
            this.windowSupport.resolveContext(pageRequest.getStartBackupBusNo(), pageRequest.getEndBackupBusNo());
        PeriodBalanceDetailAssemblyContext assemblyContext = this.loadAssemblyContext(
            pageRequest, tenantId, location, formEnum, windowContext, null);
        List<PeriodBalanceDetailColumnDto> columns = this.assemblySupport.resolveActiveColumns(formEnum);
        List<PeriodBalanceDetailListRowDto> allRows = new ArrayList<>();
        int exportCap = InventoryPeriodBalanceDetailExportConstants.EXPORT_MAX_ROWS;
        boolean truncated = false;
        // Asm：与列表同筛；默认有业务先筛后导出，避免按全目录翻页漏感/空页
        if (this.isHideZeroBusiness(pageRequest.getIsBus())) {
            List<PeriodBalanceDetailListRowDto> filteredRows =
                this.buildFilteredBusinessRows(pageRequest, assemblyContext);
            for (PeriodBalanceDetailListRowDto row : filteredRows) {
                if (allRows.size() >= exportCap) {
                    truncated = true;
                    break;
                }
                allRows.add(row);
            }
        } else {
            int pageSize = InventoryPeriodBalanceDetailExportConstants.EXPORT_PAGE_SIZE;
            int pageNo = 1;
            while (allRows.size() < exportCap) {
                // Pre：分页拉取目录主表直至耗尽或触顶导出行上限
                PagePeriodBalanceDetailRequest batchRequest = this.copyPageRequest(pageRequest);
                batchRequest.setPageNo(Integer.valueOf(pageNo));
                batchRequest.setPageSize(Integer.valueOf(pageSize));
                CatalogPageSlice slice = this.pageCatalogDefinitions(batchRequest);
                if (slice.itemIds.isEmpty()) {
                    break;
                }
                this.enrichProductsForItems(assemblyContext, slice.itemIds, pageRequest.getItemTable());
                this.loadPriceHistoryForItems(assemblyContext, pageRequest.getItemTable(), slice.itemIds);
                List<PeriodBalanceDetailListRowDto> batchRows =
                    this.assemblySupport.buildListRows(slice.itemIds, assemblyContext);
                batchRows = this.assemblySupport.applyRowFilters(
                    batchRows, pageRequest.getKeyword(), pageRequest.getIsBus(), pageRequest.getIsBalance());
                for (PeriodBalanceDetailListRowDto row : batchRows) {
                    if (allRows.size() >= exportCap) {
                        truncated = true;
                        break;
                    }
                    allRows.add(row);
                }
                if (truncated) {
                    break;
                }
                if (pageNo >= slice.totalPages) {
                    break;
                }
                pageNo = pageNo + 1;
            }
        }
        List<PeriodBalanceDetailExportRowDto> exportRows = new ArrayList<>();
        for (PeriodBalanceDetailListRowDto row : allRows) {
            exportRows.add(this.periodBalanceDetailMapper.toExportRowDto(row));
        }
        // Asm：动态列 Excel 与导出响应
        ExportPeriodBalanceDetailResponse response = new ExportPeriodBalanceDetailResponse();
        response.setContentType(InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE);
        response.setExportedRowCount(Integer.valueOf(exportRows.size()));
        response.setTruncated(Boolean.valueOf(truncated));
        String timestamp = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        response.setFileName("库房期初期末明细_" + timestamp + ".xlsx");
        try {
            byte[] bytes = this.periodBalanceDetailMapper.buildExcelBytes(
                exportRows,
                columns,
                InventoryPeriodBalanceDetailExportConstants.PERIOD_BALANCE_DETAIL_SHEET_NAME,
                request.getExportColumns()
            );
            response.setFileBytes(bytes);
        } catch (RuntimeException ex) {
            log.warn("库房期初期末明细导出写流失败 locationId={} startBusNo={}", pageRequest.getLocationId(),
                pageRequest.getStartBackupBusNo(), ex);
            throw new BusinessException(ErrorCodeConstants.INV_SET_205, "导出失败，请稍后重试", ex);
        }
        log.info("库房期初期末明细导出 operator={} locationId={} startBusNo={} rows={}",
            this.currentOperatorResolver.findOperatorIdOrNull(),
            pageRequest.getLocationId(),
            pageRequest.getStartBackupBusNo(),
            exportRows.size());
        return response;
    }

    /**
     * 批量加载期初/期末/期间业务上下文。
     *
     * @param request        分页请求
     * @param tenantId       租户
     * @param location       库位
     * @param formEnum       形态
     * @param windowContext  时间窗
     * @param pageItemIds    可选页内品种（预加载定价）
     * @return 装配上下文
     */
    private PeriodBalanceDetailAssemblyContext loadAssemblyContext(
        PagePeriodBalanceDetailRequest request,
        Long tenantId,
        Location location,
        String formEnum,
        PeriodBalanceDetailWindowContext windowContext,
        List<Long> pageItemIds
    ) {
        PeriodBalanceDetailAssemblyContext context = new PeriodBalanceDetailAssemblyContext();
        context.setLocationId(location.getId());
        context.setLocationFormEnum(formEnum);
        context.setItemTable(request.getItemTable());
        context.setEndUsesRealtime(windowContext.isEndUsesRealtime());
        try {
            // Pre：期初快照 SUM
            List<DocInventoryItemSnapshotRowQueryModel> beginRows =
                this.docInventoryItemSnapshotQueryService.listSnapshotRowsForSum(
                    this.periodBalanceDetailMapper.toSnapshotSumQuery(
                        request, tenantId, request.getStartBackupBusNo(), location.getId()),
                    InventoryPeriodBalanceDetailConstants.SNAPSHOT_SUM_MAX_ROWS
                );
            if (beginRows == null || beginRows.isEmpty()) {
                throw new BusinessException(ErrorCodeConstants.INV_SET_204, "该库房没有备份库存数据");
            }
            context.setBeginByItemId(this.assemblySupport.sumSnapshotRowsByItemId(beginRows));
            context.setSnapshotPriceFallbackByKey(this.buildSnapshotPriceFallbackByKey(beginRows));
            // Pre：期末快照或实时库存
            if (windowContext.isEndUsesRealtime()) {
                this.loadRealtimeEndInventory(context, location.getId(), request.getItemTable());
            } else {
                List<DocInventoryItemSnapshotRowQueryModel> endRows =
                    this.docInventoryItemSnapshotQueryService.listSnapshotRowsForSum(
                        this.periodBalanceDetailMapper.toSnapshotSumQuery(
                            request, tenantId, request.getEndBackupBusNo(), location.getId()),
                        InventoryPeriodBalanceDetailConstants.SNAPSHOT_SUM_MAX_ROWS
                    );
                context.setEndSnapshotByItemId(this.assemblySupport.sumSnapshotRowsByItemId(endRows));
                this.mergeSnapshotPriceFallback(context, endRows);
            }
            // Pre：期间供应动账
            List<String> typeWhitelist = this.typeWhitelistSupport.resolveSupplyTypeCodes(formEnum);
            List<SupplyRequestSettlementLineQueryModel> supplyLines =
                this.supplyRequestQueryService.listAgreedLinesByWarehouseAndApprovalWindow(
                    this.periodBalanceDetailMapper.toSupplyWindowQuery(request, windowContext, typeWhitelist, tenantId)
                );
            Map<Long, PeriodBalanceDetailAggregateRow> periodMap = new HashMap<>();
            this.assemblySupport.accumulateSupplyLines(supplyLines, location.getId(), formEnum, periodMap);
            // Pre：期间本库调价进价盈亏（仅 location_id 匹配子表；loc 空不计）
            this.accumulatePriceAdjustmentCostByLocation(
                request.getItemTable(),
                location.getId(),
                windowContext,
                periodMap
            );
            // Pre：期间发退药（先拉行再按品种加载调价历史后累加金额）
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(request.getItemTable())) {
                this.accumulateMedicationDispenseWithHistory(request, windowContext, periodMap, context);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(request.getItemTable())) {
                this.accumulateDeviceDispenseWithHistory(request, windowContext, periodMap, context);
            }
            context.setPeriodByItemId(periodMap);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("库房期初期末明细数据装载失败 locationId={}", location.getId(), ex);
            throw new BusinessException(ErrorCodeConstants.INV_SET_205, "查询失败，请稍后重试", ex);
        }
        return context;
    }

    /**
     * 将期间本库调价进价盈亏累加到期间业务 Map（BR-INV-SET-012 调价项）。
     *
     * <p>仅合计子表 {@code location_id} 等于本库的行；loc 空不计。无供应动账的品种也会建累加器行。</p>
     *
     * @param itemTable     物品主表
     * @param locationId    库位
     * @param windowContext 审批时间窗
     * @param periodMap     期间累加器（可增新品种键）
     */
    private void accumulatePriceAdjustmentCostByLocation(
        String itemTable,
        Long locationId,
        PeriodBalanceDetailWindowContext windowContext,
        Map<Long, PeriodBalanceDetailAggregateRow> periodMap
    ) {
        // Gate：缺表名/库位/累加器则无法落调价盈亏
        if (StringUtils.isEmpty(itemTable) || locationId == null || periodMap == null) {
            return;
        }
        LocalDateTime from = null;
        LocalDateTime to = null;
        // 无时间窗：不限审批时刻（仍按库位过滤）
        if (windowContext != null) {
            from = windowContext.getApprovalWindowStartInclusive();
            to = windowContext.getApprovalWindowEndInclusive();
        }
        // Pre：同表同库期间已审批调价进价盈亏（不按页收窄）
        Map<Long, BigDecimal> impactByItemId =
            this.priceAdjustmentQueryService.sumApprovedPurchaseImpactByLocation(
                itemTable.trim(),
                null,
                locationId,
                from,
                to
            );
        // 期间本库无调价进价盈亏：不改 periodMap
        if (impactByItemId == null || impactByItemId.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, BigDecimal> entry : impactByItemId.entrySet()) {
            Long itemId = entry.getKey();
            BigDecimal impact = entry.getValue();
            // 脏键跳过
            if (itemId == null || impact == null) {
                continue;
            }
            PeriodBalanceDetailAggregateRow aggregate = periodMap.get(itemId);
            // 仅调价无动账：补建累加器，保证主表能带出调价列
            if (aggregate == null) {
                aggregate = new PeriodBalanceDetailAggregateRow();
                aggregate.setItemId(itemId);
                periodMap.put(itemId, aggregate);
            }
            BigDecimal current = aggregate.getPriceAdjustmentCostAmount();
            // 空视为 0 再累加（避免 NPE）
            if (current == null) {
                current = BigDecimal.ZERO;
            }
            aggregate.setPriceAdjustmentCostAmount(current.add(impact));
        }
    }

    /**
     * 实时期末库存转批号行并批量读定价。
     *
     * @param context    上下文
     * @param locationId 库位
     * @param itemTable  物品表
     */
    private void loadRealtimeEndInventory(
        PeriodBalanceDetailAssemblyContext context,
        Long locationId,
        String itemTable
    ) {
        List<InventoryItemModel> inventoryItems =
            this.inventoryItemQueryService.getBatchInventoryByWarehouse(locationId, itemTable);
        Map<Long, List<DocInventoryItemSnapshotRowQueryModel>> lotRowsByItem = new HashMap<>();
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> pricingKeys = new LinkedHashSet<>();
        for (InventoryItemModel item : inventoryItems) {
            if (item == null || item.getItemId() == null) {
                continue;
            }
            DocInventoryItemSnapshotRowQueryModel lotRow = new DocInventoryItemSnapshotRowQueryModel();
            lotRow.setItemTable(itemTable);
            lotRow.setItemId(item.getItemId());
            lotRow.setLotNumber(item.getLotNumber());
            lotRow.setQuantity(item.getQuantity());
            List<DocInventoryItemSnapshotRowQueryModel> list = lotRowsByItem.get(item.getItemId());
            if (list == null) {
                list = new ArrayList<>();
                lotRowsByItem.put(item.getItemId(), list);
            }
            list.add(lotRow);
            ChargeItemDefinitionLotBatchKey lotKey =
                ChargeItemDefinitionLotBatchKey.maybeOf(itemTable, item.getItemId(), item.getLotNumber());
            if (lotKey != null) {
                pricingKeys.add(lotKey);
            }
        }
        context.setEndRealtimeLotRowsByItemId(lotRowsByItem);
        if (pricingKeys.isEmpty()) {
            return;
        }
        Map<String, List<ChargeItemDefinition>> costByKey =
            this.chargeItemDefinitionQueryService.findLotNumberCostLinesGroupedByLotBatchKey(pricingKeys);
        Map<String, List<ChargeItemDefinition>> priceByKey =
            this.chargeItemDefinitionQueryService.findLotNumberPriceLinesGroupedByLotBatchKey(pricingKeys);
        Map<String, BigDecimal> lotCost = new HashMap<>();
        Map<String, BigDecimal> lotSale = new HashMap<>();
        for (ChargeItemDefinitionLotBatchKey key : pricingKeys) {
            String composite = key.compositeKey();
            BigDecimal cost = this.pricingSupport.resolveFirstPriceValue(costByKey.get(composite));
            BigDecimal sale = this.pricingSupport.resolveFirstPriceValue(priceByKey.get(composite));
            lotCost.put(composite, cost);
            lotSale.put(composite, sale);
        }
        context.setLotCostPriceByKey(lotCost);
        context.setLotSalePriceByKey(lotSale);
    }

    /**
     * 药品发退药期间汇总（含调价历史预加载）。
     *
     * @param request       请求
     * @param windowContext 时间窗
     * @param periodMap     累加器
     * @param context       装配上下文
     */
    private void accumulateMedicationDispenseWithHistory(
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailWindowContext windowContext,
        Map<Long, PeriodBalanceDetailAggregateRow> periodMap,
        PeriodBalanceDetailAssemblyContext context
    ) {
        MedicationDispenseRecordPageQuery outpatientQuery =
            this.periodBalanceDetailMapper.toMedicationDispenseRecordQuery(request, windowContext, true);
        MedicationDispenseRecordPageQuery inpatientQuery =
            this.periodBalanceDetailMapper.toMedicationDispenseRecordQuery(request, windowContext, false);
        MedicationDispenseRecordPageQuery refundQuery =
            this.periodBalanceDetailMapper.toMedicationDispenseRefundRecordQuery(request, windowContext);
        List<MedicationDispenseSlipQueryModel> outpatientRows =
            this.listAllMedicationDispenseSlipsForSettlement(outpatientQuery);
        List<MedicationDispenseSlipQueryModel> inpatientRows =
            this.listAllMedicationDispenseSlipsForSettlement(inpatientQuery);
        List<MedicationDispenseSlipQueryModel> refundRows =
            this.listAllMedicationDispenseSlipsForSettlement(refundQuery);
        // Pre：正向发药分门诊/住院；退药单独一路——原 ALL 双通道会把同一退药行各计一次（与住院是否发药无关）
        List<MedicationDispenseSlipQueryModel> merged = this.mergeMedicationDispenseSlipsForSettlement(
            outpatientRows, inpatientRows, refundRows);
        LinkedHashSet<Long> dispenseItemIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel line : merged) {
            if (line != null && line.getMedicationId() != null) {
                dispenseItemIds.add(line.getMedicationId());
            }
        }
        this.mergePriceHistory(context, request.getItemTable(), new ArrayList<>(dispenseItemIds));
        this.enrichProductsForItems(context, new ArrayList<>(dispenseItemIds), request.getItemTable());
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> dispenseLotKeys = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel line : merged) {
            if (line == null || line.getMedicationId() == null) {
                continue;
            }
            ChargeItemDefinitionLotBatchKey lotBatchKey = ChargeItemDefinitionLotBatchKey.maybeOf(
                request.getItemTable(), line.getMedicationId(), line.getLotNumber());
            if (lotBatchKey != null) {
                dispenseLotKeys.add(lotBatchKey);
            }
        }
        this.mergeLotCostPricingIntoContext(context, dispenseLotKeys);
        for (MedicationDispenseSlipQueryModel line : merged) {
            if (line == null || line.getMedicationId() == null) {
                continue;
            }
            List<PriceAdjustmentLineQueryModel> history = null;
            if (context.getPriceHistoryByItemId() != null) {
                history = context.getPriceHistoryByItemId().get(line.getMedicationId());
            }
            ProductDefinitionDisplayDto product = null;
            if (context.getProductByItemId() != null) {
                product = context.getProductByItemId().get(line.getMedicationId());
            }
            BigDecimal ratio = this.assemblySupport.resolvePackageUnitRatio(line.getMedicationId(), context);
            BigDecimal splitQty = this.assemblySupport.resolveMedicationDispenseSplitQuantity(line, product);
            if (splitQty == null) {
                continue;
            }
            String lotKey = this.buildLotKey(request.getItemTable(), line.getMedicationId(), line.getLotNumber());
            DocInventoryItemSnapshotRowQueryModel snapshotFallback = null;
            if (lotKey != null && context.getSnapshotPriceFallbackByKey() != null) {
                snapshotFallback = context.getSnapshotPriceFallbackByKey().get(lotKey);
            }
            BigDecimal costPkg = this.pricingSupport.resolveHistoricalCostPrice(
                line.getLotNumber(), line.getDispenseTime(), history, snapshotFallback,
                context.getLotCostPriceByKey(), lotKey);
            BigDecimal salePkg = this.pricingSupport.resolveHistoricalSalePrice(
                line.getLotNumber(), line.getDispenseTime(), history, snapshotFallback,
                context.getLotSalePriceByKey(), lotKey);
            BigDecimal costAmt = this.pricingSupport.resolveDispenseLineCostAmount(
                splitQty, ratio, costPkg, snapshotFallback);
            BigDecimal saleAmt = this.pricingSupport.resolveDispenseLineSaleAmount(
                splitQty, ratio, salePkg, snapshotFallback);
            this.assemblySupport.accumulateMedicationDispenseLine(
                line, product, costAmt, saleAmt, costPkg, salePkg, periodMap);
        }
    }

    /**
     * 正向发药双通道 + 退药单通道合并：按 {@code dispenseId} 去重后返回。
     *
     * <p>退药已与正向分查（{@code RECORD_TYPE_REFUND}）；本处主要消化 {@code TEMPORARY} 正向行
     * 在门诊/住院两路均可能命中的重复。</p>
     *
     * @param outpatientDispenseRows 门诊正向发药
     * @param inpatientDispenseRows  住院正向发药
     * @param refundRows             退药（全场景单路）
     * @return 去重后的发放事实列表
     */
    private List<MedicationDispenseSlipQueryModel> mergeMedicationDispenseSlipsForSettlement(
        List<MedicationDispenseSlipQueryModel> outpatientDispenseRows,
        List<MedicationDispenseSlipQueryModel> inpatientDispenseRows,
        List<MedicationDispenseSlipQueryModel> refundRows
    ) {
        LinkedHashMap<Long, MedicationDispenseSlipQueryModel> byDispenseId = new LinkedHashMap<>();
        List<MedicationDispenseSlipQueryModel> withoutDispenseId = new ArrayList<>();
        this.collectMedicationDispenseSlipsForMerge(byDispenseId, withoutDispenseId, outpatientDispenseRows);
        this.collectMedicationDispenseSlipsForMerge(byDispenseId, withoutDispenseId, inpatientDispenseRows);
        this.collectMedicationDispenseSlipsForMerge(byDispenseId, withoutDispenseId, refundRows);
        List<MedicationDispenseSlipQueryModel> merged = new ArrayList<>(byDispenseId.values());
        merged.addAll(withoutDispenseId);
        return merged;
    }

    /**
     * 将一页发退药行写入合并索引：有主键则去重，无主键则追加到兜底列表。
     *
     * @param byDispenseId      发放主键 → 行（已存在则跳过，避免双通道重复累加）
     * @param withoutDispenseId 无发放主键的行（兜底保留）
     * @param rows              待合并列表
     */
    private void collectMedicationDispenseSlipsForMerge(
        LinkedHashMap<Long, MedicationDispenseSlipQueryModel> byDispenseId,
        List<MedicationDispenseSlipQueryModel> withoutDispenseId,
        List<MedicationDispenseSlipQueryModel> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (MedicationDispenseSlipQueryModel line : rows) {
            if (line == null) {
                continue;
            }
            Long dispenseId = line.getDispenseId();
            if (dispenseId == null) {
                withoutDispenseId.add(line);
                continue;
            }
            if (!byDispenseId.containsKey(dispenseId)) {
                byDispenseId.put(dispenseId, line);
            }
        }
    }

    /**
     * 日结期间药品发退药全量分页拉取（禁止复用 {@code listMedicationDispenseRecords} 单次 5000 条截断）。
     *
     * @param templateQuery 门诊/住院通道查询模板（含库位与时间窗）
     * @return 发药读模型列表；触顶 {@link InventoryPeriodBalanceDetailExportConstants#EXPORT_MAX_ROWS} 时截断
     */
    private List<MedicationDispenseSlipQueryModel> listAllMedicationDispenseSlipsForSettlement(
        MedicationDispenseRecordPageQuery templateQuery
    ) {
        List<MedicationDispenseSlipQueryModel> allRows = new ArrayList<>();
        if (templateQuery == null) {
            return allRows;
        }
        int pageSize = InventoryPeriodBalanceDetailExportConstants.EXPORT_PAGE_SIZE.intValue();
        int maxRows = InventoryPeriodBalanceDetailExportConstants.EXPORT_MAX_ROWS.intValue();
        templateQuery.setPageSize(Integer.valueOf(pageSize));
        int pageNo = 1;
        while (allRows.size() < maxRows) {
            templateQuery.setPageNo(Integer.valueOf(pageNo));
            Page<MedicationDispenseSlipQueryModel> page =
                this.medicationDispenseQueryService.pageMedicationDispenseRecords(templateQuery);
            List<MedicationDispenseSlipQueryModel> content = page.getContent();
            if (content == null || content.isEmpty()) {
                break;
            }
            for (MedicationDispenseSlipQueryModel row : content) {
                if (allRows.size() >= maxRows) {
                    break;
                }
                allRows.add(row);
            }
            if (pageNo >= page.getTotalPages()) {
                break;
            }
            pageNo = pageNo + 1;
        }
        return allRows;
    }

    /**
     * 耗材发退药期间汇总（含调价历史预加载）。
     *
     * @param request       请求
     * @param windowContext 时间窗
     * @param periodMap     累加器
     * @param context       装配上下文
     */
    private void accumulateDeviceDispenseWithHistory(
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailWindowContext windowContext,
        Map<Long, PeriodBalanceDetailAggregateRow> periodMap,
        PeriodBalanceDetailAssemblyContext context
    ) {
        List<DeviceDispenseSettlementLineQueryModel> lines =
            this.deviceDispenseQueryService.listDeviceDispensesByLocationAndDispenseWindow(
                this.periodBalanceDetailMapper.toDeviceDispenseWindowQuery(request, windowContext)
            );
        if (lines == null) {
            return;
        }
        LinkedHashSet<Long> dispenseItemIds = new LinkedHashSet<>();
        for (DeviceDispenseSettlementLineQueryModel line : lines) {
            if (line != null && line.getDeviceDefId() != null) {
                dispenseItemIds.add(line.getDeviceDefId());
            }
        }
        this.mergePriceHistory(context, request.getItemTable(), new ArrayList<>(dispenseItemIds));
        this.enrichProductsForItems(context, new ArrayList<>(dispenseItemIds), request.getItemTable());
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> dispenseLotKeys = new LinkedHashSet<>();
        for (DeviceDispenseSettlementLineQueryModel line : lines) {
            if (line == null || line.getDeviceDefId() == null) {
                continue;
            }
            ChargeItemDefinitionLotBatchKey lotBatchKey = ChargeItemDefinitionLotBatchKey.maybeOf(
                request.getItemTable(), line.getDeviceDefId(), line.getLotNumber());
            if (lotBatchKey != null) {
                dispenseLotKeys.add(lotBatchKey);
            }
        }
        this.mergeLotCostPricingIntoContext(context, dispenseLotKeys);
        for (DeviceDispenseSettlementLineQueryModel line : lines) {
            if (line == null || line.getDeviceDefId() == null) {
                continue;
            }
            List<PriceAdjustmentLineQueryModel> history = null;
            if (context.getPriceHistoryByItemId() != null) {
                history = context.getPriceHistoryByItemId().get(line.getDeviceDefId());
            }
            BigDecimal ratio = this.assemblySupport.resolvePackageUnitRatio(line.getDeviceDefId(), context);
            String lotKey = this.buildLotKey(request.getItemTable(), line.getDeviceDefId(), line.getLotNumber());
            DocInventoryItemSnapshotRowQueryModel snapshotFallback = null;
            if (lotKey != null && context.getSnapshotPriceFallbackByKey() != null) {
                snapshotFallback = context.getSnapshotPriceFallbackByKey().get(lotKey);
            }
            BigDecimal costPkg = this.pricingSupport.resolveHistoricalCostPrice(
                line.getLotNumber(), line.getDispenseTime(), history, snapshotFallback,
                context.getLotCostPriceByKey(), lotKey);
            BigDecimal salePkg = this.pricingSupport.resolveHistoricalSalePrice(
                line.getLotNumber(), line.getDispenseTime(), history, snapshotFallback,
                context.getLotSalePriceByKey(), lotKey);
            BigDecimal costAmt = this.pricingSupport.resolveDispenseLineCostAmount(
                line.getQuantity(), ratio, costPkg, snapshotFallback);
            BigDecimal saleAmt = this.pricingSupport.resolveDispenseLineSaleAmount(
                line.getQuantity(), ratio, salePkg, snapshotFallback);
            this.assemblySupport.accumulateDeviceDispenseLine(line, costAmt, saleAmt, periodMap);
        }
    }

    /**
     * 合并页内/发药品种调价历史到上下文。
     *
     * @param context   上下文
     * @param itemTable 物品表
     * @param itemIds   品种主键
     */
    private void mergePriceHistory(
        PeriodBalanceDetailAssemblyContext context,
        String itemTable,
        List<Long> itemIds
    ) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        Map<Long, List<PriceAdjustmentLineQueryModel>> map = context.getPriceHistoryByItemId();
        if (map == null) {
            map = new HashMap<>();
            context.setPriceHistoryByItemId(map);
        }
        LinkedHashSet<Long> pendingIds = new LinkedHashSet<>();
        for (Long itemId : itemIds) {
            if (itemId == null || map.containsKey(itemId)) {
                continue;
            }
            pendingIds.add(itemId);
        }
        if (pendingIds.isEmpty()) {
            return;
        }
        // Pre：先筛有调价品规；无调价者写入空历史，禁止无脑 findApprovedHistoryByItem
        LinkedHashSet<Long> adjustedIds =
            this.priceAdjustmentQueryService.listApprovedItemIds(itemTable, pendingIds);
        for (Long itemId : pendingIds) {
            if (!adjustedIds.contains(itemId)) {
                map.put(itemId, List.of());
            }
        }
        if (adjustedIds.isEmpty()) {
            return;
        }
        Map<Long, List<PriceAdjustmentLineQueryModel>> historyByItem =
            this.priceAdjustmentQueryService.findApprovedHistoryByItems(itemTable, adjustedIds);
        for (Long itemId : adjustedIds) {
            List<PriceAdjustmentLineQueryModel> history = historyByItem.get(itemId);
            if (history == null) {
                map.put(itemId, List.of());
            } else {
                map.put(itemId, history);
            }
        }
    }

    /**
     * 是否默认隐藏期间全零业务行（{@code isBus} 未传或 true）。
     *
     * @param isBus 请求筛选
     * @return 隐藏为 true
     */
    private boolean isHideZeroBusiness(Boolean isBus) {
        if (isBus == null) {
            return true;
        }
        return isBus.booleanValue();
    }

    /**
     * 默认「仅有业务」：以期间动账品种为主表，先筛后分页，{@code total} 为筛选后条数。
     *
     * @param request          分页请求
     * @param assemblyContext  已装载上下文
     * @return 当前页行与筛选后总数
     */
    private FilteredPageSlice pageFilteredBusinessRows(
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailAssemblyContext assemblyContext
    ) {
        List<PeriodBalanceDetailListRowDto> filteredRows =
            this.buildFilteredBusinessRows(request, assemblyContext);
        return this.sliceFilteredRows(filteredRows, request);
    }

    /**
     * 展示全目录：目录分页主表 + 当前页行筛选（{@code isBalance}/关键字）。
     *
     * @param request          分页请求
     * @param assemblyContext  已装载上下文
     * @return 当前页行与目录总数
     */
    private FilteredPageSlice pageCatalogRows(
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailAssemblyContext assemblyContext
    ) {
        CatalogPageSlice catalogSlice = this.pageCatalogDefinitions(request);
        List<Long> pageItemIds = catalogSlice.itemIds;
        this.enrichProductsForItems(assemblyContext, pageItemIds, request.getItemTable());
        this.loadPriceHistoryForItems(assemblyContext, request.getItemTable(), pageItemIds);
        List<PeriodBalanceDetailListRowDto> rows =
            this.assemblySupport.buildListRows(pageItemIds, assemblyContext);
        rows = this.assemblySupport.applyRowFilters(
            rows, request.getKeyword(), request.getIsBus(), request.getIsBalance());
        FilteredPageSlice slice = new FilteredPageSlice();
        slice.rows = rows;
        slice.totalElements = catalogSlice.totalElements;
        return slice;
    }

    /**
     * 收集期间有业务品种、填数并应用 keyword / isBus / isBalance。
     *
     * @param request          筛选条件
     * @param assemblyContext  已装载上下文
     * @return 筛选后全量行（未分页）
     */
    private List<PeriodBalanceDetailListRowDto> buildFilteredBusinessRows(
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailAssemblyContext assemblyContext
    ) {
        List<Long> candidateItemIds = this.collectPeriodBusinessItemIds(assemblyContext);
        if (candidateItemIds.isEmpty()) {
            return new ArrayList<>();
        }
        this.enrichProductsForItems(assemblyContext, candidateItemIds, request.getItemTable());
        this.loadPriceHistoryForItems(assemblyContext, request.getItemTable(), candidateItemIds);
        List<PeriodBalanceDetailListRowDto> rows =
            this.assemblySupport.buildListRows(candidateItemIds, assemblyContext);
        return this.assemblySupport.applyRowFilters(
            rows, request.getKeyword(), request.getIsBus(), request.getIsBalance());
    }

    /**
     * 期间业务 Map 中的品种主键（按 id 升序，稳定分页）。
     *
     * @param assemblyContext 装配上下文
     * @return 候选品种 id
     */
    private List<Long> collectPeriodBusinessItemIds(PeriodBalanceDetailAssemblyContext assemblyContext) {
        List<Long> itemIds = new ArrayList<>();
        Map<Long, PeriodBalanceDetailAggregateRow> periodByItemId = assemblyContext.getPeriodByItemId();
        if (periodByItemId == null || periodByItemId.isEmpty()) {
            return itemIds;
        }
        for (Long itemId : periodByItemId.keySet()) {
            if (itemId != null) {
                itemIds.add(itemId);
            }
        }
        Collections.sort(itemIds);
        return itemIds;
    }

    /**
     * 对筛选后全量行做内存分页。
     *
     * @param filteredRows 筛选后行
     * @param request      分页参数
     * @return 当前页切片
     */
    private FilteredPageSlice sliceFilteredRows(
        List<PeriodBalanceDetailListRowDto> filteredRows,
        PagePeriodBalanceDetailRequest request
    ) {
        FilteredPageSlice slice = new FilteredPageSlice();
        if (filteredRows == null) {
            slice.rows = new ArrayList<>();
            slice.totalElements = 0L;
            return slice;
        }
        slice.totalElements = filteredRows.size();
        int pageSize = request.getBoundedPageSize();
        int pageNo = 1;
        if (request.getPageNo() != null && request.getPageNo().intValue() > 0) {
            pageNo = request.getPageNo().intValue();
        }
        int fromIndex = (pageNo - 1) * pageSize;
        if (fromIndex >= filteredRows.size()) {
            slice.rows = new ArrayList<>();
            return slice;
        }
        int toIndex = fromIndex + pageSize;
        if (toIndex > filteredRows.size()) {
            toIndex = filteredRows.size();
        }
        slice.rows = new ArrayList<>(filteredRows.subList(fromIndex, toIndex));
        return slice;
    }

    /**
     * 目录分页切片。
     *
     * @param request 分页请求
     * @return 品种 id 与分页元数据
     */
    private CatalogPageSlice pageCatalogDefinitions(PagePeriodBalanceDetailRequest request) {
        CatalogPageSlice slice = new CatalogPageSlice();
        int pageIndex = request.springPageIndex();
        int pageSize = request.getBoundedPageSize();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(request.getItemTable())) {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Page<MedicationDefinitionPageQueryModel> page =
                this.medicationDefinitionQueryService.pageMedicationCatalog(
                    this.periodBalanceDetailMapper.toMedicationCatalogPageQuery(request), pageable);
            slice.totalElements = page.getTotalElements();
            slice.totalPages = page.getTotalPages();
            for (MedicationDefinitionPageQueryModel model : page.getContent()) {
                if (model != null && model.getId() != null) {
                    slice.itemIds.add(model.getId());
                }
            }
            return slice;
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(request.getItemTable())) {
            Page<DeviceDefinition> page = this.deviceDefinitionQueryService.pageDeviceDefinition(
                pageIndex, pageSize, request.getKeyword(), null, null);
            slice.totalElements = page.getTotalElements();
            slice.totalPages = page.getTotalPages();
            for (DeviceDefinition model : page.getContent()) {
                if (model != null && model.getId() != null) {
                    slice.itemIds.add(model.getId());
                }
            }
            return slice;
        }
        throw new BusinessException(ErrorCodeConstants.INV_SET_202, "请选择库房");
    }

    /**
     * 批量 enrich 产品定义。
     *
     * @param context   上下文
     * @param itemIds   品种主键
     * @param itemTable 物品表
     */
    private void enrichProductsForItems(
        PeriodBalanceDetailAssemblyContext context,
        List<Long> itemIds,
        String itemTable
    ) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long itemId : itemIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(itemTable);
            lookup.setProductDefinitionId(itemId);
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> loaded = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<Long, ProductDefinitionDisplayDto> byItemId = new HashMap<>();
        if (context.getProductByItemId() != null) {
            byItemId.putAll(context.getProductByItemId());
        }
        for (Long itemId : itemIds) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(itemTable, itemId);
            ProductDefinitionDisplayDto dto = loaded.get(key);
            if (dto != null) {
                byItemId.put(itemId, dto);
            }
        }
        context.setProductByItemId(byItemId);
    }

    /**
     * 页内品种调价历史批量加载。
     *
     * @param context   上下文
     * @param itemTable 物品表
     * @param itemIds   品种主键
     */
    private void loadPriceHistoryForItems(
        PeriodBalanceDetailAssemblyContext context,
        String itemTable,
        List<Long> itemIds
    ) {
        this.mergePriceHistory(context, itemTable, itemIds);
    }

    /**
     * 构建管辖库房 OptionDto。
     *
     * @return 下拉列表
     */
    private List<OptionDto> buildWarehouseOptions() {
        List<OptionDto> options = new ArrayList<>();
        LinkedHashMap<Long, Location> locationById = new LinkedHashMap<>();
        for (Location warehouse : this.managedLocationIdsResolver.listManagedActiveWarehousesOrEmpty()) {
            if (warehouse == null || warehouse.getId() == null) {
                continue;
            }
            locationById.put(warehouse.getId(), warehouse);
        }
        for (Location pharmacy : this.managedLocationIdsResolver.listManagedActivePharmaciesOrEmpty()) {
            if (pharmacy == null || pharmacy.getId() == null) {
                continue;
            }
            locationById.put(pharmacy.getId(), pharmacy);
        }
        for (Location deptPharmacy : this.managedLocationIdsResolver.narrowActiveLocationsByManaged(
            this.locationQueryService.listAllActiveDepartmentPharmacies())) {
            if (deptPharmacy == null || deptPharmacy.getId() == null) {
                continue;
            }
            locationById.put(deptPharmacy.getId(), deptPharmacy);
        }
        for (Location location : locationById.values()) {
            OptionDto option = new OptionDto();
            option.setValue(String.valueOf(location.getId()));
            option.setLabel(location.getName());
            options.add(option);
        }
        return options;
    }

    /**
     * 按库位现库/期初快照 DISTINCT {@code item_table} 写入请求（无需前端传表名）。
     *
     * @param request  日结请求
     * @param location 已 Gate 库位
     * @param tenantId 租户
     */
    private void applyItemTableForLocation(
        PagePeriodBalanceDetailRequest request,
        Location location,
        Long tenantId
    ) {
        if (request == null || location == null) {
            return;
        }
        request.setItemTable(this.locationItemTableSupport.resolveItemTable(
            location, tenantId, request.getStartBackupBusNo()));
    }

    /**
     * 解析 itemTable 前的 Gate：库房与期初备份。
     *
     * @param request 请求
     */
    private void validatePageRequestBeforeItemTable(PagePeriodBalanceDetailRequest request) {
        if (request.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_202, "请选择库房");
        }
        if (StringUtils.isEmpty(request.getStartBackupBusNo())) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_203, "请选择期初备份单号");
        }
    }

    /**
     * 分页请求 Gate 校验。
     *
     * @param request 请求
     */
    private void validatePageRequest(PagePeriodBalanceDetailRequest request) {
        if (request.getLocationId() == null) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_202, "请选择库房");
        }
        if (StringUtils.isEmpty(request.getStartBackupBusNo())) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_203, "请选择期初备份单号");
        }
        if (StringUtils.isEmpty(request.getItemTable())) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_202, "请选择库房");
        }
        if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(request.getItemTable())
            && !TableNameConstants.ADM_DEVICE_DEFINITION.equals(request.getItemTable())) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_202, "请选择库房");
        }
    }

    /**
     * 解析并 Gate 库位。
     *
     * @param locationId 库位主键
     * @return 库位实体
     */
    private Location resolveGatedLocation(Long locationId) {
        Optional<Location> optional = this.locationQueryService.findOptionalById(locationId);
        if (optional.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_206, "无权查看该库房");
        }
        Location location = optional.get();
        Set<Long> gatedIds = this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveWarehouses());
        Set<Long> pharmacyIds = this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActivePharmacies());
        Set<Long> deptPharmacyIds = this.managedLocationIdsResolver.gatedLocationIdsFromActive(
            this.locationQueryService.listAllActiveDepartmentPharmacies());
        gatedIds.addAll(pharmacyIds);
        gatedIds.addAll(deptPharmacyIds);
        this.managedLocationIdsResolver.assertLocationInManagedGate(
            locationId,
            gatedIds,
            ErrorCodeConstants.INV_SET_206,
            "无权查看该库房"
        );
        return location;
    }

    /**
     * 导出入参转分页入参。
     *
     * @param request 导出入参
     * @return 分页入参
     */
    private PagePeriodBalanceDetailRequest toPageRequest(ExportPeriodBalanceDetailRequest request) {
        PagePeriodBalanceDetailRequest pageRequest = new PagePeriodBalanceDetailRequest();
        pageRequest.setLocationId(request.getLocationId());
        pageRequest.setStartBackupBusNo(request.getStartBackupBusNo());
        pageRequest.setEndBackupBusNo(request.getEndBackupBusNo());
        pageRequest.setItemTable(request.getItemTable());
        pageRequest.setKeyword(request.getKeyword());
        pageRequest.setIsBus(request.getIsBus());
        pageRequest.setIsBalance(request.getIsBalance());
        return pageRequest;
    }

    /**
     * 浅拷贝分页请求（翻页导出用）。
     *
     * @param source 源请求
     * @return 副本
     */
    private PagePeriodBalanceDetailRequest copyPageRequest(PagePeriodBalanceDetailRequest source) {
        PagePeriodBalanceDetailRequest copy = new PagePeriodBalanceDetailRequest();
        copy.setLocationId(source.getLocationId());
        copy.setStartBackupBusNo(source.getStartBackupBusNo());
        copy.setEndBackupBusNo(source.getEndBackupBusNo());
        copy.setItemTable(source.getItemTable());
        copy.setKeyword(source.getKeyword());
        copy.setIsBus(source.getIsBus());
        copy.setIsBalance(source.getIsBalance());
        return copy;
    }

    /**
     * 构建批号复合键。
     *
     * @param itemTable 物品表
     * @param itemId    品种
     * @param lotNumber 批号
     * @return 复合键
     */
    private String buildLotKey(String itemTable, Long itemId, String lotNumber) {
        ChargeItemDefinitionLotBatchKey key =
            ChargeItemDefinitionLotBatchKey.maybeOf(itemTable, itemId, lotNumber);
        if (key == null) {
            return null;
        }
        return key.compositeKey();
    }

    /**
     * 自快照批号行构建发药进价回落索引（BR-INV-SET-020；同键保留较新 {@code snapshotTime}）。
     *
     * @param rows 快照批号行
     * @return composite lot key → 快照行
     */
    private Map<String, DocInventoryItemSnapshotRowQueryModel> buildSnapshotPriceFallbackByKey(
        List<DocInventoryItemSnapshotRowQueryModel> rows
    ) {
        Map<String, DocInventoryItemSnapshotRowQueryModel> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (DocInventoryItemSnapshotRowQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            String compositeKey = this.buildLotKey(row.getItemTable(), row.getItemId(), row.getLotNumber());
            if (compositeKey == null) {
                continue;
            }
            DocInventoryItemSnapshotRowQueryModel existing = map.get(compositeKey);
            if (existing == null) {
                map.put(compositeKey, row);
                continue;
            }
            java.time.LocalDateTime existingTime = existing.getSnapshotTime();
            java.time.LocalDateTime rowTime = row.getSnapshotTime();
            if (rowTime != null && (existingTime == null || rowTime.isAfter(existingTime))) {
                map.put(compositeKey, row);
            }
        }
        return map;
    }

    /**
     * 合并快照批号四价回落（不覆盖已存在且更新的条目）。
     *
     * @param context 装配上下文
     * @param rows    追加快照行
     */
    private void mergeSnapshotPriceFallback(
        PeriodBalanceDetailAssemblyContext context,
        List<DocInventoryItemSnapshotRowQueryModel> rows
    ) {
        if (context == null || rows == null || rows.isEmpty()) {
            return;
        }
        Map<String, DocInventoryItemSnapshotRowQueryModel> target = context.getSnapshotPriceFallbackByKey();
        if (target == null) {
            target = new HashMap<>();
            context.setSnapshotPriceFallbackByKey(target);
        }
        Map<String, DocInventoryItemSnapshotRowQueryModel> incoming = this.buildSnapshotPriceFallbackByKey(rows);
        for (Map.Entry<String, DocInventoryItemSnapshotRowQueryModel> entry : incoming.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            DocInventoryItemSnapshotRowQueryModel existing = target.get(entry.getKey());
            if (existing == null) {
                target.put(entry.getKey(), entry.getValue());
                continue;
            }
            java.time.LocalDateTime existingTime = existing.getSnapshotTime();
            java.time.LocalDateTime incomingTime = entry.getValue().getSnapshotTime();
            if (incomingTime != null && (existingTime == null || incomingTime.isAfter(existingTime))) {
                target.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 批量合并批号现价进/售价到上下文（发药历史价第三回落）。
     *
     * @param context     装配上下文
     * @param pricingKeys 批号定价键
     */
    private void mergeLotCostPricingIntoContext(
        PeriodBalanceDetailAssemblyContext context,
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> pricingKeys
    ) {
        if (context == null || pricingKeys == null || pricingKeys.isEmpty()) {
            return;
        }
        Map<String, List<ChargeItemDefinition>> costByKey =
            this.chargeItemDefinitionQueryService.findLotNumberCostLinesGroupedByLotBatchKey(pricingKeys);
        Map<String, List<ChargeItemDefinition>> priceByKey =
            this.chargeItemDefinitionQueryService.findLotNumberPriceLinesGroupedByLotBatchKey(pricingKeys);
        Map<String, BigDecimal> lotCost = context.getLotCostPriceByKey();
        if (lotCost == null) {
            lotCost = new HashMap<>();
            context.setLotCostPriceByKey(lotCost);
        }
        Map<String, BigDecimal> lotSale = context.getLotSalePriceByKey();
        if (lotSale == null) {
            lotSale = new HashMap<>();
            context.setLotSalePriceByKey(lotSale);
        }
        for (ChargeItemDefinitionLotBatchKey key : pricingKeys) {
            if (key == null) {
                continue;
            }
            String composite = key.compositeKey();
            if (!lotCost.containsKey(composite)) {
                BigDecimal cost = this.pricingSupport.resolveFirstPriceValue(costByKey.get(composite));
                if (cost != null) {
                    lotCost.put(composite, cost);
                }
            }
            if (!lotSale.containsKey(composite)) {
                BigDecimal sale = this.pricingSupport.resolveFirstPriceValue(priceByKey.get(composite));
                if (sale != null) {
                    lotSale.put(composite, sale);
                }
            }
        }
    }

    /**
     * Gate：须已登录。
     */
    private void assertGateOperatorPresent() {
        this.currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.INV_SET_201,
            "登录上下文缺失");
    }

    /**
     * 解析租户主键。
     *
     * @return 租户 id
     */
    private Long requireTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCodeConstants.INV_SET_201, "登录上下文缺失");
        }
        return tenantId;
    }

    /**
     * 目录分页切片载体。
     */
    private static final class CatalogPageSlice {

        /** 当前页品种主键。 */
        private final List<Long> itemIds = new ArrayList<>();

        /** 总条数。 */
        private long totalElements;

        /** 总页数。 */
        private int totalPages;
    }

    /**
     * 列表分页结果（行已筛选）。
     */
    private static final class FilteredPageSlice {

        /** 当前页行。 */
        private List<PeriodBalanceDetailListRowDto> rows = new ArrayList<>();

        /** 筛选后总条数（默认有业务）或目录总数（展示全目录）。 */
        private long totalElements;
    }
}
