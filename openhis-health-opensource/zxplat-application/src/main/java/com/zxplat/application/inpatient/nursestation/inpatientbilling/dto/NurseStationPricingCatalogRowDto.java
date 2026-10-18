/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 护士划价选品目录行（IF-IP-PRC-01，BR-IP-PRC-017）。
 *
 * <p>耗材按「品 × 批号」展开一行（有可用净库存时）；诊疗仍按产品定义一行。
 * 前端选品后组装 {@link NurseStationPricingWriteItemDto} 提交 IF-02/04；套餐子项由服务端写路径展开。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "护士划价选品目录行")
public class NurseStationPricingCatalogRowDto {

    @Schema(description = "产品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 产品定义主键。 */
    private Long productDefinitionId;

    /** 产品物理表名。 */
    @Schema(description = "产品物理表名")
    private String productTableName;

    /** 业务编码。 */
    @Schema(description = "业务编码")
    private String busNo;

    /** 展示名称。 */
    @Schema(description = "展示名称")
    private String name;

    /**
     * 规格/装量展示（产品定义 {@code total_volume}，如 {@code 10mg*12片}）。
     */
    @Schema(description = "规格/装量展示")
    private String totalVolume;

    /**
     * 货位展示名（耗材优先库存行 {@code locationId} 对应库位名；回落产品默认库位名）。
     */
    @Schema(description = "货位展示名（locName）")
    private String locationName;

    /** 拼音码。 */
    @Schema(description = "拼音码")
    private String pyCode;

    /** 五笔码。 */
    @Schema(description = "五笔码")
    private String wbCode;

    /** 目录类别 code。 */
    @Schema(description = "目录类别 code")
    private String categoryCode;

    /** 目录类别展示名。 */
    @Schema(description = "目录类别展示名")
    private String categoryCodeDictText;

    /**
     * 默认划价单位 code（耗材优先拆零 SPLIT；诊疗为包装 PACKAGE）。
     */
    @Schema(description = "默认划价单位 code（耗材优先拆零）")
    private String unitCode;

    /** 默认划价单位名。 */
    @Schema(description = "默认划价单位名")
    private String unitName;

    /** 五类单位槽（含各槽 unitPrice，包装锚线性换算）。 */
    @Schema(description = "五类单位槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 选中定价行定义 id（展示用）。 */
    @Schema(description = "选中定价行定义 id（展示用）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemDefinitionId;

    /**
     * 默认划价单位下单价（展示用；耗材对齐拆零槽价，诊疗对齐包装 SALE）。
     *
     * <p>入账权威见写路径 BR-IP-PRC-020，不以本字段落账。</p>
     */
    @Schema(description = "默认划价单位下单价（展示用）")
    private BigDecimal unitPrice;

    /** 诊疗活动定义 id。 */
    @Schema(description = "诊疗活动定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 耗材设备定义 id。 */
    @Schema(description = "耗材设备定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceDefId;

    /**
     * 耗材发放库房（批号行取库存行 locationId；无库存时回落模板默认库）。
     * 写路径回传 {@code performLocation}。
     */
    @Schema(description = "默认发放库房（耗材行；写 IF-IP-PRC-02/04 回传 performLocation）")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 默认发放库房（耗材行；写 IF-IP-PRC-02/04 回传 performLocation）。 */
    private Long defaultDispenseLocationId;

    /**
     * 耗材库存项主键（{@code wkf_inventory_item.id}）；批号展开行必填；无库存品级行可空。
     */
    @Schema(description = "耗材库存项主键（批号行）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long inventoryItemId;

    /** 耗材批号；批号展开行必填。 */
    @Schema(description = "耗材批号")
    private String lotNumber;

    /**
     * 该批号剩余可用量（拆零单位净量，已扣发放预占）。
     */
    @Schema(description = "批号剩余数量（拆零净量）")
    private BigDecimal remainingQuantity;

    /** 剩余量展示文案（如「4盒6支」）。 */
    @Schema(description = "剩余数量展示文案")
    private String availableQuantityText;

    /** 效期截止时刻；未维护可空。 */
    @Schema(description = "效期截止时刻")
    private LocalDateTime expirationDate;

    @Schema(description = "诊疗套餐子项列表（子表事实行）")
    /** 诊疗套餐子项列表（子表事实行）。 */
    private List<NurseStationPricingPackageChildDto> packageChildren;

    @Schema(description = "是否吸氧/心电计时类")
    /** 是否吸氧/心电计时类。 */
    private Boolean oxygenOrEcgFlag;

    /** 吸氧/心电 Gate 类型：OXYGEN/ECG。 */
    @Schema(description = "吸氧/心电 Gate 类型：OXYGEN/ECG")
    private String durationGateKind;
}
