/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.inventorydetail.mapper;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.inventory.inventorydetail.dto.InventoryDetailBackupExportRowDto;
import com.openhis.health.application.inventory.inventorydetail.dto.InventoryDetailBackupListRowDto;
import com.openhis.health.application.inventory.inventorydetail.dto.InventoryDetailCurrentExportRowDto;
import com.openhis.health.application.inventory.inventorydetail.dto.InventoryDetailListRowDto;
import com.openhis.health.application.inventory.inventorydetail.dto.InventoryItemTraceNumberRowDto;
import com.openhis.health.domain.custom.tracenumber.queries.model.InventoryItemTraceNumberQueryModel;
import com.openhis.health.application.inventory.inventorydetail.request.ExportInventoryDetailBackupRequest;
import com.openhis.health.application.inventory.inventorydetail.request.ExportInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.inventorydetail.request.PageInventoryDetailBackupRequest;
import com.openhis.health.application.inventory.inventorydetail.request.PageInventoryDetailCurrentRequest;
import com.openhis.health.application.inventory.inventorydetail.request.SumInventoryDetailBackupRequest;
import com.openhis.health.application.inventory.inventorydetail.request.SumInventoryDetailCurrentRequest;
import com.openhis.health.domain.document.inventoryitemsnapshot.command.DocInventoryItemSnapshotRowCommand;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InventoryWarehouseDetailExportConstants;
import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import com.openhis.health.domain.workflow.inventoryitem.service.InventoryItemQueryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 库存明细 Mapper 实现：将 HTTP 请求投影为 {@link WarehouseInventoryDetailPageQuery}，并把现库装载委托给
 * {@link InventoryItemQueryService}（JPA {@code Specification} 下推，非 UNION SQL）。
 */
@Component
@RequiredArgsConstructor
public class InventoryWarehouseInventoryDetailMapperImpl implements InventoryWarehouseInventoryDetailMapper {

    private final InventoryItemQueryService inventoryItemQueryService;

