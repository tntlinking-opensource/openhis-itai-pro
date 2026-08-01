/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.returntowarehouse.dto;

import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药房退库单详情明细行。
 *
 * <p>{@code unitCodeDictText}/{@code productCatalog} 由应用层按产品读模型批量命中后填充，供前端
 * SPLIT→录入单位换算与多计量带下拉；未命中时保持 null。</p>
 */
@Getter
@Setter
public class ReturnToWarehouseDetailLineDto {

    /** 供应请求明细主键 {@code wkf_supply_request.id}。 */
    @Schema(description = "明细主键 id")
    private Long id;
    /** 物品来源表名。 */
    @Schema(description = "物品表 itemTable")
    private String itemTable;
    /** 物品定义主键。 */
    @Schema(description = "物品 itemId")
    private Long itemId;
    /** 退库数量（拆零量）。 */
    @Schema(description = "退库数量 itemQuantity")
    private BigDecimal itemQuantity;
    /** 计量单位编码。 */
    @Schema(description = "计量单位 unitCode")
    private String unitCode;
    /** 计量单位展示名（术语/产品槽字典文案；禁止回落裸 unitCode）。 */
    @Schema(description = "计量单位展示名 unitCodeDictText")
    private String unitCodeDictText;
    /** 批号。 */
    @Schema(description = "批号 lotNumber")
    private String lotNumber;
    /** 效期起。 */
    @Schema(description = "效期起 startTime")
    private LocalDateTime startTime;
    /** 效期止。 */
    @Schema(description = "效期止 endTime")
    private LocalDateTime endTime;
    /** 追溯码（逗号分隔多码）。 */
    @Schema(description = "追溯码 traceNo")
    private String traceNo;
    /** 业务发生/制单时间。 */
    @Schema(description = "发生时间 occurrenceTime")
    private LocalDateTime occurrenceTime;
    /** 退库原因说明。 */
    @Schema(description = "退库原因 reason")
    private String reason;
    /** 备注。 */
    @Schema(description = "备注 remake")
    private String remake;
    /** 产品展示名称（读模型富化）。 */
    @Schema(description = "产品名称 productName")
    private String productName;
    /** 产品目录（含 unitBands，详情单位回显与计量带切换用）。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
