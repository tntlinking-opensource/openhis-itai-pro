/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.product.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 产品定义读侧展示 DTO（由主表批量加载，替代 {@code ProductDefinitionReadModel} 用于应用层装配）。
 */
@Getter
@Setter
public class ProductDefinitionDisplayDto {

    /** 产品定义来源表名（药品/诊疗/材料锚点）。 */
    private String productTableName;

    /** 产品定义主键。 */
    private Long productDefinitionId;

    /** 产品业务编码。 */
    private String productBusNo;

    /** 产品通用名/项目名称。 */
    private String productName;

    /** 商品名（药品定义 {@code merchandise_name}）。 */
    private String merchandiseName;

    /** 产品类别术语码。 */
    private String categoryCode;

    /** 产品类别展示名。 */
    private String categoryCodeDictText;

    /**
     * 耗材种类术语码（{@code adm_device_definition.type_code} / {@code device_type_code}）。
     * 药品通常为空；取药科室模板种类维匹配依赖本字段。
     */
    private String typeCode;

    /** 耗材种类展示名。 */
    private String typeCodeDictText;

    /** 采购单位码。 */
    private String purchaseUnitCode;

    /** 采购单位展示名。 */
    private String purchaseUnitCodeDictText;

    /** 采购单位与最小单位换算比。 */
    private BigDecimal purchaseUnitRatio;

    /** 拆零单位码。 */
    private String splitUnitCode;

    /** 拆零单位展示名。 */
    private String splitUnitCodeDictText;

    /** 包装单位码。 */
    private String packageUnitCode;

    /** 包装单位展示名。 */
    private String packageUnitCodeDictText;

    /** 包装单位与最小单位换算比。 */
    private BigDecimal packageUnitRatio;

    /**
     * 追溯码贴码单位（可游离于五类业务单位之外）。
     *
     * <p>药品必填；耗材可空。同意入库写入主档；已建码以 {@code cus_trace_number} 为准。</p>
     */
    private String traceUnitCode;

    /** 追溯码贴码单位展示名。 */
    private String traceUnitCodeDictText;

    /**
     * 1 个追溯码单位对应的拆零数量；有追溯单位时须 &gt; 0。
     */
    private BigDecimal traceUnitRatio;

    /** 库存单位码。 */
    private String stockUnitCode;

    /** 库存单位展示名。 */
    private String stockUnitCodeDictText;

    /** 库存单位与最小单位换算比。 */
    private BigDecimal stockUnitRatio;

    /**
     * 最小库存警戒数量（库存单位）；药品/耗材目录维护；{@code null} 表示未维护。
     * 库存预警优先于系统设置全局包装阈值（见 AS-INV-WHS-004 BR-INV-DTL-024）。
     */
    private BigDecimal itemMinQuantity;

    /**
     * 最大库存警戒数量（库存单位）；药品/耗材目录维护；{@code null} 表示未维护。
     * 库存预警优先于系统设置全局包装阈值（见 AS-INV-WHS-004 BR-INV-DTL-024）。
     */
    private BigDecimal itemMaxQuantity;

    /** 剂量单位码。 */
    private String doseUnitCode;

    /** 剂量单位展示名。 */
    private String doseUnitCodeDictText;

    /** 剂量单位与最小单位换算比。 */
    private BigDecimal doseUnitRatio;

    /**
     * 默认单次剂量（剂量单位）；药品目录维护；{@code null} 表示未维护。
     */
    private BigDecimal dose;

    /**
     * 单次最小剂量（剂量单位）；药品目录维护；{@code null} 表示未维护。
     */
    private BigDecimal minUnit;

    /**
     * 单次最大剂量（剂量单位）；药品目录维护；{@code null} 表示未维护。
     */
    private BigDecimal maxUnit;

    /** 默认门诊/就诊计价单位码。 */
    private String defEncounterUnitCode;

