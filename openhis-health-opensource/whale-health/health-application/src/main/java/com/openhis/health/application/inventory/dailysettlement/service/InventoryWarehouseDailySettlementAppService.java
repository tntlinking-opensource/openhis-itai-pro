/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.service;

import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementColumnDto;
import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementExportRowDto;
import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementListRowDto;
import com.openhis.health.application.inventory.dailysettlement.mapper.InventoryWarehouseDailySettlementMapper;
import com.openhis.health.application.inventory.dailysettlement.request.ExportDailySettlementRequest;
import com.openhis.health.application.inventory.dailysettlement.request.LoadDailySettlementFormInitRequest;
import com.openhis.health.application.inventory.dailysettlement.request.PageDailySettlementRequest;
import com.openhis.health.application.inventory.dailysettlement.response.ExportDailySettlementResponse;
import com.openhis.health.application.inventory.dailysettlement.response.LoadDailySettlementFormInitResponse;
import com.openhis.health.application.inventory.dailysettlement.response.PageDailySettlementResponse;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementAggregateRow;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementAssemblyContext;
import com.openhis.health.application.inventory.dailysettlement.support.InventoryDailySettlementAssembler;
import com.openhis.health.application.inventory.dailysettlement.support.InventoryDailySettlementPricingAssembler;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementLocationItemTableResolver;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementTypeWhitelistHelper;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementWindowContext;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementWindowResolver;
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
import com.openhis.health.domain.share.constants.InventoryWarehouseDailySettlementConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDailySettlementExportConstants;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 库房日结只读报表编排（AS-INV-RPT-001）。
 *
 * <p>职责边界：消费 DOC 快照 / InventoryItem 实时库存 / SupplyRequest / Dispense / 调价读侧；
 * 品种级 SUM、动态列与账平在应用层 support 完成；禁止写库存与触发备份。</p>
 * <p>接入：AppServiceControllerRegistrar → POST /api/app/inventoryWarehouseDailySettlement/{methodName}。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryWarehouseDailySettlementAppService {

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
    private final InventoryWarehouseDailySettlementMapper dailySettlementMapper;

    /** 业务日窗解析。 */
    private final DailySettlementWindowResolver windowSupport;

    /** 供应类型白名单。 */
    private final DailySettlementTypeWhitelistHelper typeWhitelistSupport;

    /** 品种汇总与账平。 */
    private final InventoryDailySettlementAssembler assemblySupport;

    /** 行额计算。 */
    private final InventoryDailySettlementPricingAssembler pricingSupport;

    /** 库位 → 物品主表（药/耗）解析。 */
    private final DailySettlementLocationItemTableResolver locationItemTableSupport;

    /**
     * 页面初始化：管辖库房下拉与备份单号降序列表。
     *
     * @param request 空壳请求
     * @return 初始化响应
     * @throws BusinessException {@code INV_SET_201} 初始化失败
     */
    public LoadDailySettlementFormInitResponse loadDailySettlementFormInit(
        @Valid LoadDailySettlementFormInitRequest request
    ) {
        // Gate：登录与租户
        this.assertGateOperatorPresent();
        Long tenantId = this.requireTenantId();
        LoadDailySettlementFormInitResponse response = new LoadDailySettlementFormInitResponse();
        try {
            // Pre：管辖药库 + 药房 + 科室药房下拉
            response.setWarehouseOptions(this.buildWarehouseOptions());
        } catch (RuntimeException ex) {
            log.warn("库房日结初始化：库房下拉加载失败", ex);
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
            log.warn("库房日结初始化：备份单号加载失败", ex);
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
    public PageDailySettlementResponse pageDailySettlement(@Valid PageDailySettlementRequest request) {
        // Gate：必填与管辖库位
        this.assertGateOperatorPresent();
        Long tenantId = this.requireTenantId();
        Location location = this.resolveGatedLocation(request.getLocationId());
        this.validatePageRequestBeforeItemTable(request);
        this.applyItemTableForLocation(request, location, tenantId);
        this.validatePageRequest(request);
        String formEnum = location.getFormEnum();
        DailySettlementWindowContext windowContext =
            this.windowSupport.resolveContext(request.getStartBackupBusNo(), request.getEndBackupBusNo());
        // Pre：批量加载期间业务数据
        DailySettlementAssemblyContext assemblyContext = this.loadAssemblyContext(
            request, tenantId, location, formEnum, windowContext, null);
        // Pre：目录分页主表
        CatalogPageSlice catalogSlice = this.pageCatalogDefinitions(request);
        List<Long> pageItemIds = catalogSlice.itemIds;
        this.enrichProductsForItems(assemblyContext, pageItemIds, request.getItemTable());
        this.loadPriceHistoryForItems(assemblyContext, request.getItemTable(), pageItemIds);
        // Asm：品种填数、筛选与动态列
        List<DailySettlementListRowDto> rows = this.assemblySupport.buildListRows(pageItemIds, assemblyContext);
        rows = this.assemblySupport.applyRowFilters(
            rows, request.getKeyword(), request.getIsBus(), request.getIsBalance());
        List<DailySettlementColumnDto> columns = this.assemblySupport.resolveActiveColumns(formEnum);
        List<String> activeCodes = this.assemblySupport.extractActiveColumnCodes(columns);
        int responsePageNo = 1;
        if (request.getPageNo() != null) {
            responsePageNo = request.getPageNo().intValue();
        }
        return this.dailySettlementMapper.toPageResponse(
            rows,
            catalogSlice.totalElements,
            responsePageNo,
            request.getBoundedPageSize(),
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
    public ExportDailySettlementResponse exportDailySettlement(@Valid ExportDailySettlementRequest request) {
        // Gate：登录、租户与必填
        this.assertGateOperatorPresent();
        Long tenantId = this.requireTenantId();
        PageDailySettlementRequest pageRequest = this.toPageRequest(request);
        Location location = this.resolveGatedLocation(pageRequest.getLocationId());
        this.validatePageRequestBeforeItemTable(pageRequest);
        this.applyItemTableForLocation(pageRequest, location, tenantId);
        this.validatePageRequest(pageRequest);
        String formEnum = location.getFormEnum();
        DailySettlementWindowContext windowContext =
            this.windowSupport.resolveContext(pageRequest.getStartBackupBusNo(), pageRequest.getEndBackupBusNo());
        DailySettlementAssemblyContext assemblyContext = this.loadAssemblyContext(
            pageRequest, tenantId, location, formEnum, windowContext, null);
        List<DailySettlementColumnDto> columns = this.assemblySupport.resolveActiveColumns(formEnum);
        List<DailySettlementListRowDto> allRows = new ArrayList<>();
        int exportCap = InventoryWarehouseDailySettlementExportConstants.EXPORT_MAX_ROWS;
        int pageSize = InventoryWarehouseDailySettlementExportConstants.EXPORT_PAGE_SIZE;
        int pageNo = 1;
        boolean truncated = false;
        while (allRows.size() < exportCap) {
            // Pre：分页拉取目录主表直至耗尽或触顶导出行上限
            PageDailySettlementRequest batchRequest = this.copyPageRequest(pageRequest);
            batchRequest.setPageNo(Integer.valueOf(pageNo));
            batchRequest.setPageSize(Integer.valueOf(pageSize));
            CatalogPageSlice slice = this.pageCatalogDefinitions(batchRequest);
            if (slice.itemIds.isEmpty()) {
                // 已无更多目录页，结束导出拉取
                break;
            }
            this.enrichProductsForItems(assemblyContext, slice.itemIds, pageRequest.getItemTable());
            this.loadPriceHistoryForItems(assemblyContext, pageRequest.getItemTable(), slice.itemIds);
            List<DailySettlementListRowDto> batchRows =
                this.assemblySupport.buildListRows(slice.itemIds, assemblyContext);
            batchRows = this.assemblySupport.applyRowFilters(
                batchRows, pageRequest.getKeyword(), pageRequest.getIsBus(), pageRequest.getIsBalance());
            for (DailySettlementListRowDto row : batchRows) {
                if (allRows.size() >= exportCap) {
                    // 触顶导出行上限，标记截断
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
        List<DailySettlementExportRowDto> exportRows = new ArrayList<>();
        for (DailySettlementListRowDto row : allRows) {
            exportRows.add(this.dailySettlementMapper.toExportRowDto(row));
        }
        // Asm：动态列 Excel 与导出响应
        ExportDailySettlementResponse response = new ExportDailySettlementResponse();
        response.setContentType(InventoryWarehouseDetailExportConstants.XLSX_CONTENT_TYPE);
        response.setExportedRowCount(Integer.valueOf(exportRows.size()));
        response.setTruncated(Boolean.valueOf(truncated));
        String timestamp = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        response.setFileName("库房日结_" + timestamp + ".xlsx");
        try {
            byte[] bytes = this.dailySettlementMapper.buildExcelBytes(
                exportRows,
                columns,
                InventoryWarehouseDailySettlementExportConstants.DAILY_SETTLEMENT_SHEET_NAME
            );
            response.setFileBytes(bytes);
        } catch (RuntimeException ex) {
            log.warn("库房日结导出写流失败 locationId={} startBusNo={}", pageRequest.getLocationId(),
                pageRequest.getStartBackupBusNo(), ex);
            throw new BusinessException(ErrorCodeConstants.INV_SET_205, "导出失败，请稍后重试", ex);
        }
        log.info("库房日结导出 operator={} locationId={} startBusNo={} rows={}",
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
    private DailySettlementAssemblyContext loadAssemblyContext(
        PageDailySettlementRequest request,
        Long tenantId,
        Location location,
        String formEnum,
        DailySettlementWindowContext windowContext,
        List<Long> pageItemIds
    ) {
        DailySettlementAssemblyContext context = new DailySettlementAssemblyContext();
        context.setLocationId(location.getId());
        context.setLocationFormEnum(formEnum);
        context.setItemTable(request.getItemTable());
        context.setEndUsesRealtime(windowContext.isEndUsesRealtime());
        try {
            // Pre：期初快照 SUM
            List<DocInventoryItemSnapshotRowQueryModel> beginRows =
                this.docInventoryItemSnapshotQueryService.listSnapshotRowsForSum(
                    this.dailySettlementMapper.toSnapshotSumQuery(
                        request, tenantId, request.getStartBackupBusNo(), location.getId()),
                    InventoryWarehouseDailySettlementConstants.SNAPSHOT_SUM_MAX_ROWS
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
                        this.dailySettlementMapper.toSnapshotSumQuery(
                            request, tenantId, request.getEndBackupBusNo(), location.getId()),
                        InventoryWarehouseDailySettlementConstants.SNAPSHOT_SUM_MAX_ROWS
                    );
                context.setEndSnapshotByItemId(this.assemblySupport.sumSnapshotRowsByItemId(endRows));
                this.mergeSnapshotPriceFallback(context, endRows);
            }
            // Pre：期间供应动账
            List<String> typeWhitelist = this.typeWhitelistSupport.resolveSupplyTypeCodes(formEnum);
            List<SupplyRequestSettlementLineQueryModel> supplyLines =
                this.supplyRequestQueryService.listAgreedLinesByWarehouseAndApprovalWindow(
                    this.dailySettlementMapper.toSupplyWindowQuery(request, windowContext, typeWhitelist, tenantId)
                );
            Map<Long, DailySettlementAggregateRow> periodMap = new HashMap<>();
            this.assemblySupport.accumulateSupplyLines(supplyLines, location.getId(), formEnum, periodMap);
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
            log.warn("库房日结数据装载失败 locationId={}", location.getId(), ex);
            throw new BusinessException(ErrorCodeConstants.INV_SET_205, "查询失败，请稍后重试", ex);
        }
        return context;
    }

    /**
     * 实时期末库存转批号行并批量读定价。
     *
     * @param context    上下文
     * @param locationId 库位
     * @param itemTable  物品表
     */
    private void loadRealtimeEndInventory(
        DailySettlementAssemblyContext context,
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
        PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext,
        Map<Long, DailySettlementAggregateRow> periodMap,
        DailySettlementAssemblyContext context
    ) {
        MedicationDispenseRecordPageQuery outpatientQuery =
            this.dailySettlementMapper.toMedicationDispenseRecordQuery(request, windowContext, true);
        MedicationDispenseRecordPageQuery inpatientQuery =
            this.dailySettlementMapper.toMedicationDispenseRecordQuery(request, windowContext, false);
        MedicationDispenseRecordPageQuery refundQuery =
            this.dailySettlementMapper.toMedicationDispenseRefundRecordQuery(request, windowContext);
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
     * @return 发药读模型列表；触顶 {@link InventoryWarehouseDailySettlementExportConstants#EXPORT_MAX_ROWS} 时截断
     */
    private List<MedicationDispenseSlipQueryModel> listAllMedicationDispenseSlipsForSettlement(
        MedicationDispenseRecordPageQuery templateQuery
    ) {
        List<MedicationDispenseSlipQueryModel> allRows = new ArrayList<>();
        if (templateQuery == null) {
            return allRows;
        }
        int pageSize = InventoryWarehouseDailySettlementExportConstants.EXPORT_PAGE_SIZE.intValue();
        int maxRows = InventoryWarehouseDailySettlementExportConstants.EXPORT_MAX_ROWS.intValue();
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
        PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext,
        Map<Long, DailySettlementAggregateRow> periodMap,
        DailySettlementAssemblyContext context
    ) {
        List<DeviceDispenseSettlementLineQueryModel> lines =
            this.deviceDispenseQueryService.listDeviceDispensesByLocationAndDispenseWindow(
                this.dailySettlementMapper.toDeviceDispenseWindowQuery(request, windowContext)
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
        DailySettlementAssemblyContext context,
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
        for (Long itemId : itemIds) {
            if (itemId == null || map.containsKey(itemId)) {
                continue;
            }
            List<PriceAdjustmentLineQueryModel> history =
                this.priceAdjustmentQueryService.findApprovedHistoryByItem(itemTable, itemId);
            map.put(itemId, history);
        }
    }

    /**
     * 目录分页切片。
     *
     * @param request 分页请求
     * @return 品种 id 与分页元数据
     */
    private CatalogPageSlice pageCatalogDefinitions(PageDailySettlementRequest request) {
        CatalogPageSlice slice = new CatalogPageSlice();
        int pageIndex = request.springPageIndex();
        int pageSize = request.getBoundedPageSize();
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(request.getItemTable())) {
            Pageable pageable = PageRequest.of(pageIndex, pageSize);
            Page<MedicationDefinitionPageQueryModel> page =
                this.medicationDefinitionQueryService.pageMedicationCatalog(
                    this.dailySettlementMapper.toMedicationCatalogPageQuery(request), pageable);
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
        DailySettlementAssemblyContext context,
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
        DailySettlementAssemblyContext context,
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
        PageDailySettlementRequest request,
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
    private void validatePageRequestBeforeItemTable(PageDailySettlementRequest request) {
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
    private void validatePageRequest(PageDailySettlementRequest request) {
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
    private PageDailySettlementRequest toPageRequest(ExportDailySettlementRequest request) {
        PageDailySettlementRequest pageRequest = new PageDailySettlementRequest();
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
    private PageDailySettlementRequest copyPageRequest(PageDailySettlementRequest source) {
        PageDailySettlementRequest copy = new PageDailySettlementRequest();
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
        DailySettlementAssemblyContext context,
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
        DailySettlementAssemblyContext context,
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
}
