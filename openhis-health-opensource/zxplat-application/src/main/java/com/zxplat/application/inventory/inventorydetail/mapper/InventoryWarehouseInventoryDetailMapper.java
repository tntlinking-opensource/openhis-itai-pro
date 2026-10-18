/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.inventorydetail.mapper;

import com.zxplat.application.inventory.inventorydetail.dto.InventoryDetailBackupExportRowDto;
import com.zxplat.application.inventory.inventorydetail.dto.InventoryDetailBackupListRowDto;
import com.zxplat.application.inventory.inventorydetail.dto.InventoryDetailCurrentExportRowDto;
import com.zxplat.application.inventory.inventorydetail.dto.InventoryDetailListRowDto;
import com.zxplat.application.inventory.inventorydetail.dto.InventoryItemTraceNumberRowDto;
import com.zxplat.domain.custom.tracenumber.queries.model.InventoryItemTraceNumberQueryModel;
import com.zxplat.application.inventory.inventorydetail.request.ExportInventoryDetailBackupRequest;
import com.zxplat.application.inventory.inventorydetail.request.ExportInventoryDetailCurrentRequest;
import com.zxplat.application.inventory.inventorydetail.request.PageInventoryDetailBackupRequest;
import com.zxplat.application.inventory.inventorydetail.request.PageInventoryDetailCurrentRequest;
import com.zxplat.application.inventory.inventorydetail.request.SumInventoryDetailBackupRequest;
import com.zxplat.application.inventory.inventorydetail.request.SumInventoryDetailCurrentRequest;
import com.zxplat.domain.document.inventoryitemsnapshot.command.DocInventoryItemSnapshotRowCommand;
import com.zxplat.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.zxplat.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.zxplat.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 药库库存明细：HTTP 契约与领域分页查询、导出行 DTO 之间的映射。
 *
 * <p>禁止承载业务流程判断；现库数据装载委托 {@link com.zxplat.domain.workflow.inventoryitem.service.InventoryItemQueryService}
 * （JPA {@code Specification} 下推，非 UNION SQL）。</p>
 */
public interface InventoryWarehouseInventoryDetailMapper {

    /**
     * 将分页请求与门控后的库房范围装配为领域分页查询。
     *
     * @param request               HTTP 入参
     * @param effectiveWarehouseIds 应用层 Gate 解析后的库房主键范围，允许为空
     * @return 领域查询对象
     */
    WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        PageInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 将汇总请求装配为领域查询（不含分页语义）。
     *
     * @param request               HTTP 入参
     * @param effectiveWarehouseIds 门控后的库房范围
     * @return 领域查询对象
     */
    WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        SumInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 将导出请求装配为领域查询。
     *
     * @param request               HTTP 入参
     * @param effectiveWarehouseIds 门控后的库房范围
     * @return 领域查询对象
     */
    WarehouseInventoryDetailPageQuery toWarehouseDetailPageQuery(
        ExportInventoryDetailCurrentRequest request,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 现库明细分页装载（IF-INV-DTL-02 Pre）。
     *
     * @param query    领域筛选
     * @param pageable 分页与排序
     * @return 分页库存行
     */
    Page<WarehouseInventoryDetailItemModel> selectCurrentDetailPage(
        WarehouseInventoryDetailPageQuery query,
        Pageable pageable
    );

    /**
     * 现库汇总候选行装载（IF-INV-DTL-03 Pre）。
     *
     * @param query   领域筛选
     * @param maxRows 最大行数
     * @return 行集合
     */
    List<WarehouseInventoryDetailItemModel> selectCurrentDetailForSum(WarehouseInventoryDetailPageQuery query, int maxRows);

    /**
     * 现库导出候选行装载（IF-INV-DTL-06 Pre）。
     *
     * @param query 领域筛选
     * @return 行集合（上限由应用层控制）
     */
    List<WarehouseInventoryDetailItemModel> selectCurrentDetailForExport(WarehouseInventoryDetailPageQuery query);

    /**
     * 领域行 → 列表 DTO 骨架字段（定价与字典列由应用层后续回填）。
     *
     * @param model 领域行，调用方保证非空
     * @return DTO
     */
    InventoryDetailListRowDto toListRowDto(WarehouseInventoryDetailItemModel model);

    /**
     * 已装配列表行 → 现库导出行（IF-INV-DTL-06 Asm）。
     *
     * @param row 列表行，调用方保证非 null
     * @return 导出行 DTO
     */
    InventoryDetailCurrentExportRowDto toCurrentExportRowDto(InventoryDetailListRowDto row);

    /**
     * 将备份分页请求装配为快照领域查询。
     *
     * @param request               HTTP 入参
     * @param tenantId              租户
     * @param resolvedBusNo         已解析的备份批次单号
     * @param effectiveWarehouseIds 门控后的库房范围
     * @return 快照分页查询
     */
    DocInventoryItemSnapshotPageQuery toSnapshotPageQuery(
        PageInventoryDetailBackupRequest request,
        Long tenantId,
        String resolvedBusNo,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 将备份汇总请求装配为快照领域查询。
     *
     * @param request               HTTP 入参
     * @param tenantId              租户
     * @param resolvedBusNo         已解析的备份批次单号
     * @param effectiveWarehouseIds 门控后的库房范围
     * @return 快照查询
     */
    DocInventoryItemSnapshotPageQuery toSnapshotPageQuery(
        SumInventoryDetailBackupRequest request,
        Long tenantId,
        String resolvedBusNo,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 将备份导出请求装配为快照领域查询。
     *
     * @param request               HTTP 入参
     * @param tenantId              租户
     * @param resolvedBusNo         已解析的备份批次单号
     * @param effectiveWarehouseIds 门控后的库房范围
     * @return 快照查询
     */
    DocInventoryItemSnapshotPageQuery toSnapshotPageQuery(
        ExportInventoryDetailBackupRequest request,
        Long tenantId,
        String resolvedBusNo,
        List<Long> effectiveWarehouseIds
    );

    /**
     * 快照读模型 → 备份列表 DTO 骨架（展示列由应用层 enrich）。
     *
     * @param model 快照行读模型
     * @return 备份列表行
     */
    InventoryDetailBackupListRowDto toBackupListRowDto(DocInventoryItemSnapshotRowQueryModel model);

    /**
     * 备份列表行 → 导出行 DTO。
     *
     * @param row 备份列表行
     * @return 导出行
     */
    InventoryDetailBackupExportRowDto toBackupExportRowDto(InventoryDetailBackupListRowDto row);

    /**
     * 现库已装配列表行 → 快照落库行命令（IF-10）。
     *
     * @param row   已 enrich 的列表行
     * @param model 领域行（提供 LocalDateTime 效期等）
     * @return 快照行命令
     */
    DocInventoryItemSnapshotRowCommand toSnapshotRowCommand(
        InventoryDetailListRowDto row,
        WarehouseInventoryDetailItemModel model
    );

    /**
     * 库存行本库仍在库码读模型 → 弹窗行 DTO 列表（IF-INV-DTL-13 Asm）。
     *
     * @param models 领域读模型列表
     * @return 弹窗行 DTO 列表；{@code null} 或空输入返回空列表
     */
    List<InventoryItemTraceNumberRowDto> toTraceNumberRowDtoList(List<InventoryItemTraceNumberQueryModel> models);
}