    /** 默认门诊/就诊计价单位展示名。 */
    private String defEncounterUnitCodeDictText;

    /** 默认住院计价单位码。 */
    private String defInhospitalUnitCode;

    /** 规格/总量展示文本（如 {@code 10mg*12片}）。 */
    private String totalVolume;

    /** 目录单价（不含调价快照）。 */
    private BigDecimal price;

    /**
     * 生产厂家展示名（非目录持久化列；由应用层按 {@link #manufacturerId} 解析供应商名称回填）。
     */
    private String manufacturer;

    /**
     * 生产厂家主键（cus_supplier.id，type=生产厂家）。
     */
    private Long manufacturerId;

    /** 默认库房/货位主键。 */
    private Long locationId;

    /** 默认供应商主键。 */
    private Long supplierId;

    /** 产品状态枚举码。 */
    private String statusEnum;

    /** 是否启用（业务有效标志）。 */
    private Boolean activeFlag;

    /** 是否处方药。 */
    private Boolean rxFlag;

    /** 医保目录编码。 */
    private String ybNo;

    /** 批准文号/国药准字（药品定义 {@code approval_number}）。 */
    private String approvalNumber;

    /** 拼音检索码。 */
    private String pyStr;

    /** 五笔检索码。 */
    private String wbStr;

    /** 医保收费项目等级整数码（术语 {@code CHRGITM_LV}）。 */
    private Integer chrgitmLv;

    /** 医保等级展示名（术语 {@code CHRGITM_LV}，由应用层按 {@link #chrgitmLv} 回填）。 */
    private String chrgitmLvText;

    /** 关联收费项目定义主键。 */
    private Long chargeItemDefinitionId;

    /** 用法频次码（诊疗/药品目录）。 */
    private String rateCode;

    /** 用法频次展示名。 */
    private String rateCodeDictText;

    /** 给药途径码。 */
    private String methodCode;

    /** 给药途径展示名。 */
    private String methodCodeDictText;

    /** 剂型码。 */
    private String doseFormCode;

    /** 剂型展示名。 */
    private String doseFormCodeDictText;

    /**
     * 中药特殊煎法术语码（药品目录 {@code dosage_instruction}，术语
     * {@code dosage_instruction}）。
     */
    private String dosageInstruction;

    /** 中药特殊煎法展示名。 */
    private String dosageInstructionDictText;

    /** 默认住院计价单位展示名。 */
    private String defInhospitalUnitCodeDictText;

    /** 是否限制性用药/项目。 */
    private Boolean restrictedFlag;

    /** 限制使用范围说明。 */
    private String restrictedScope;

    /** 限制类型枚举码。 */
    private String restrictedEnum;

    /** 限制类型展示名。 */
    private String restrictedEnumDictText;

    /**
     * 药品性质码（药品目录 {@code pharmacology_category_code}，术语 {@code medicine_properties}）。
     *
     * <p>门诊分方特殊管理药品轨道以此为准；勿与 {@link #partAttributeEnum} 混淆。</p>
     */
    private String pharmacologyCategoryCode;

    /** 药品性质展示名。 */
    private String pharmacologyCategoryCodeDictText;

    /** 拆分属性枚举码（门诊拆零规则 {@code OrderCalcRuleEnum}，非药品性质）。 */
    private String partAttributeEnum;

    /** 拆分属性展示名。 */
    private String partAttributeEnumDictText;

    /** 住院拆分属性枚举码。 */
    private String thoPartAttributeEnum;

    /** 住院拆分属性展示名。 */
    private String thoPartAttributeEnumDictText;

    /** 是否皮试药品。 */
    private Boolean skinTestFlag;

    /** 是否注射/输液类药品。 */
    private Boolean injectFlag;

    /** 诊疗类型（活动定义 {@code type_enum} 或读模型投影整型）。 */
    private String activityTypeCode;

    /** 默认库房/货位展示名。 */
    private String locationName;
}