    /**
     * 将现库分页请求映射为领域分页查询：复制分页与排序父类字段，写入筛选条件，并附着应用层已求交的库房主键列表。
     *
     * @param request               分页请求，调用方保证非 null
     * @param effectiveWarehouseIds 门控后的库房范围，允许为 null 或空（表示不按库房收窄，与产品策略一致）
     * @return 新构造的 {@link WarehouseInventoryDetailPageQuery}
     */
    @Override
    public WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        PageInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    ) {
        WarehouseInventoryDetailPageQuery q = new WarehouseInventoryDetailPageQuery();
        PageQueryBinder.bind(request, q);
        this.applyFilters(q, request.getMedicationCategoryCodes(), request.getDeviceCategoryCodes(),
            request.getSupplierIds(), request.getChrgitmLvList(), request.getKeyDrugType(),
            request.getMedicinePropertyCode(),
            request.getZeroStockOnly(), request.getExpireWithinDays(), request.getInventoryStatusEnums(),
            request.getItemTable());
        q.setEffectiveWarehouseIds(effectiveWarehouseIds);
        return q;
    }

    /**
     * 将现库汇总请求映射为领域查询：不含分页字段，仅筛选与关键字及门控库房范围。
     *
     * @param request               汇总请求，调用方保证非 null
     * @param effectiveWarehouseIds 门控后的库房范围，允许为 null 或空
     * @return 新构造的 {@link WarehouseInventoryDetailPageQuery}
     */
    @Override
    public WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        SumInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    ) {
        WarehouseInventoryDetailPageQuery q = new WarehouseInventoryDetailPageQuery();
        q.setKeyword(request.getKeyword());
        this.applyFilters(q, request.getMedicationCategoryCodes(), request.getDeviceCategoryCodes(),
            request.getSupplierIds(), request.getChrgitmLvList(), request.getKeyDrugType(),
            request.getMedicinePropertyCode(),
            request.getZeroStockOnly(), request.getExpireWithinDays(), request.getInventoryStatusEnums(),
            request.getItemTable());
        q.setEffectiveWarehouseIds(effectiveWarehouseIds);
        return q;
    }

    /**
     * 将现库导出请求映射为领域查询：与汇总请求同构，无分页语义。
     *
     * @param request               导出请求，调用方保证非 null
     * @param effectiveWarehouseIds 门控后的库房范围，允许为 null 或空
     * @return 新构造的 {@link WarehouseInventoryDetailPageQuery}
     */
    @Override
    public WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        ExportInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    ) {
        WarehouseInventoryDetailPageQuery q = new WarehouseInventoryDetailPageQuery();
        q.setKeyword(request.getKeyword());
        this.applyFilters(q, request.getMedicationCategoryCodes(), request.getDeviceCategoryCodes(),
            request.getSupplierIds(), request.getChrgitmLvList(), request.getKeyDrugType(),
            request.getMedicinePropertyCode(),
            request.getZeroStockOnly(), request.getExpireWithinDays(), request.getInventoryStatusEnums(),
            request.getItemTable());
        q.setEffectiveWarehouseIds(effectiveWarehouseIds);
        return q;
    }

    /**
     * 将筛选字段写入领域查询对象，避免在多个 toWarehouse 重载中重复赋值。
     *
     * @param q                       目标查询对象
     * @param medicationCategoryCodes 药品分类编码列表
     * @param deviceCategoryCodes     耗材分类编码列表
     * @param supplierIds             供应商主键列表
     * @param chrgitmLvList           医保等级列表（与读模型列语义一致）
     * @param keyDrugType             重点药类型 code（null/空不限）
     * @param medicinePropertyCode    药品性质 code（null/空不限）
     * @param zeroStockOnly           库存范围（true 数量等于0，false 数量大于0）
     * @param expireWithinDays        近效期天数
     * @param inventoryStatusEnums    供应状态持久化码列表
     * @param itemTable               项目类型（药品/耗材定义表名）；空/null 不限
     */
    private void applyFilters(
        WarehouseInventoryDetailPageQuery q,
        List<String> medicationCategoryCodes,
        List<String> deviceCategoryCodes,
        List<Long> supplierIds,
        List<Integer> chrgitmLvList,
        String keyDrugType,
        String medicinePropertyCode,
        Boolean zeroStockOnly,
        Integer expireWithinDays,
        List<String> inventoryStatusEnums,
        String itemTable
    ) {
        q.setMedicationCategoryCodes(medicationCategoryCodes);
        q.setDeviceCategoryCodes(deviceCategoryCodes);
        q.setSupplierIds(supplierIds);
        q.setChrgitmLvList(chrgitmLvList);
        q.setKeyDrugType(keyDrugType);
        q.setMedicinePropertyCode(medicinePropertyCode);
        q.setZeroStockOnly(zeroStockOnly);
        q.setExpireWithinDays(expireWithinDays);
        q.setInventoryStatusEnums(inventoryStatusEnums);
        // 项目类型非空时按药品/耗材定义表名收窄
        if (StringUtils.isNotEmpty(itemTable)) {
            q.setItemTable(itemTable.trim());
        }
    }

    /**
     * 现库明细分页装载：委托 {@link InventoryItemQueryService#queryWarehouseInventoryDetailPage}。
     *
     * @param query    领域筛选条件，调用方保证非 null
     * @param pageable 分页与排序，调用方保证非 null
     * @return Spring Data 分页结果
     */
    @Override
    public Page<WarehouseInventoryDetailItemModel> selectCurrentDetailPage(
        WarehouseInventoryDetailPageQuery query,
        Pageable pageable
    ) {
        return this.inventoryItemQueryService.queryWarehouseInventoryDetailPage(query, pageable);
    }

    /**
     * 现库汇总候选行装载：至多 {@code maxRows} 行，无排序要求时使用 {@link Sort#unsorted()}。
     *
     * @param query   领域筛选条件，调用方保证非 null
     * @param maxRows 最大行数，须为正数
     * @return 行集合
     */
    @Override
    public List<WarehouseInventoryDetailItemModel> selectCurrentDetailForSum(
        WarehouseInventoryDetailPageQuery query,
        int maxRows
    ) {
        return this.inventoryItemQueryService.queryWarehouseInventoryDetailTopN(query, maxRows, Sort.unsorted());
    }

    /**
     * 现库导出候选行装载：行数上限为 {@link InventoryWarehouseDetailExportConstants#INVENTORY_DETAIL_SUM_MAX_ROWS}，
     * 与底部汇总（IF-03）一致，避免单次响应过大。
     *
     * @param query 领域筛选条件，调用方保证非 null
     * @return 行集合
     */
    @Override
    public List<WarehouseInventoryDetailItemModel> selectCurrentDetailForExport(WarehouseInventoryDetailPageQuery query) {
        int cap = InventoryWarehouseDetailExportConstants.INVENTORY_DETAIL_SUM_MAX_ROWS;
        return this.inventoryItemQueryService.queryWarehouseInventoryDetailTopN(query, cap, Sort.unsorted());
    }

    /**
     * 将领域行模型转为列表 DTO 骨架；定价、字典与金额列由应用层 {@code enrichRows} 回填。
     *
     * @param model 读侧行模型；若为 {@code null} 则返回 {@code null}
     * @return 列表行 DTO 或 null
     */
    @Override
    public InventoryDetailListRowDto toListRowDto(WarehouseInventoryDetailItemModel model) {
        if (model == null) {
            return null;
        }
        InventoryDetailListRowDto dto = new InventoryDetailListRowDto();
        dto.setId(model.getId());
        dto.setItemTable(model.getItemTable());
        dto.setItemId(model.getItemId());
        dto.setName(model.getName());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setLotNumber(model.getLotNumber());
        dto.setQuantity(model.getQuantity());
        dto.setUnitCode(model.getUnitCode());
        dto.setWarehouseId(model.getWarehouseId());
        dto.setSupplierId(model.getSupplierId());
        dto.setInventoryStatusEnum(model.getInventoryStatusEnum());
        // BR-INV-DTL-026：列表不展示 InventoryItem.trace_no 逗号串
        dto.setTraceNo(null);
        dto.setRemainingDays(model.getRemainingDays());
        if (model.getExpirationDate() != null) {
            dto.setExpirationDate(GeneralSharedConstants.DATE_TIME_FORMATTER.format(model.getExpirationDate()));
        }
        if (model.getProductionDate() != null) {
            dto.setProductionDate(GeneralSharedConstants.DATE_TIME_FORMATTER.format(model.getProductionDate()));
        }
        return dto;
    }

    /**
     * 将已装配列表行投影为导出行 DTO（包装/最小数量与各自单位分列）。
     *
     * @param row 列表行，调用方保证非 null
     * @return 导出行
     */
    @Override
    public InventoryDetailCurrentExportRowDto toCurrentExportRowDto(InventoryDetailListRowDto row) {
        BigDecimal splitQty = row.getQuantity();
        return new InventoryDetailCurrentExportRowDto()
            .setProductBusNo(row.getProductBusNo())
            .setName(row.getName())
            .setMerchandiseName(row.getMerchandiseName())
            .setCategoryLabel(row.getCategoryLabel())
            .setWarehouseName(row.getWarehouseName())
            .setTotalVolume(row.getTotalVolume())
            .setManufacturer(row.getManufacturer())
            .setLotNumber(row.getLotNumber())
            // 满包整数 + 包装单位；最小量 = 拆零账面 + 拆零单位
            .setPackageQuantity(this.toExportPackageQuantity(splitQty, row.getPackageUnitRatio()))
            .setPackageUnitName(row.getPackageUnitName())
            .setMinUnitQuantity(this.toExportMinUnitQuantity(splitQty))
            .setMinUnitName(row.getSplitUnitName())
            .setPackageUnitRatio(row.getPackageUnitRatio())
            .setLotCostPrice(row.getLotCostPrice())
            .setLotSalePrice(row.getLotSalePrice())
            .setLineCostTotal(row.getLineCostTotal())
            .setLinePriceTotal(row.getLinePriceTotal())
            .setDoseFormLabel(row.getDoseFormLabel())
            .setApprovalNumber(row.getApprovalNumber())
            .setSupplierName(row.getSupplierName())
            .setChrgitmLvLabel(row.getChrgitmLvLabel())
            .setProductionDate(row.getProductionDate())
            .setExpirationDate(row.getExpirationDate())
            .setRemainingDays(row.getRemainingDays());
    }

    /**
     * 将备份分页请求映射为快照领域查询。
     *
     * @param request               分页请求，调用方保证非 null
     * @param tenantId              租户
     * @param resolvedBusNo         已解析批次单号
     * @param effectiveWarehouseIds 门控库房范围
     * @return 快照查询对象
     */
    @Override
    public DocInventoryItemSnapshotPageQuery toSnapshotPageQuery(
        PageInventoryDetailBackupRequest request,
        Long tenantId,
        String resolvedBusNo,
        List<Long> effectiveWarehouseIds
    ) {
        DocInventoryItemSnapshotPageQuery query = new DocInventoryItemSnapshotPageQuery();
        query.setTenantId(tenantId);
        query.setBusNo(resolvedBusNo);
        query.setKeyword(request.getKeyword());
        this.applySnapshotFilters(
            query,
            request.getMedicationCategoryCodes(),
            request.getDeviceCategoryCodes(),
            request.getSupplierIds(),
            request.getChrgitmLvList(),
            request.getKeyDrugType(),
            request.getMedicinePropertyCode(),
            request.getZeroStockOnly(),
            request.getExpireWithinDays(),
            request.getInventoryStatusEnums(),
            request.getItemTable()
        );
        query.setWarehouseIds(effectiveWarehouseIds);
        return query;
    }

    /**
     * 将备份汇总请求映射为快照领域查询。
     *
     * @param request               汇总请求，调用方保证非 null
     * @param tenantId              租户
     * @param resolvedBusNo         已解析批次单号
     * @param effectiveWarehouseIds 门控库房范围
     * @return 快照查询对象
     */
    @Override
    public DocInventoryItemSnapshotPageQuery toSnapshotPageQuery(
        SumInventoryDetailBackupRequest request,
        Long tenantId,
        String resolvedBusNo,
        List<Long> effectiveWarehouseIds
    ) {
        DocInventoryItemSnapshotPageQuery query = new DocInventoryItemSnapshotPageQuery();
        query.setTenantId(tenantId);
        query.setBusNo(resolvedBusNo);
        query.setKeyword(request.getKeyword());
        this.applySnapshotFilters(
            query,
            request.getMedicationCategoryCodes(),
            request.getDeviceCategoryCodes(),
            request.getSupplierIds(),
            request.getChrgitmLvList(),
            request.getKeyDrugType(),
            request.getMedicinePropertyCode(),
            request.getZeroStockOnly(),
            request.getExpireWithinDays(),
            request.getInventoryStatusEnums(),
            request.getItemTable()
        );
        query.setWarehouseIds(effectiveWarehouseIds);
        return query;
    }

    /**
     * 将备份导出请求映射为快照领域查询。
     *
     * @param request               导出请求，调用方保证非 null
     * @param tenantId              租户
     * @param resolvedBusNo         已解析批次单号
     * @param effectiveWarehouseIds 门控库房范围
     * @return 快照查询对象
     */
    @Override
    public DocInventoryItemSnapshotPageQuery toSnapshotPageQuery(
        ExportInventoryDetailBackupRequest request,
        Long tenantId,
        String resolvedBusNo,
        List<Long> effectiveWarehouseIds
    ) {
        DocInventoryItemSnapshotPageQuery query = new DocInventoryItemSnapshotPageQuery();
        query.setTenantId(tenantId);
        query.setBusNo(resolvedBusNo);
        query.setKeyword(request.getKeyword());
        this.applySnapshotFilters(
            query,
            request.getMedicationCategoryCodes(),
            request.getDeviceCategoryCodes(),
            request.getSupplierIds(),
            request.getChrgitmLvList(),
            request.getKeyDrugType(),
            request.getMedicinePropertyCode(),
            request.getZeroStockOnly(),
            request.getExpireWithinDays(),
            request.getInventoryStatusEnums(),
            request.getItemTable()
        );
        query.setWarehouseIds(effectiveWarehouseIds);
        return query;
    }

    /**
     * 备份筛选字段写入快照查询对象。
     *
     * @param query                   目标查询
     * @param medicationCategoryCodes 药品分类
     * @param deviceCategoryCodes     耗材分类
     * @param supplierIds             供应商
     * @param chrgitmLvList           医保等级
     * @param keyDrugType             重点药类型 code（null/空不限）
     * @param medicinePropertyCode    药品性质 code（null/空不限）
     * @param zeroStockOnly           零库存筛选
     * @param expireWithinDays        近效期
     * @param inventoryStatusEnums    供应状态
     * @param itemTable               项目类型（药品/耗材定义表名）；空/null 不限
     */
    private void applySnapshotFilters(
        DocInventoryItemSnapshotPageQuery query,
        List<String> medicationCategoryCodes,
        List<String> deviceCategoryCodes,
        List<Long> supplierIds,
        List<Integer> chrgitmLvList,
        String keyDrugType,
        String medicinePropertyCode,
        Boolean zeroStockOnly,
        Integer expireWithinDays,
        List<String> inventoryStatusEnums,
        String itemTable
    ) {
        query.setMedicationCategoryCodes(medicationCategoryCodes);
        query.setDeviceCategoryCodes(deviceCategoryCodes);
        query.setSupplierIds(supplierIds);
        query.setChrgitmLvs(chrgitmLvList);
        query.setKeyDrugType(keyDrugType);
        query.setMedicinePropertyCode(medicinePropertyCode);
        query.setInventoryStatusEnums(inventoryStatusEnums);
        if (zeroStockOnly != null) {
            query.setZeroStockFlag(zeroStockOnly);
        }
        query.setRemainingDaysLe(expireWithinDays);
        // 项目类型非空时按药品/耗材定义表名收窄
        if (StringUtils.isNotEmpty(itemTable)) {
            query.setItemTable(itemTable.trim());
        }
    }

    /**
     * 快照读模型 → 备份列表 DTO 骨架。
     *
     * @param model 快照行；null 时返回 null
     * @return 备份列表行
     */
    @Override
    public InventoryDetailBackupListRowDto toBackupListRowDto(DocInventoryItemSnapshotRowQueryModel model) {
        if (model == null) {
            return null;
        }
        InventoryDetailBackupListRowDto dto = new InventoryDetailBackupListRowDto();
        dto.setId(model.getInventoryItemId());
        dto.setItemTable(model.getItemTable());
        dto.setItemId(model.getItemId());
        dto.setChargeItemDefinitionId(model.getChargeItemDefinitionId());
        dto.setProductBusNo(model.getProductBusNo());
        dto.setName(model.getName());
        dto.setMerchandiseName(model.getMerchandiseName());
        dto.setTotalVolume(model.getTotalVolume());
        dto.setManufacturer(model.getManufacturer());
        dto.setApprovalNumber(model.getApprovalNumber());
        dto.setYbNo(model.getYbNo());
        // BR-INV-DTL-026：备份列表不展示 traceNo
        dto.setTraceNo(null);
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setLotNumber(model.getLotNumber());
        dto.setWarehouseId(model.getWarehouseId());
        dto.setSupplierId(model.getSupplierId());
        dto.setQuantity(model.getQuantity());
        dto.setPackageUnitRatio(model.getPackageUnitRatio());
        dto.setPackageUnitCode(model.getPackageUnitCode());
        dto.setLotCostPrice(model.getLotNumberCostAmount());
        dto.setLotSalePrice(model.getLotNumberPriceAmount());
        dto.setLineCostTotal(model.getLineCostTotal());
        dto.setLinePriceTotal(model.getLinePriceTotal());
        dto.setRemainingDays(model.getRemainingDays());
        dto.setInventoryStatusEnum(model.getInventoryStatusEnum());
        dto.setChrgitmLv(model.getChrgitmLv());
        dto.setMedicationCategoryCode(model.getMedicationCategoryCode());
        dto.setDeviceCategoryCode(model.getDeviceCategoryCode());
        dto.setDoseFormCode(model.getDoseFormCode());
        if (model.getExpirationDate() != null) {
            dto.setExpirationDate(GeneralSharedConstants.DATE_TIME_FORMATTER.format(model.getExpirationDate()));
        }
        if (model.getProductionDate() != null) {
            dto.setProductionDate(GeneralSharedConstants.DATE_TIME_FORMATTER.format(model.getProductionDate()));
        }
        return dto;
    }

    /**
     * 备份列表行 → 导出行 DTO（包装/最小数量与各自单位分列）。
     *
     * @param row 列表行，调用方保证非 null
     * @return 导出行
     */
    @Override
    public InventoryDetailBackupExportRowDto toBackupExportRowDto(InventoryDetailBackupListRowDto row) {
        BigDecimal splitQty = row.getQuantity();
        return new InventoryDetailBackupExportRowDto()
            .setProductBusNo(row.getProductBusNo())
            .setName(row.getName())
            .setMerchandiseName(row.getMerchandiseName())
            .setCategoryLabel(row.getCategoryLabel())
            .setWarehouseName(row.getWarehouseName())
            .setTotalVolume(row.getTotalVolume())
            .setManufacturer(row.getManufacturer())
            .setLotNumber(row.getLotNumber())
            .setPackageQuantity(this.toExportPackageQuantity(splitQty, row.getPackageUnitRatio()))
            .setPackageUnitName(row.getPackageUnitName())
            .setMinUnitQuantity(this.toExportMinUnitQuantity(splitQty))
            .setMinUnitName(row.getSplitUnitName())
            .setPackageUnitRatio(row.getPackageUnitRatio())
            .setLotCostPrice(row.getLotCostPrice())
            .setLotSalePrice(row.getLotSalePrice())
            .setLineCostTotal(row.getLineCostTotal())
            .setLinePriceTotal(row.getLinePriceTotal())
            .setDoseFormLabel(row.getDoseFormLabel())
            .setApprovalNumber(row.getApprovalNumber())
            .setSupplierName(row.getSupplierName())
            .setChrgitmLvLabel(row.getChrgitmLvLabel())
            .setProductionDate(row.getProductionDate())
            .setExpirationDate(row.getExpirationDate())
            .setRemainingDays(row.getRemainingDays());
    }

    /**
     * 导出包装满包整数：拆零量 ÷ 包装倍数向下取整；倍数缺失时与最小量同值。
     *
     * @param splitQuantity    拆零账面量
     * @param packageUnitRatio 1 包装 = ratio 拆零
     * @return 包装数量整数；拆零量为 null 时 null
     */
    private BigDecimal toExportPackageQuantity(BigDecimal splitQuantity, BigDecimal packageUnitRatio) {
        if (splitQuantity == null) {
            return null;
        }
        BigDecimal ratio = packageUnitRatio;
        // 无有效倍数：包装列与最小列同量，避免空白
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return splitQuantity.setScale(0, RoundingMode.HALF_UP);
        }
        return splitQuantity.divide(ratio, 0, RoundingMode.DOWN);
    }

    /**
     * 导出最小单位数量（拆零账面，整数刻度）。
     *
     * @param splitQuantity 拆零账面量
     * @return 整数 BigDecimal 或 null
     */
    private BigDecimal toExportMinUnitQuantity(BigDecimal splitQuantity) {
        if (splitQuantity == null) {
            return null;
        }
        return splitQuantity.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 现库已装配行 → 快照落库命令（IF-10 · BR-DOC-SNAP-004）。
     *
     * @param row   已 enrich 列表行，调用方保证非 null
     * @param model 领域行，调用方保证非 null
     * @return 快照行命令
     */
    @Override
    public DocInventoryItemSnapshotRowCommand toSnapshotRowCommand(
        InventoryDetailListRowDto row,
        WarehouseInventoryDetailItemModel model
    ) {
        DocInventoryItemSnapshotRowCommand cmd = new DocInventoryItemSnapshotRowCommand();
        cmd.setInventoryItemId(row.getId());
        cmd.setItemTable(row.getItemTable());
        cmd.setItemId(row.getItemId());
        cmd.setChargeItemDefinitionId(row.getChargeItemDefinitionId());
        cmd.setWarehouseId(row.getWarehouseId());
        cmd.setSupplierId(row.getSupplierId());
        cmd.setProductBusNo(row.getProductBusNo());
        cmd.setName(row.getName());
        cmd.setMerchandiseName(row.getMerchandiseName());
        cmd.setTotalVolume(row.getTotalVolume());
        cmd.setManufacturer(row.getManufacturer());
        cmd.setApprovalNumber(row.getApprovalNumber());
        cmd.setYbNo(row.getYbNo());
        cmd.setTraceNo(row.getTraceNo());
        cmd.setPyStr(row.getPyStr());
        cmd.setWbStr(row.getWbStr());
        cmd.setLotNumber(row.getLotNumber());
        cmd.setQuantity(row.getQuantity());
        cmd.setPackageUnitRatio(row.getPackageUnitRatio());
        cmd.setPackageUnitCode(row.getPackageUnitCode());
        cmd.setLotNumberCostAmount(row.getLotCostPrice());
        cmd.setLotNumberPriceAmount(row.getLotSalePrice());
        cmd.setLineCostTotal(row.getLineCostTotal());
        cmd.setLinePriceTotal(row.getLinePriceTotal());
        cmd.setProductionDate(model.getProductionDate());
        cmd.setExpirationDate(model.getExpirationDate());
        cmd.setRemainingDays(row.getRemainingDays());
        cmd.setInventoryStatusEnum(row.getInventoryStatusEnum());
        cmd.setChrgitmLv(row.getChrgitmLv());
        cmd.setMedicationCategoryCode(row.getMedicationCategoryCode());
        cmd.setDeviceCategoryCode(row.getDeviceCategoryCode());
        cmd.setDoseFormCode(row.getDoseFormCode());
        return cmd;
    }

    /**
     * 库存行本库仍在库码读模型 → 弹窗行 DTO 列表（IF-INV-DTL-13）。
     *
     * @param models 领域读模型列表
     * @return 弹窗行 DTO 列表
     */
    @Override
    public List<InventoryItemTraceNumberRowDto> toTraceNumberRowDtoList(List<InventoryItemTraceNumberQueryModel> models) {
        List<InventoryItemTraceNumberRowDto> result = new ArrayList<>();
        if (models == null) {
            return result;
        }
        for (InventoryItemTraceNumberQueryModel model : models) {
            if (model == null) {
                continue;
            }
            InventoryItemTraceNumberRowDto dto = new InventoryItemTraceNumberRowDto();
            dto.setTraceNo(model.getTraceNo());
            dto.setLotNumber(model.getLotNumber());
            dto.setLocationNetSplitQuantity(model.getLocationNetSplitQuantity());
            // 主档拆零剩余（全院浏览 / 与本库净量并存）
            dto.setRemainingSplitQuantity(model.getRemainingSplitQuantity());
            dto.setTraceUnitCode(model.getTraceUnitCode());
            // 主档贴码比：发药待扫换算
            dto.setTraceUnitRatio(model.getTraceUnitRatio());
            dto.setInHospitalStatusEnum(model.getInHospitalStatusEnum());
            result.add(dto);
        }
        return result;
    }
}
