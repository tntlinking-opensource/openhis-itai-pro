/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 药品目录列表/摘要 DTO：读模型或分页行扁平输出，供表格与卡片展示。
 *
 * <p>详情扩展字段见 {@link MedicationDetailDto}；定价相关金额单位为「元」，与收费侧持久化分值由应用层 Codec 转换。</p>
 */
@Data
@Accessors(chain = true)
@Schema(title = "药品目录详情DTO", description = "药品目录详情传输对象")
public class MedicationDto {

    @Schema(description = "ID（以字符串形式序列化）", example = "1", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    /** ID（以字符串形式序列化）。 */
    private Long id;

    @Schema(description = "药品编码（以字符串形式序列化）", example = "1001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationDefId;

    /** 药品定义流转状态（PublicationStatus 编码），与目录启用标识 activeFlag 区分。 */
    @Schema(description = "药品定义流转状态（PublicationStatus 编码），与目录启用标识 activeFlag 区分", example = "active")
    private String statusEnum;

    /** 药品定义流转状态文本。 */
    @Schema(description = "药品定义流转状态文本", example = "在用")
    private String statusEnum_enumText;

    /** 目录是否启用。 */
    @Schema(description = "目录是否启用", example = "true")
    private Boolean activeFlag;

    /** 目录启用文本。 */
    @Schema(description = "目录启用文本", example = "启用")
    private String activeFlagText;

    /** 所在位置 ID（以字符串形式序列化）。 */
    @Schema(description = "所在位置 ID（以字符串形式序列化）", example = "10", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 所在位置文本。 */
    @Schema(description = "所在位置文本", example = "门诊药房")
    private String locationId_dictText;

    /** 药品编号。 */
    @Schema(description = "药品编号", example = "MED-2026001")
    private String busNo;

    /** 药品名称。 */
    @Schema(description = "药品名称", example = "阿莫西林胶囊")
    private String name;

    /** 药品分类代码（字典值）。 */
    @Schema(description = "药品分类代码（字典值）", example = "ANTIBIOTIC")
    private String categoryCode;

    /** 药品分类文本。 */
    @Schema(description = "药品分类文本", example = "抗生素")
    private String categoryCode_dictText;

    /** 生产厂家 ID（以字符串形式序列化）。 */
    @Schema(description = "生产厂家 ID（以字符串形式序列化）", example = "200", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long manufacturerId;

    /** 供应商标识（历史字段；列表页不再回填展示名）。 */
    @Schema(description = "供应商标识（目录维护已废弃）", example = "300", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyId;

    /** 供应商文本（历史字段；列表页不再回填）。 */
    @Schema(description = "供应商文本（已废弃）", example = "某某医药有限公司")
    private String supplyId_dictText;

    /** 抗生素分类代码（字典值）。 */
    @Schema(description = "抗生素分类代码（字典值）", example = "BETA_LACTAM")
    private String antibioticCode;

    /** 抗生素分类文本。 */
    @Schema(description = "抗生素分类文本", example = "β-内酰胺类")
    private String antibioticCode_dictText;

    /** 生产厂商文本。 */
    @Schema(description = "生产厂商文本", example = "某某制药厂")
    private String manufacturerText;

    /**
     * 销售参考价（维护态 {@code SALE}，元/包装单位）。
     *
     * <p>列表展示：中药饮片（分类码 {@code 4}）按 3 位小数 format，其余 2 位；落库仍为 6 位。</p>
     */
    @Schema(description = "销售参考价（元/包装单位；中药 UI 可 3 位）", example = "12.50", type = "number", format = "double")
    private BigDecimal retailPrice;

    /**
     * 采购参考价（元/包装单位）。
     *
     * <p>属性名为历史遗留 PascalCase（{@code PurchasePrice}），与 JSON/JavaBean 约定一致，不宜在本轮单独改名。
     * 维护态价类为 {@code PURCHASE}；真正进价见批号 {@code LOT_NUMBER_COST}。</p>
     */
    private BigDecimal PurchasePrice;

    @Schema(description = "是否处方药", example = "true")
    /** 医保是否已对码。 */
    private Boolean rxFlag;

    @Schema(description = "医保是否已对码", example = "true")
    private Boolean ybMatchFlag;

    /** 单次最小用药剂量。 */
    private BigDecimal minUnit;

    /** 单次最大用药剂量。 */
    private BigDecimal maxUnit;

    /** 医保是否已对码。 */
    private String specification;

    /** 医保目录编码或对照码。 */
    private String ybNo;

    /** 采购单位编码。 */
    @Schema(description = "采购单位编码", example = "DW001")
    private String purchaseUnitCode;

    /** 拆零单位编码。 */
    @Schema(description = "拆零单位编码", example = "DW002")
    private String splitUnitCode;

    /** 销售单位编码。 */
    @Schema(description = "销售单位编码", example = "DW003")
    private String packageUnitCode;

    /** 包装单位文本。 */
    @Schema(description = "包装单位文本", example = "盒")
    private String packageUnitCode_dictText;

    /** 库存单位编码。 */
    @Schema(description = "库存单位编码", example = "DW004")
    private String stockUnitCode;

    /** 剂量单位编码。 */
    @Schema(description = "剂量单位编码", example = "DW005")
    private String doseUnitCode;

    /** 剂量单位换算比。 */
    @Schema(description = "剂量单位换算比", example = "10")
    private BigDecimal doseUnitRatio;

    /** 采购单位换算比。 */
    @Schema(description = "采购单位换算比", example = "10")
    private BigDecimal purchaseUnitRatio;

    /** 库存单位换算比。 */
    @Schema(description = "库存单位换算比", example = "10")
    private BigDecimal stockUnitRatio;

    /** 销售单位换算比。 */
    @Schema(description = "销售单位换算比", example = "10")
    private BigDecimal packageUnitRatio;

    /**
     * 追溯码所贴单位编码（术语 {@code unit_code}）。
     */
    @Schema(description = "追溯码单位编码", example = "DW003")
    private String traceUnitCode;

    /** 追溯码单位字典展示文案。 */
    @Schema(description = "追溯码单位展示名", example = "盒")
    private String traceUnitCode_dictText;

    /**
     * 1 个追溯码单位对应的拆零数量。
     */
    @Schema(description = "追溯码单位→拆零倍数", example = "10")
    private BigDecimal traceUnitRatio;

    /** 版本号。 */
    private String version;

    /**
     * 五类单位槽（采购/拆零/包装/库存/剂量），口径对齐门诊医生站 {@code pageDoctorStationAdviceCatalog}。
     */
    @Schema(description = "多计量单位槽（unitBands）")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;
}
