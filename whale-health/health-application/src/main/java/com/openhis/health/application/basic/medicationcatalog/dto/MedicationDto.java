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

/* (C)2026 */
package com.openhis.health.application.basic.medicationcatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 药品目录列表/摘要 DTO：读模型或分页行扁平输出，供表格与卡片展示。
 *
 * <p>详情扩展字段见 {@link MedicationDetailDto}；定价相关金额单位为「元」，与收费侧持久化分值由应用层 Codec 转换。</p>
 */
@Data
@Accessors(chain = true)
@Schema(title = "药品目录详情DTO", description = "药品目录详情传输对象")
public class MedicationDto {

    /** 读模型或聚合主键（序列化为字符串避免前端 Long 精度丢失）。 */
    @Schema(description = "ID（以字符串形式序列化）", example = "1", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 药品业务定义主键，可与 {@link #id} 同源或按投影口径冗余。 */
    @Schema(description = "药品编码（以字符串形式序列化）", example = "1001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationDefId;

    /** 药品定义生命周期状态编码（如 PublicationStatus），区别于目录启用 {@link #activeFlag}。 */
    @Schema(description = "药品定义流转状态（PublicationStatus 编码），与目录启用标识 activeFlag 区分", example = "active")
    private String statusEnum;

    /** 生命周期状态中文展示文案。 */
    @Schema(description = "药品定义流转状态文本", example = "在用")
    private String statusEnum_enumText;

    /** 读模型侧目录是否对临床/库房可见启用。 */
    @Schema(description = "目录是否启用", example = "true")
    private Boolean activeFlag;

    /** 目录启用状态中文标签。 */
    @Schema(description = "目录启用文本", example = "启用")
    private String activeFlagText;

    /** 默认发放或绑定库房/药房点位主键。 */
    @Schema(description = "所在位置 ID（以字符串形式序列化）", example = "10", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 存放点位中文名称。 */
    @Schema(description = "所在位置文本", example = "门诊药房")
    private String locationId_dictText;

    /** 院内药品业务编号（非医保码）。 */
    @Schema(description = "药品编号", example = "MED-2026001")
    private String busNo;

    /** 通用名或制剂展示名称。 */
    @Schema(description = "药品名称", example = "阿莫西林胶囊")
    private String name;

    /** 药理/管理分类字典编码。 */
    @Schema(description = "药品分类代码（字典值）", example = "ANTIBIOTIC")
    private String categoryCode;

    /** 分类字典中文标签。 */
    @Schema(description = "药品分类文本", example = "抗生素")
    private String categoryCode_dictText;

    /** 生产厂家主数据主键。 */
    @Schema(description = "生产厂家 ID（以字符串形式序列化）", example = "200", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long manufacturerId;

    /** 供应厂商主数据主键。 */
    @Schema(description = "供应商 ID（以字符串形式序列化）", example = "300", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyId;

    /** 供应商名称冗余展示。 */
    @Schema(description = "供应商文本", example = "某某医药有限公司")
    private String supplyId_dictText;

    /** 抗菌药物管理分类编码。 */
    @Schema(description = "抗生素分类代码（字典值）", example = "BETA_LACTAM")
    private String antibioticCode;

    /** 抗菌分类中文标签。 */
    @Schema(description = "抗生素分类文本", example = "β-内酰胺类")
    private String antibioticCode_dictText;

    /** 生产厂家名称冗余展示。 */
    @Schema(description = "生产厂商文本", example = "某某制药厂")
    private String manufacturerText;

    /** 零售价（元），可由收费定价行回填。 */
    @Schema(description = "售价", example = "12.50", type = "number", format = "double")
    private BigDecimal retailPrice;

    /**
     * 采购价（元）。
     *
     * <p>属性名为历史遗留 PascalCase（{@code PurchasePrice}），与 JSON/JavaBean 约定一致，不宜在本轮单独改名。</p>
     */
    private BigDecimal PurchasePrice;

    /** 是否处方药标识。 */
    @Schema(description = "是否处方药", example = "true")
    private Boolean rxFlag;

    /** 国家/院内医保目录是否已对照成功。 */
    @Schema(description = "医保是否已对码", example = "true")
    private Boolean ybMatchFlag;

    /** 单次或包装尺度上的剂量上限说明值（业务口径依前端）。 */
    private BigDecimal maxUnit;

    /** 制剂规格文字描述（如含量、装量）。 */
    private String specification;

    /** 医保目录编码或对照码。 */
    private String ybNo;

    /** 采购单位字典编码。 */
    @Schema(description = "采购单位编码", example = "DW001")
    private String purchaseUnitCode;

    /** 拆零单位编码。 */
    @Schema(description = "拆零单位编码", example = "DW002")
    private String splitUnitCode;

    /** 开单销售单位编码。 */
    @Schema(description = "销售单位编码", example = "DW003")
    private String packageUnitCode;

    /** 库房记账单位编码。 */
    @Schema(description = "库存单位编码", example = "DW004")
    private String stockUnitCode;

    /** 单次给药剂量所用单位编码。 */
    @Schema(description = "剂量单位编码", example = "DW005")
    private String doseUnitCode;

    /** 剂量单位相对拆零单位的换算比。 */
    @Schema(description = "剂量单位换算比", example = "10")
    private BigDecimal doseUnitRatio;

    /** 采购单位相对拆零单位的换算比。 */
    @Schema(description = "采购单位换算比", example = "10")
    private BigDecimal purchaseUnitRatio;

    /** 库存单位相对拆零单位的换算比。 */
    @Schema(description = "库存单位换算比", example = "10")
    private BigDecimal stockUnitRatio;

    /** 销售单位相对拆零单位的换算比。 */
    @Schema(description = "销售单位换算比", example = "10")
    private BigDecimal packageUnitRatio;

    /** 目录或制剂版本号（变更追溯）。 */
    private String version;
}
