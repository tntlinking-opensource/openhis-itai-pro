/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.mapper;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.*;

import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementColumnDto;
import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementExportRowDto;
import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementListRowDto;
import com.openhis.health.application.inventory.dailysettlement.request.PageDailySettlementRequest;
import com.openhis.health.application.inventory.dailysettlement.response.PageDailySettlementResponse;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementWindowContext;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.share.constants.InventoryWarehouseDailySettlementExportConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordConstants;
import com.openhis.health.domain.share.constants.PharmacyDispenseRecordExportConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.workflow.devicedispense.queries.DeviceDispenseSettlementWindowQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestSettlementWindowQuery;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.whale.core.application.util.TabularColumnDef;
import com.openhis.whale.core.application.util.TabularExcelExporter;

/**
 * {@link InventoryWarehouseDailySettlementMapper} 实现。
 */
@Component
public class InventoryWarehouseDailySettlementMapperImpl implements InventoryWarehouseDailySettlementMapper {

    /**
     * 分页/导出请求映射为快照汇总查询。
     *
     * @param request 日结请求
     * @param tenantId 租户
     * @param busNo 备份单号
     * @param warehouseId 库位
     * @return 快照查询
     */
    @Override
    public DocInventoryItemSnapshotPageQuery toSnapshotSumQuery(PageDailySettlementRequest request, Long tenantId,
        String busNo, Long warehouseId) {
        DocInventoryItemSnapshotPageQuery query = new DocInventoryItemSnapshotPageQuery();
        query.setTenantId(tenantId);
        query.setBusNo(busNo);
        query.setItemTable(request.getItemTable());
        if (warehouseId != null) {
            query.setWarehouseIds(Collections.singletonList(warehouseId));
        }
        return query;
    }

    /**
     * 映射供应动账窗查询。
     *
     * @param request 日结请求
     * @param windowContext 时间窗
     * @param typeWhitelist 类型白名单
     * @param tenantId 租户
     * @return 供应窗查询
     */
    @Override
    public SupplyRequestSettlementWindowQuery toSupplyWindowQuery(PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext, List<String> typeWhitelist, Long tenantId) {
        SupplyRequestSettlementWindowQuery query = new SupplyRequestSettlementWindowQuery();
        query.setWarehouseId(request.getLocationId());
        query.setItemTable(request.getItemTable());
        query.setTypeEnumWhitelist(typeWhitelist);
        query.setTenantId(tenantId);
        if (windowContext != null) {
            query.setApprovalWindowStartInclusive(windowContext.getApprovalWindowStartInclusive());
            query.setApprovalWindowEndInclusive(windowContext.getApprovalWindowEndInclusive());
        }
        return query;
    }

    /**
     * 映射药品正向发药记录查询（{@code RECORD_TYPE_DISPENSE}，不含退药行）。
     *
     * @param request 日结请求
     * @param windowContext 时间窗
     * @param outpatientOnly 是否仅门诊正向
     * @return 发药记录查询
     */
    @Override
    public MedicationDispenseRecordPageQuery toMedicationDispenseRecordQuery(PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext, boolean outpatientOnly) {
        MedicationDispenseRecordPageQuery query = new MedicationDispenseRecordPageQuery();
        query.setLocationId(request.getLocationId());
        query.setPageSize(Integer.valueOf(PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS));
        query.setRecordTypeCode(PharmacyDispenseRecordConstants.RECORD_TYPE_DISPENSE);
        if (outpatientOnly) {
            query.setOutpatientMedicationOnly(Boolean.TRUE);
        } else {
            query.setInpatientMedicationOnly(Boolean.TRUE);
        }
        if (windowContext != null) {
            query.setStartDate(windowContext.getApprovalWindowStartInclusive());
            query.setEndDate(windowContext.getApprovalWindowEndInclusive());
        }
        return query;
    }

    /**
     * 映射药品退药记录查询（{@code RECORD_TYPE_REFUND}，全场景单通道，避免双路 ALL 重复统计退药）。
     *
     * @param request 日结请求
     * @param windowContext 时间窗
     * @return 退药记录查询
     */
    @Override
    public MedicationDispenseRecordPageQuery toMedicationDispenseRefundRecordQuery(
        PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext
    ) {
        MedicationDispenseRecordPageQuery query = new MedicationDispenseRecordPageQuery();
        query.setLocationId(request.getLocationId());
        query.setPageSize(Integer.valueOf(PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS));
        query.setRecordTypeCode(PharmacyDispenseRecordConstants.RECORD_TYPE_REFUND);
        if (windowContext != null) {
            query.setStartDate(windowContext.getApprovalWindowStartInclusive());
            query.setEndDate(windowContext.getApprovalWindowEndInclusive());
        }
        return query;
    }

