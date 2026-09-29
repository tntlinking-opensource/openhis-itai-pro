/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 医技计费耗材选品目录行（AS-MT-CHG-001 IF-MT-CHG-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技耗材选品目录行")
public class MedicalTechDeviceCatalogRowDto {

    /** 耗材定义主键 {@code adm_device_definition.id}。 */
    @Schema(description = "耗材定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long definitionId;

    /** 品名。 */
    @Schema(description = "品名")
    private String itemName;

    /** 项目编码。 */
    @Schema(description = "项目编码")
    private String itemCode;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 单位码（行头默认；耗材优先拆零槽）。 */
    @Schema(description = "单位码")
    private String unitCode;

    /** 单位名称。 */
    @Schema(description = "单位名称")
    private String unitName;

    /**
     * 可切换单位槽（包装/拆零等）；与护士划价同构，供已选清单改单位。
     */
    @Schema(description = "单位槽列表")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 展示单价（与行头 unitCode 同口径）。 */
    @Schema(description = "展示单价")
    private BigDecimal unitPrice;

    /** 默认/选定库房主键。 */
    @Schema(description = "库房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 可选库存数量（只读提示；划价按护士同开关扣库后数量会变）。 */
    @Schema(description = "可选库存数量")
    private BigDecimal availableQty;

    /**
     * 库存批号（库存驱动选品一行一批号；写路径回传 {@code lotNumber} 锁定批号价）。
     */
    @Schema(description = "库存批号")
    private String lotNumber;
}
