/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-IP-DOR-11：住院医生站「医嘱开立目录」分页行契约。
 *
 * <p>主数据来自 {@link ProductDefinitionDisplayDto}；
 * 单价来自 {@code adm_charge_item_definition} 批量查询（读模型不落库金额）；五类单位以 {@link #unitBands} 结构化下发。
 * 产品类型由 {@link #productTableName} 表达（如药品/耗材/诊疗定义表名）；批号见 {@link #inventoryList} 明细行。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱开立目录行")
public class InpatientDoctorStationAdviceCatalogRowDto {

    /** 产品定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "产品定义主键")
    private Long productDefinitionId;

    /** 产品来源表名。 */
    @Schema(description = "产品来源表名")
    private String productTableName;

    /** 业务编码。 */
    @Schema(description = "业务编码")
    private String busNo;

    /** 展示名称（通用名/项目名称）。 */
    @Schema(description = "展示名称（通用名/项目名称）")
    private String name;

    /**
     * 商品名称（药品定义 {@code merchandise_name}；诊疗/耗材通常为空）。
     * <p>开立选品关键字已可按商品名模糊检索；本字段供列表列展示。</p>
     */
    @Schema(description = "商品名称")
    private String merchandiseName;

    /** 拼音检索码。 */
    @Schema(description = "拼音检索码")
    private String pyCode;

    /** 五笔检索码。 */
    @Schema(description = "五笔检索码")
    private String wbCode;

    /** 目录类别编码。 */
    @Schema(description = "目录类别编码")
    private String categoryCode;

    /** 目录类别展示名。 */
    @Schema(description = "目录类别展示名")
    private String categoryCodeDictText;

    /**
     * 计价主单位编码：包装单位（{@code package_unit_code}），禁止跨槽回落。
     */
    @Schema(description = "计价主单位编码（包装单位）")
    private String unitCode;

    @Schema(description = "计价主单位展示名")
    /** 计价主单位展示名。 */
    private String unitName;

    /**
     * 五类单位槽（采购/拆零/销售/库存/剂量）及相对拆零倍数；来源读模型单位列与换算比列。
     */
    @Schema(description = "五类单位槽列表")
    private List<ProductDefinitionCatalogUnitBandDto> unitBands;

    /** 默认频次编码。 */
    @Schema(description = "默认频次编码")
    private String rateCode;

    /** 默认频次展示名。 */
    @Schema(description = "默认频次展示名")
    private String rateCodeDictText;

    /** 默认用法编码。 */
    @Schema(description = "默认用法编码")
    private String methodCode;

    /** 默认用法展示名。 */
    @Schema(description = "默认用法展示名")
    private String methodCodeDictText;

    /** 中药特殊煎法术语码（药品目录 dosage_instruction）。 */
    @Schema(description = "中药特殊煎法术语码")
    private String dosageInstruction;

    /** 中药特殊煎法展示名。 */
    @Schema(description = "中药特殊煎法展示名")
    private String dosageInstructionDictText;

    /** 剂型编码。 */
    @Schema(description = "剂型编码")
    private String doseFormCode;

    /** 剂型展示名。 */
    @Schema(description = "剂型展示名")
    private String doseFormCodeDictText;

    /** 默认单次剂量（剂量单位）。 */
    @Schema(description = "默认单次剂量（剂量单位）")
    private BigDecimal dose;

    /** 单次最小剂量（剂量单位）。 */
    @Schema(description = "单次最小剂量（剂量单位）")
    private BigDecimal minUnit;

    /** 单次最大剂量（剂量单位）。 */
    @Schema(description = "单次最大剂量（剂量单位）")
    private BigDecimal maxUnit;

    /** 是否限制使用。 */
    @Schema(description = "是否限制使用")
    private Boolean restrictedFlag;

    /** 限制使用范围说明。 */
    @Schema(description = "限制使用范围说明")
    private String restrictedScope;

    /** 限制用药等级编码。 */
    @Schema(description = "限制用药等级编码")
    private String restrictedEnum;

    /** 限制用药等级展示名。 */
    @Schema(description = "限制用药等级展示名")
    private String restrictedEnumDictText;

    /** 拆零属性码。 */
    @Schema(description = "拆零属性码")
    private String partAttributeEnum;

    /** 拆零属性展示名。 */
    @Schema(description = "拆零属性展示名")
    private String partAttributeEnumDictText;

    /** 住院临时拆分属性码。 */
    @Schema(description = "住院临时拆分属性码")
    private String thoPartAttributeEnum;

    /** 住院临时拆分属性展示名。 */
    @Schema(description = "住院临时拆分属性展示名")
    private String thoPartAttributeEnumDictText;

    /** 包装规格。 */
    @Schema(description = "包装规格")
    private String totalVolume;

    /** 生产厂家/产地展示名（读模型 {@code manufacturer}）。 */
    @Schema(description = "生产厂家/产地")
    private String manufacturer;

    /** 诊疗活动类型 code。 */
    @Schema(description = "诊疗活动类型 code")
    private String activityTypeCode;

    /** 皮试标志。 */
    @Schema(description = "皮试标志")
    private Boolean skinTestFlag;

    /** 注射标志。 */
    @Schema(description = "注射标志")
    private Boolean injectFlag;

    /** 默认执行科室主键（读模型 {@code location_id}）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 默认执行科室主键。 */
    @Schema(description = "默认执行科室主键")
    private Long defaultPerformOrgId;

    /** 默认执行科室名称。 */
    @Schema(description = "默认执行科室名称")
    private String defaultPerformOrgName;

    /** 默认发药/发材库房（IF-IP-DOR-11 读侧展示；住院写路径不回传）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 默认发药/发材库房 id。 */
    @Schema(description = "默认发药/发材库房 id")
    private Long defaultDispenseLocationId;

    /** 默认库房名称。 */
    @Schema(description = "默认库房名称")
    private String defaultDispenseLocationName;

    /**
     * 默认发点下可开量（包装单位换算值，供库存对比；可含小数，界面勿直接展示）。
     * <p>该目录各批净可用合计；界面库存列优先用 {@link #availableQuantityText}。</p>
     */
    @Schema(description = "可开量（包装单位；该目录各批合计）")
    private BigDecimal availableQuantity;

    /**
     * 可开量展示文案：拆零净量 → 满包整数 + 不足一包拆零余量（如 {@code 4盒6片}），与库存明细
     * {@code packageStockText} / {@link com.openhis.health.application.shared.product.MedicationPackageSplitQuantityDisplayAssembler} 同族。
     */
    @Schema(description = "可开量展示文案（满包+拆零，无小数盒）")
    private String availableQuantityText;

    /** 预减后的库存行列表（仅药/材）。 */
    @Schema(description = "预减后的库存行列表（仅药/材）")
    private List<InpatientDoctorStationAdviceInventoryRowDto> inventoryList;

    /**
     * 目录展示单价（元）：诊疗等为择优定价行；药品/耗材在存在 {@link #inventoryList} 时与<strong>首条库存行批号</strong>对应的
     * {@code LOT_NUMBER_PRICE} 一致（批号不同价不同）；无库存行时仍为实例级择优口径。
     */
    @Schema(description = "目录展示单价（元）；药耗有库存时与首条库存批号的批号售价一致")
    private BigDecimal unitPrice;

    /** 收费项定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费项定义主键")
    private Long chargeItemDefinitionId;

    /** 医保收费项目等级码（术语 {@code CHRGITM_LV}，如 1/2/3）。 */
    @Schema(description = "医保收费项目等级码（CHRGITM_LV）")
    private Integer chrgitmLv;

    /** 医保等级展示名（甲类/乙类/丙类等）。 */
    @Schema(description = "医保等级展示名（甲类/乙类/丙类等）")
    private String chrgitmLvText;
}
