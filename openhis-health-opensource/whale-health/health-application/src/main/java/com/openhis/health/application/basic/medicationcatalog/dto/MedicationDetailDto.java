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
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 药品目录详情 DTO：在 {@link MedicationDto} 列表字段基础上扩展表单维护所需的明细列。
 *
 * <p>OpenAPI {@link Schema} 描述字段语义；持久化与校验仍以 AS-SDD/领域为准。</p>
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "药品目录详情DTO", title = "MedicationDetailDto", requiredProperties = {"name", "doseUnitCode"})
public class MedicationDetailDto extends MedicationDto {

    /**
     * ID
     */
    @Schema(description = "主键ID", example = "10001", type = "string") // 因ToStringSerializer转字符串，标注type
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 药品编码
     */
    @Schema(description = "药品定义ID", example = "20001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationDefId;

    /**
     * 所属科室
     */
    @Schema(description = "所属机构ID", example = "30001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /**
     * 所在位置
     */
    @Schema(description = "存储位置ID", example = "40001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /**
     * 剂型
     */
    @Schema(description = "剂型编码", example = "JX001")
    private String doseFormCode;

    /**
     * 规格
     */
    @Schema(description = "药品规格", example = "5ml:0.5g")
    private String totalVolume;

    /**
     * 成分
     */
    @Schema(description = "药品成分项", example = "阿莫西林")
    private String ingredientItem;

    /**
     * 用法
     */
    @Schema(description = "用药方法编码", example = "YF001")
    private String methodCode;

    /**
     * 用药频次
     */
    @Schema(description = "用药频次编码", example = "PC001")
    private String rateCode;

    /**
     * 单次剂量
     */
    @Schema(description = "单次用药剂量", example = "0.5")
    private BigDecimal dose;

    /**
     * 剂量单位
     */
    @Schema(description = "剂量单位编码（字典码：unit_code）", required = true, example = "DW001")
    @NotBlank(message = "剂量单位不能为空")
    private String doseUnitCode;

    /** 剂量单位字典码对应的中文展示（冗余于术语解析结果）。 */
    @Schema(description = "剂量单位编码对应的文本值", example = "克")
    private String doseUnitCode_dictText;

    /**
     * 单次最大剂量
     */
    @Schema(description = "单次最大用药剂量", example = "1.0")
    private BigDecimal maxUnit;

    /**
     * 药品定义
     */
    @Schema(description = "药品定义描述", example = "阿莫西林胶囊，用于敏感菌所致的呼吸道感染等")
    private String definition;

    /**
     * 药品编号
     */
    @Schema(description = "药品业务编号", example = "YP20250409001")
    private String busNo;

    /**
     * 药品名称
     */
    @Schema(description = "药品名称", required = true, example = "阿莫西林胶囊")
    @NotBlank(message = "药品名称不能为空")
    private String name;

    /**
     * 适用范围
     */
    @Schema(description = "适用范围枚举 1-门诊 2-住院 3-通用", example = "3", allowableValues = {"1", "2", "3"})
    private String domainEnum;

    /**
     * 药品版本
     */
    @Schema(description = "药品版本号", example = "V1.0")
    private String version;

    /**
     * 英文药名
     */
    @Schema(description = "药品英文名称", example = "Amoxicillin Capsules")
    private String nameEn;

    /**
     * 药品名称拼音码
     */
    @Schema(description = "药品名称拼音简码", example = "AMXLJN")
    private String pyStr;

    /**
     * 药品五笔码
     */
    @Schema(description = "药品名称五笔简码", example = "SGRM")
    private String wbStr;

    /**
     * 药品分类
     */
    @Schema(description = "药品分类编码", example = "FL001")
    private String categoryCode;

    /**
     * 商品名称
     */
    @Schema(description = "药品商品名称", example = "阿莫仙")
    private String merchandiseName;

    /**
     * 商品名称拼音码
     */
    @Schema(description = "商品名称拼音简码", example = "AMX")
    private String merchandisePyStr;

    /**
     * 商品五笔码
     */
    @Schema(description = "商品名称五笔简码", example = "SGR")
    private String merchandiseWbStr;

    /**
     * 采购单位
     */
    @Schema(description = "采购单位编码（字典码：unit_code）", example = "DW001")
    private String purchaseUnitCode;

    /** 采购单位字典码对应的中文展示。 */
    @Schema(description = "采购单位编码对应的文本值", example = "盒")
    private String purchaseUnitCode_dictText;

    /**
     * 拆零单位
     */
    @Schema(description = "拆零单位编码", example = "DW002")
    private String splitUnitCode;

    /**
     * 销售单位
     */
    @Schema(description = "药品销售单位编码", example = "DW003")
    private String packageUnitCode;

    /**
     * 库存单位
     */
    @Schema(description = "药品库存单位编码", example = "DW004")
    private String stockUnitCode;

    /**
     * 所含耗材
     */
    @Schema(description = "药品包含的耗材说明", example = "一次性注射器")
    private String comprisedText;

    /**
     * 成分
     */
    @Schema(description = "药品主要成分", example = "阿莫西林")
    private String ingredient;


    /**
     * 剂量形式
     */
    @Schema(description = "剂量形式（字典码：dose_form_code）", example = "1")
    private Integer doseFrom;

    /** 剂量形式整数码对应的中文展示。 */
    @Schema(description = "剂量形式对应的文本值", example = "胶囊")
    private String doseFrom_dictText;

    /**
     * 批准文号
     */
    @Schema(description = "药品批准文号", example = "国药准字H20060575")
    private String approvalNumber;

    /**
     * 医保是否对码
     */
    @Schema(description = "医保是否已对码", example = "true")
    private Boolean ybMatchFlag;

    /**
     * 医保编码
     */
    @Schema(description = "医保目录编码", example = "YB2025001")
    private String ybNo;

    /**
     * 药理作用分类
     */
    @Schema(description = "药理作用分类编码", example = "YL001")
    private String pharmacologyCategoryCode;

    /**
     * 生产厂家
     */
    @Schema(description = "生产厂家ID", example = "50001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long manufacturerId;

    /**
     * 生产厂商文本
     */
    @Schema(description = "生产厂家名称", example = "广州白云山医药集团股份有限公司")
    private String manufacturerText;

    /**
     * 供应商
     */
    @Schema(description = "供应商ID", example = "60001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyId;

    /**
     * 限制使用范围
     */
    @Schema(description = "限制使用范围说明", example = "仅限呼吸内科使用")
    private String restrictedScope;

    /**
     * 产品特性
     */
    @Schema(description = "产品特性枚举 1-处方药 2-非处方药 3-特殊管理药品", example = "1", allowableValues = {"1", "2", "3"})
    private Integer characteristic;

    /**
     * 购入价
     */
    @Schema(description = "药品购入价格", example = "10.5")
    private BigDecimal purchasePrice;

    /**
     * 零售价
     */
    @Schema(description = "药品零售价格", example = "15.8")
    private BigDecimal retailPrice;

    /**
     * 最高零售价
     */
    @Schema(description = "药品最高零售限价", example = "18.0")
    private BigDecimal maximumRetailPrice;

    /**
     * 医保类别
     */
    @Schema(description = "医保类别编码", example = "YB001")
    private String ybType;

    /**
     * 财务类别
     */
    @Schema(description = "财务类别编码（字典码：fin_type_code）", example = "CW001")
    private String typeCode;

    /** 财务分类字典码对应的中文展示。 */
    @Schema(description = "财务类别编码对应的文本值", example = "西药")
    private String typeCode_dictText;

    /**
     * 单次最小用药频次
     */
    @Schema(description = "单次最小用药频次编码", example = "PC001")
    private String minRateCode;

    /**
     * 单次最大用药频次
     */
    @Schema(description = "单次最大用药频次编码", example = "PC002")
    private String maxRateCode;

    /**
     * 拆分属性
     */
    @Schema(description = "拆分属性枚举 1-可拆分 2-不可拆分", example = "1", allowableValues = {"1", "2"})
    private String partAttributeEnum;

    /**
     * 贯标国家编码
     */
    @Schema(description = "国家药品编码（贯标）", example = "860000123456789")
    private String nationalDrugCode;

    /**
     * DDD值
     */
    @Schema(description = "限定日剂量（DDD）编码", example = "DDD001")
    private String dddCode;

    /**
     * DDD单位
     */
    @Schema(description = "限定日剂量单位编码", example = "DW001")
    private String dddUnitCode;

    /**
     * 用量限定
     */
    @Schema(description = "单次最大用量限定", example = "2.0")
    private BigDecimal usageLimit;

    /**
     * 抗生素分类
     */
    @Schema(description = "抗生素分类编码（字典码：antibiotic_type_code）", example = "KS001")
    private String antibioticCode;

    /** 抗生素分类字典码对应的中文展示。 */
    @Schema(description = "抗生素分类编码对应的文本值", example = "青霉素类")
    private String antibioticCode_dictText;

    /**
     * 权限限制
     */
    @Schema(description = "权限限制枚举 1-普通权限 2-专科权限 3-主任权限", example = "1", allowableValues = {"1", "2", "3"})
    private String restrictedEnum;


    /**
     * 住院临时医嘱拆分属性
     */
    @Schema(description = "住院临时医嘱拆分属性 1-可拆分 2-不可拆分", example = "1", allowableValues = {"1", "2"})
    private String thoPartAttributeEnum;

    /**
     * 最小库存警戒数量(常规单位)
     */
    @Schema(description = "最小库存警戒数量", example = "10")
    private BigDecimal itemMinQuantity;

    /**
     * 最大库存警戒数量(常规单位)
     */
    @Schema(description = "最大库存警戒数量", example = "100")
    private BigDecimal itemMaxQuantity;

    /**
     * 剂量单位 -> 拆零单位换算比
     */
    @Schema(description = "剂量单位到拆零单位换算比例", example = "10")
    private BigDecimal doseUnitRatio;

    /**
     * 采购单位 -> 拆零单位换算比
     */
    @Schema(description = "采购单位到拆零单位换算比例", example = "10")
    private BigDecimal purchaseUnitRatio;

    /**
     * 库存单位 -> 拆零单位换算比
     */
    @Schema(description = "库存单位到拆零单位换算比例", example = "10")
    private BigDecimal stockUnitRatio;

    /**
     * 销售单位 -> 拆零单位换算比
     */
    @Schema(description = "销售单位到拆零单位换算比例", example = "10")
    private BigDecimal packageUnitRatio;

    /**
     * 医保等级
     */
    @Schema(description = "医保等级 1-甲类 2-乙类 3-丙类", example = "2", allowableValues = {"1", "2", "3"})
    private Integer chrgitmLv;

    /**
     * 用药说明
     */
    @Schema(description = "用药说明/注意事项", example = "饭后服用，每次1粒，每日3次")
    private String dosageInstruction;

    /** 基药标识 */
    @Schema(description = "是否为基本药物 1-是 0-否", example = "1", allowableValues = {"0", "1"})
    private Boolean basicFlag;


    /** 是否皮试 */
    @Schema(description = "是否需要皮试 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean skinTestFlag;

    /** 是否为注射药物 */
    @Schema(description = "是否为注射类药物 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean injectFlag;

    /** 儿童用药标志 */
    @Schema(description = "儿童用药标志 1-适用 0-不适用", example = "1", allowableValues = {"0", "1"})
    private Boolean childrenFlag;

    /** 是否自制 */
    @Schema(description = "是否为自制药品 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean selfFlag;

    /** 是否为活性（与父类目录启用 activeFlag 无关） */
    @Schema(description = "是否为活性状态", example = "true")
    private Boolean potencyFlag;

    /** 是否限制使用 */
    @Schema(description = "是否限制使用 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean restrictedFlag;

    /** 是否抗生素 */
    @Schema(description = "是否为抗生素 1-是 0-否", example = "1", allowableValues = {"0", "1"})
    private Boolean antibioticFlag;
}
