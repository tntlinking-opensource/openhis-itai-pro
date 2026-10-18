/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.periodbalancedetail.mapper;

import com.zxplat.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailColumnDto;
import com.zxplat.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailExportRowDto;
import com.zxplat.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailListRowDto;
import com.zxplat.application.inventory.periodbalancedetail.request.PagePeriodBalanceDetailRequest;
import com.zxplat.application.inventory.periodbalancedetail.response.PagePeriodBalanceDetailResponse;
import com.zxplat.application.inventory.periodbalancedetail.support.PeriodBalanceDetailWindowContext;
import com.zxplat.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.zxplat.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.zxplat.domain.medication.medicationdispense.query.MedicationDispenseRecordPageQuery;
import com.zxplat.domain.workflow.devicedispense.queries.DeviceDispenseSettlementWindowQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyRequestSettlementWindowQuery;
import com.zxplat.framework.core.application.dto.ExcelExportColumnDto;

import java.util.List;

/**
 * 库房期初期末明细 Request/Query/DTO 映射（禁止承载业务流程判断）。
 */
public interface InventoryPeriodBalanceDetailMapper {

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
        PagePeriodBalanceDetailRequest request,
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
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailWindowContext windowContext,
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
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailWindowContext windowContext,
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
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailWindowContext windowContext
    );

    /**
     * 映射耗材发退药窗查询。
     *
     * @param request       日结请求
     * @param windowContext 时间窗
     * @return 耗材发药窗查询
     */
    DeviceDispenseSettlementWindowQuery toDeviceDispenseWindowQuery(
        PagePeriodBalanceDetailRequest request,
        PeriodBalanceDetailWindowContext windowContext
    );

    /**
     * 映射药品目录分页查询。
     *
     * @param request 日结分页请求
     * @return 药品目录查询
     */
    MedicationDefinitionCatalogPageQuery toMedicationCatalogPageQuery(PagePeriodBalanceDetailRequest request);

    /**
     * 列表行映射为导出行。
     *
     * @param row 列表行
     * @return 导出行；row 为 null 时 null
     */
    PeriodBalanceDetailExportRowDto toExportRowDto(PeriodBalanceDetailListRowDto row);

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
    PagePeriodBalanceDetailResponse toPageResponse(
        List<PeriodBalanceDetailListRowDto> rows,
        long total,
        int pageNo,
        int pageSize,
        String locationFormEnum,
        String itemTable,
        List<PeriodBalanceDetailColumnDto> columns,
        List<String> activeColumnCodes
    );

    /**
     * 动态列 Excel 写出（可按前端列清单裁剪）。
     *
     * @param exportRows     导出行
     * @param columns        动态列元数据
     * @param sheetName      工作表名
     * @param exportColumns  前端自选列；null/空则全部动态列
     * @return xlsx 字节
     */
    byte[] buildExcelBytes(
        List<PeriodBalanceDetailExportRowDto> exportRows,
        List<PeriodBalanceDetailColumnDto> columns,
        String sheetName,
        List<ExcelExportColumnDto> exportColumns
    );
}