    /**
     * 映射耗材发退药窗查询。
     *
     * @param request 日结请求
     * @param windowContext 时间窗
     * @return 耗材发药窗查询
     */
    @Override
    public DeviceDispenseSettlementWindowQuery toDeviceDispenseWindowQuery(PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext) {
        DeviceDispenseSettlementWindowQuery query = new DeviceDispenseSettlementWindowQuery();
        query.setLocationId(request.getLocationId());
        List<String> statuses = new ArrayList<>();
        statuses.add(DispenseStatus.COMPLETED.getCode());
        statuses.add(DispenseStatus.REFUNDED.getCode());
        query.setStatusEnumWhitelist(statuses);
        if (windowContext != null) {
            query.setDispenseWindowStartInclusive(windowContext.getApprovalWindowStartInclusive());
            query.setDispenseWindowEndInclusive(windowContext.getApprovalWindowEndInclusive());
        }
        return query;
    }

    /**
     * 映射药品目录分页查询。
     *
     * @param request 日结分页请求
     * @return 药品目录查询
     */
    @Override
    public MedicationDefinitionCatalogPageQuery toMedicationCatalogPageQuery(PageDailySettlementRequest request) {
        MedicationDefinitionCatalogPageQuery query = new MedicationDefinitionCatalogPageQuery();
        PageQueryBinder.bind(request, query);
        return query;
    }

    /**
     * 列表行映射为导出行。
     *
     * @param row 列表行
     * @return 导出行；row 为 null 时 null
     */
    @Override
    public DailySettlementExportRowDto toExportRowDto(DailySettlementListRowDto row) {
        if (row == null) {
            return null;
        }
        DailySettlementExportRowDto exportRow = new DailySettlementExportRowDto();
        BeanUtils.copyProperties(row, exportRow);
        return exportRow;
    }

    /**
     * 装配分页响应壳。
     *
     * @param rows 数据行
     * @param total 总行数
     * @param pageNo 页码
     * @param pageSize 页大小
     * @param locationFormEnum 库位形态
     * @param columns 动态列
     * @param activeColumnCodes 列 code
     * @return 分页响应
     */
    @Override
    public PageDailySettlementResponse toPageResponse(List<DailySettlementListRowDto> rows, long total, int pageNo,
        int pageSize, String locationFormEnum, String itemTable, List<DailySettlementColumnDto> columns,
        List<String> activeColumnCodes) {
        PageDailySettlementResponse response = new PageDailySettlementResponse();
        response.setTotal(Long.valueOf(total));
        response.setPageNo(Integer.valueOf(pageNo));
        response.setPageSize(Integer.valueOf(pageSize));
        response.setLocationFormEnum(locationFormEnum);
        response.setItemTable(itemTable);
        response.setColumns(columns);
        response.setActiveColumnCodes(activeColumnCodes);
        response.setRows(rows);
        return response;
    }

    /**
     * 动态列 Excel 写出。
     *
     * @param exportRows 导出行
     * @param columns 动态列元数据
     * @param sheetName 工作表名
     * @return xlsx 字节
     */
    @Override
    public byte[] buildExcelBytes(List<DailySettlementExportRowDto> exportRows, List<DailySettlementColumnDto> columns,
        String sheetName) {
        List<TabularColumnDef> tabularColumns = new ArrayList<>();
        this.appendFixedExportColumns(tabularColumns);
        if (columns != null) {
            for (DailySettlementColumnDto column : columns) {
                if (column == null || StringUtils.isEmpty(column.getColumnCode())) {
                    continue;
                }
                tabularColumns.add(new TabularColumnDef(column.getColumnCode(), column.getColumnTitle()));
            }
        }
        this.appendBalanceExportColumns(tabularColumns);
        List<Map<String, Object>> rowMaps = new ArrayList<>();
        if (exportRows != null) {
            for (DailySettlementExportRowDto exportRow : exportRows) {
                rowMaps.add(this.toRowMap(exportRow, tabularColumns));
            }
        }
        return TabularExcelExporter.exportToXlsxBytes(sheetName, tabularColumns, rowMaps);
    }

