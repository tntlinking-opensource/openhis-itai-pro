/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.warehouse.inboundreceipt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogEditorHintsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药库入库单详情明细行（IF-WHS-IN-03）。
 *
 * <p>写模型字段来自 {@code wkf_supply_request} 行，含效期区间 {@code startTime}/{@code endTime}（库列
 * {@code start_time}/{@code end_time}）；{@code productName}/{@code specification}/{@code manufacturer}、
 * {@code unitCodeDictText} 等展示扩展仅在应用层批量命中产品定义读模型时由 Mapper 填充，未命中时为
 * {@code null}（B.2 分层：不在 Mapper 内注入查询服务）。{@link #productCatalog} 与选品列表在单位槽与参考价维度对齐。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "药库入库单详情明细行")
public class InboundReceiptDetailLineDto {

    /** 供应请求行主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应请求行主键")
    private Long id;

    /** 物品来源表名。 */
    @Schema(description = "物品来源表名")
    private String itemTable;

    @JsonSerialize(using = ToStringSerializer.class)
    /** 物品定义主键。 */
    @Schema(description = "物品定义主键")
    private Long itemId;

    /** 物品名称。 */
    @Schema(description = "物品名称")
    private String productName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specification;

    /** 厂家合一展示。 */
    @Schema(description = "厂家合一展示")
    private String manufacturer;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 计量单位编码。 */
    @Schema(description = "计量单位编码")
    private String unitCode;

    /** 计量单位展示名。 */
    @Schema(description = "计量单位展示名")
    private String unitCodeDictText;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 效期起点。 */
    @Schema(description = "效期起点")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 效期止点。 */
    @Schema(description = "效期止点")
    private LocalDateTime endTime;

    /** 追溯码。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /** 行备注。 */
    @Schema(description = "行备注")
    private String remake;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal price;

    /** 行总价。 */
    @Schema(description = "行总价")
    private BigDecimal totalPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    /** 业务发生时间。 */
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;

    /** 产品目录 productCatalog。 */
    @Schema(description = "产品目录 productCatalog")
    private ProductDefinitionCatalogEditorHintsDto productCatalog;
}
