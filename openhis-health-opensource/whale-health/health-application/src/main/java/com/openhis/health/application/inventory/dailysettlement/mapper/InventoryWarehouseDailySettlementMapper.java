/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.mapper;

import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementColumnDto;
import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementExportRowDto;
import com.openhis.health.application.inventory.dailysettlement.dto.DailySettlementListRowDto;
import com.openhis.health.application.inventory.dailysettlement.request.PageDailySettlementRequest;
import com.openhis.health.application.inventory.dailysettlement.response.PageDailySettlementResponse;
import com.openhis.health.application.inventory.dailysettlement.support.DailySettlementWindowContext;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.openhis.health.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.openhis.health.domain.workflow.devicedispense.queries.DeviceDispenseSettlementWindowQuery;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestSettlementWindowQuery;

import java.util.List;

/**
 * 库房日结 Request/Query/DTO 映射（禁止承载业务流程判断）。
 */
public interface InventoryWarehouseDailySettlementMapper {

    /**
     * 分页/导出请求映射为快照汇总查询。
     *
     * @param request     日结请求
     * @param tenantId    租户
     * @param busNo       备份单号
     * @param warehouseId 库位
     * @return 快照查询
     */
    DocInventoryItemSnapshotPageQuery toSnapshotSumQuery(
        PageDailySettlementRequest request,
        Long tenantId,
        String busNo,
        Long warehouseId
    );

    /**
     * 映射供应动账窗查询。
     *
     * @param request       日结请求
     * @param windowContext 时间窗
     * @param typeWhitelist 类型白名单
     * @param tenantId      租户
     * @return 供应窗查询
     */
    SupplyRequestSettlementWindowQuery toSupplyWindowQuery(
        PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext,
        List<String> typeWhitelist,
        Long tenantId
    );

    /**
     * 映射药品正向发药记录查询（仅 {@code COMPLETED} 正向行，不含退药）。
     *
     * @param request          日结请求
     * @param windowContext    时间窗
     * @param outpatientOnly   是否仅门诊正向（与住院互斥）
     * @return 发药记录查询
     */
    MedicationDispenseRecordPageQuery toMedicationDispenseRecordQuery(
        PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext,
        boolean outpatientOnly
    );

    /**
     * 映射药品退药记录查询（仅 {@code REFUND+REFUNDED}，全场景一次拉取）。
     *
     * @param request       日结请求
     * @param windowContext 时间窗
     * @return 退药记录查询
     */
    MedicationDispenseRecordPageQuery toMedicationDispenseRefundRecordQuery(
        PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext
    );

    /**
     * 映射耗材发退药窗查询。
     *
     * @param request       日结请求
     * @param windowContext 时间窗
     * @return 耗材发药窗查询
     */
    DeviceDispenseSettlementWindowQuery toDeviceDispenseWindowQuery(
        PageDailySettlementRequest request,
        DailySettlementWindowContext windowContext
    );

    /**
     * 映射药品目录分页查询。
     *
     * @param request 日结分页请求
     * @return 药品目录查询
     */
    MedicationDefinitionCatalogPageQuery toMedicationCatalogPageQuery(PageDailySettlementRequest request);

    /**
     * 列表行映射为导出行。
     *
     * @param row 列表行
     * @return 导出行；row 为 null 时 null
     */
    DailySettlementExportRowDto toExportRowDto(DailySettlementListRowDto row);

    /**
     * 装配分页响应壳。
     *
     * @param rows              数据行
     * @param total             总行数
     * @param pageNo            页码
     * @param pageSize          页大小
     * @param locationFormEnum  库位形态
     * @param itemTable         解析后的物品主表
     * @param columns           动态列
     * @param activeColumnCodes 列 code
     * @return 分页响应
     */
    PageDailySettlementResponse toPageResponse(
        List<DailySettlementListRowDto> rows,
        long total,
        int pageNo,
        int pageSize,
        String locationFormEnum,
        String itemTable,
        List<DailySettlementColumnDto> columns,
        List<String> activeColumnCodes
    );

    /**
     * 动态列 Excel 写出。
     *
     * @param exportRows 导出行
     * @param columns    动态列元数据
     * @param sheetName  工作表名
     * @return xlsx 字节
     */
    byte[] buildExcelBytes(
        List<DailySettlementExportRowDto> exportRows,
        List<DailySettlementColumnDto> columns,
        String sheetName
    );
}
