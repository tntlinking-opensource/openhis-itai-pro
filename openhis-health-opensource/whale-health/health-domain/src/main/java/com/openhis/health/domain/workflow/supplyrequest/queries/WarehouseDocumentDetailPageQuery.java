/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库房单据明细分页查询入参（查 {@code wkf_supply_request} 明细行；替代 RM {@code WarehouseDocumentDetailPageQuery}）。
 *
 * <p>分页与通用关键字见 {@link AbstractPageQuery}；业务申请时刻窗使用 {@link #applyTimeStart}/{@link #applyTimeEnd}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WarehouseDocumentDetailPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 单据号精确匹配（可选）。 */
    private String busNo;

    /** 单据类型筛选（可选）；与 {@link #typeEnums} 互斥，本字段非空时优先。 */
    private String typeEnum;

    /**
     * 单据类型多选（IN）；与 {@link #typeEnum} 互斥。
     */
    private List<String> typeEnums;

    /** 单据状态筛选（可选）。 */
    private String statusEnum;

    /** 源位置 ID 筛选（可选）。 */
    private Long sourceLocationId;

    /**
     * 源位置 ID 多选（IN），与 {@link #sourceLocationId} 互斥。
     */
    private List<Long> sourceLocationIds;

    /** 租户筛选（可选）。 */
    private Long tenantId;

    /** 目标位置 ID 筛选（可选）。 */
    private Long purposeLocationId;

    /**
     * 目标位置 ID 多选（IN），与 {@link #purposeLocationId} 互斥。
     */
    private List<Long> purposeLocationIds;

    /** 物品 ID 筛选（可选）。 */
    private Long itemId;

    /** 批号精确匹配（可选）；与 {@link #lotNumberKeyword} 互斥，关键字非空时优先 LIKE。 */
    private String lotNumber;

    /**
     * 批号模糊关键字（可选）；对 {@code lot_number} 不区分大小写 LIKE。
     */
    private String lotNumberKeyword;

    /** 发放状态筛选（可选）；主表查询暂不填充发放侧字段，保留入参兼容 RM 契约。 */
    private String deliveryStatusEnum;

    /** 申请时间起（可选）。 */
    private LocalDateTime applyTimeStart;

    /** 申请时间止（可选）。 */
    private LocalDateTime applyTimeEnd;

    /** 审批时间起（可选）；闭区间下界，映射 {@code approval_time}。 */
    private LocalDateTime approvalTimeStart;

    /** 审批时间止（可选）；闭区间上界，映射 {@code approval_time}。 */
    private LocalDateTime approvalTimeEnd;

    /**
     * 品名/编码/拼音/五笔关键字（可选）；应用层经 {@code InventoryCatalogFilter} 解析为
     * {@link #resolvedMedicationDefinitionIdsForKeyword} / {@link #resolvedDeviceDefinitionIdsForKeyword} 后再查询。
     */
    private String productKeyword;

    /**
     * 关键字预解析的药品定义主键（{@code item_table=med_medication_definition}）；
     * 由 {@code InventoryCatalogFilter#enrichDocumentDetailKeywordDefinitionIds} 写入。
     */
    private List<Long> resolvedMedicationDefinitionIdsForKeyword;

    /**
     * 关键字预解析的耗材定义主键（{@code item_table=adm_device_definition}）；
     * 由 {@code InventoryCatalogFilter#enrichDocumentDetailKeywordDefinitionIds} 写入。
     */
    private List<Long> resolvedDeviceDefinitionIdsForKeyword;
}