    /**
     * 固定品种/期初期末列。
     *
     * @param tabularColumns 列定义列表
     */
    private void appendFixedExportColumns(List<TabularColumnDef> tabularColumns) {
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_ITEM_NO,
            InventoryWarehouseDailySettlementExportConstants.LABEL_ITEM_NO));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_PRODUCT_NAME,
            InventoryWarehouseDailySettlementExportConstants.LABEL_PRODUCT_NAME));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_ITEM_NAME,
            InventoryWarehouseDailySettlementExportConstants.LABEL_ITEM_NAME));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_SPEC_TEXT,
            InventoryWarehouseDailySettlementExportConstants.LABEL_SPEC_TEXT));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_MANUFACTURER_TEXT,
                InventoryWarehouseDailySettlementExportConstants.LABEL_MANUFACTURER_TEXT));
        tabularColumns.add(new TabularColumnDef(
            InventoryWarehouseDailySettlementExportConstants.FIELD_BEGIN_COST_PACKAGE_UNIT_PRICE,
            InventoryWarehouseDailySettlementExportConstants.LABEL_BEGIN_COST_PACKAGE_UNIT_PRICE));
        tabularColumns.add(new TabularColumnDef(
            InventoryWarehouseDailySettlementExportConstants.FIELD_BEGIN_QUANTITY,
            InventoryWarehouseDailySettlementExportConstants.LABEL_BEGIN_QUANTITY));
        tabularColumns
            .add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_BEGIN_COST_AMOUNT,
                InventoryWarehouseDailySettlementExportConstants.LABEL_BEGIN_COST_AMOUNT));
        tabularColumns.add(new TabularColumnDef(
            InventoryWarehouseDailySettlementExportConstants.FIELD_END_COST_PACKAGE_UNIT_PRICE,
            InventoryWarehouseDailySettlementExportConstants.LABEL_END_COST_PACKAGE_UNIT_PRICE));
        tabularColumns.add(new TabularColumnDef(
            InventoryWarehouseDailySettlementExportConstants.FIELD_END_QUANTITY,
            InventoryWarehouseDailySettlementExportConstants.LABEL_END_QUANTITY));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_END_COST_AMOUNT,
            InventoryWarehouseDailySettlementExportConstants.LABEL_END_COST_AMOUNT));
    }

    /**
     * 账平/盈亏差列。
     *
     * @param tabularColumns 列定义列表
     */
    private void appendBalanceExportColumns(List<TabularColumnDef> tabularColumns) {
        tabularColumns
            .add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_COMPUTED_END_QUANTITY,
                InventoryWarehouseDailySettlementExportConstants.LABEL_COMPUTED_END_QUANTITY));
        tabularColumns
            .add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_COMPUTED_END_COST_AMOUNT,
                InventoryWarehouseDailySettlementExportConstants.LABEL_COMPUTED_END_COST_AMOUNT));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_QUANTITY_DIFF,
            InventoryWarehouseDailySettlementExportConstants.LABEL_QUANTITY_DIFF));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_COST_AMOUNT_DIFF,
            InventoryWarehouseDailySettlementExportConstants.LABEL_COST_AMOUNT_DIFF));
        tabularColumns.add(new TabularColumnDef(InventoryWarehouseDailySettlementExportConstants.FIELD_IS_BALANCE,
            InventoryWarehouseDailySettlementExportConstants.LABEL_IS_BALANCE));
    }

    /**
     * 导出行转 Map（按列 code 反射取值）。
     *
     * @param exportRow 导出行
     * @param tabularColumns 列定义
     * @return 行 Map
     */
    private Map<String, Object> toRowMap(DailySettlementExportRowDto exportRow, List<TabularColumnDef> tabularColumns) {
        Map<String, Object> map = new HashMap<>();
        if (exportRow == null) {
            return map;
        }
        for (TabularColumnDef column : tabularColumns) {
            Object value = this.readProperty(exportRow, column.field());
            map.put(column.field(), value);
        }
        return map;
    }

    /**
     * 读取 DTO 属性值。
     *
     * @param target 目标对象
     * @param fieldName 属性名
     * @return 属性值
     */
    private Object readProperty(Object target, String fieldName) {
        if (target == null || StringUtils.isEmpty(fieldName)) {
            return null;
        }
        try {
            PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(target.getClass(), fieldName);
            if (descriptor == null) {
                return null;
            }
            Method readMethod = descriptor.getReadMethod();
            if (readMethod == null) {
                return null;
            }
            return readMethod.invoke(target);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
