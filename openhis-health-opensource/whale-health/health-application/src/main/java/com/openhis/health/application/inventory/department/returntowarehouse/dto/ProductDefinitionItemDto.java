/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.department.returntowarehouse.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 科室退库选品分页行（源科室药房库存批号级展开，IF-DEP-RTW-11）。
 *
 * <p>展示用批号维进价取 {@code LOT_NUMBER_COST}（选品槽），不写回供应请求行价。</p>
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "科室退库选品分页行")
public class ProductDefinitionItemDto {

    /** 物品来源物理表名（药品/耗材定义表）。 */
    @Schema(description = "物品来源物理表名")
    private String productTableName;

    /** 产品定义主键（Snowflake；JSON 序列化为字符串防精度丢失）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    /** 目录业务编号 {@code productBusNo}，选品弹窗检索/回显用。 */
    @Schema(description = "目录业务编号 productBusNo")
    private String productBusNo;

    /** 物品名称（产品定义展示名）。 */
    @Schema(description = "物品名称")
    private String productName;

    /** 规格/装量展示（取产品定义 totalVolume 等）。 */
    @Schema(description = "规格")
    private String specification;

    /** 生产厂家/产地合一展示。 */
    @Schema(description = "厂家合一")
    private String manufacturer;

    /** 库存批号（批号行级展开键之一）。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 批号效期（库存行 expiration）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "效期")
    private LocalDateTime expirationDate;

    /** 源科室药房该库存行可用量（拆零口径展示）。 */
    @Schema(description = "源科室药房可用量")
    private BigDecimal availableQuantity;

    /** 单位四槽（目录计量带；选品回填数量/单位换算用）。 */
    @Schema(description = "单位四槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /**
     * 批号维进价展示（{@code LOT_NUMBER_COST}，随 lotNumber）；仅选品展示，不落供应请求。
     */
    @Schema(description = "批号维进价展示（LOT_NUMBER_COST，随 lotNumber）")
    private BigDecimal purchaseUnitPrice;

    /** 库存行主键（批号行级；可用量按此 id 回填）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库存行主键")
    private Long inventoryItemId;
}
