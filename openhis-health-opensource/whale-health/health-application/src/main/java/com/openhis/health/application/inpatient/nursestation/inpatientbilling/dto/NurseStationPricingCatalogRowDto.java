/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 护士划价选品目录行（IF-IP-PRC-01，BR-IP-PRC-017）。
 *
 * <p>前端选品后组装 {@link NurseStationPricingWriteItemDto} 提交 IF-02/04；套餐子项由服务端写路径展开。</p>
 */
@Data
@Accessors(chain = true)
/** 护士划价选品目录行。 */
@Schema(description = "护士划价选品目录行")
public class NurseStationPricingCatalogRowDto {

    @Schema(description = "产品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long productDefinitionId;

    /** 产品物理表：wkf_activity_definition / adm_device_definition。 */
    @Schema(description = "产品物理表名")
    private String productTableName;

    /** 产品业务编码（展示/检索）。 */
    @Schema(description = "业务编码")
    private String busNo;

    /** 产品展示名称。 */
    @Schema(description = "展示名称")
    private String name;

    /** 拼音检索码。 */
    @Schema(description = "拼音码")
    private String pyCode;

    /** 五笔检索码。 */
    @Schema(description = "五笔码")
    private String wbCode;

    /** 目录类别字典 code。 */
    @Schema(description = "目录类别 code")
    private String categoryCode;

    /** 目录类别字典展示名。 */
    @Schema(description = "目录类别展示名")
    private String categoryCodeDictText;

    /** 主计价单位 code（就诊单位优先）。 */
    @Schema(description = "主计价单位 code")
    private String unitCode;

    /** 主计价单位展示名。 */
    @Schema(description = "主计价单位名")
    private String unitName;

    /** 五类单位槽（与医生站选品 bands 同构）。 */
    @Schema(description = "五类单位槽")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 目录展示选中的 ChargeItemDefinition id（仅展示；入账以写路径重算为准）。 */
    @Schema(description = "选中定价行定义 id（展示用）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemDefinitionId;

    /** 目录展示单价（仅展示；入账以写路径重算为准）。 */
    @Schema(description = "单价（展示用）")
    private BigDecimal unitPrice;

    /** 诊疗活动定义 id（诊疗行）。 */
    @Schema(description = "诊疗活动定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityId;

    /** 耗材设备定义 id（耗材行）。 */
    @Schema(description = "耗材设备定义 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deviceDefId;

    /**
     * 耗材默认发放库房（IF-IP-PRC-01 读侧模板 + FIFO 解析；写路径回传 {@code performLocation}）。
     */
    @Schema(description = "默认发放库房（耗材行；写 IF-IP-PRC-02/04 回传 performLocation）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultDispenseLocationId;

    @Schema(description = "诊疗套餐子项列表（子表事实行）")
    private List<NurseStationPricingPackageChildDto> packageChildren;

    @Schema(description = "是否吸氧/心电计时类")
    private Boolean oxygenOrEcgFlag;

    /** 计时 Gate 类型：{@code OXYGEN} / {@code ECG}（BR-IP-PRC-010）。 */
    @Schema(description = "吸氧/心电 Gate 类型：OXYGEN/ECG")
    private String durationGateKind;
}
