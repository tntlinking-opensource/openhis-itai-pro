/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 新建药品目录请求体：字段形状与 {@link com.openhis.health.application.basic.medicationcatalog.dto.MedicationDetailDto} 对齐，
 * 由 {@link com.openhis.health.application.basic.medicationcatalog.service.MedicationCatalogAppService#createMedicationCatalog} 接入网关。
 *
 * <p>必填项以 Bean Validation 与 OpenAPI {@link Schema#requiredProperties()} 为准；租户与操作者由底座装配。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "药品目录详情DTO", title = "MedicationDetailDto", requiredProperties = {"name", "doseUnitCode"}) // 显式指定必填字段
public class CreateMedicationCatalogRequest {

    @Schema(description = "药品定义ID", example = "20001", type = "Long")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 药品定义ID。 */
    private Long medicationDefId;

    @Schema(description = "所属机构ID", example = "30001", type = "Long")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 存储位置ID。 */
    @Schema(description = "存储位置ID", example = "40001", type = "Long")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 剂型编码。 */
    @Schema(description = "剂型编码", example = "JX001")
    private String doseFormCode;

    /** 药品规格。 */
    @Schema(description = "药品规格", example = "5ml:0.5g")
    private String totalVolume;

    /** 药品成分项。 */
    @Schema(description = "药品成分项", example = "阿莫西林")
    private String ingredientItem;

    /** 用药方法编码。 */
    @Schema(description = "用药方法编码", example = "YF001")
    private String methodCode;

    /** 用药频次编码。 */
    @Schema(description = "用药频次编码", example = "PC001")
    private String rateCode;

    /** 单次用药剂量。 */
    @Schema(description = "单次用药剂量", example = "0.5")
    private BigDecimal dose;

    /** 剂量单位编码（字典码：unit_code）。 */
    @Schema(description = "剂量单位编码（字典码：unit_code）", required = true, example = "DW001")
    @NotBlank(message = "剂量单位不能为空")
    private String doseUnitCode;

    /** 剂量单位编码对应的文本值。 */
    @Schema(description = "剂量单位编码对应的文本值", example = "克")
    private String doseUnitCode_dictText;

    /** 单次最小用药剂量。 */
    @Schema(description = "单次最小用药剂量", example = "0.5")
    private BigDecimal minUnit;

    /** 单次最大用药剂量。 */
    @Schema(description = "单次最大用药剂量", example = "1.0")
    private BigDecimal maxUnit;

    /** 药品定义描述。 */
    @Schema(description = "药品定义描述", example = "阿莫西林胶囊，用于敏感菌所致的呼吸道感染等")
    private String definition;

    /** 药品业务编号。 */
    @Schema(description = "药品业务编号", example = "YP20250409001")
    private String busNo;

    /** 药品名称 */
    @Schema(description = "药品名称", required = true, example = "阿莫西林胶囊")
    @NotBlank(message = "药品名称不能为空")
    private String name;

    /** 适用范围枚举 1-门诊 2-住院 3-通用。 */
    @Schema(description = "适用范围枚举 1-门诊 2-住院 3-通用", example = "3", allowableValues = {"1", "2", "3"})
    private String domainEnum;

    /** 药品版本号。 */
    @Schema(description = "药品版本号", example = "V1.0")
    private String version;

    /** 药品英文名称。 */
    @Schema(description = "药品英文名称", example = "Amoxicillin Capsules")
    private String nameEn;

    /** 药品名称拼音简码。 */
    @Schema(description = "药品名称拼音简码", example = "AMXLJN")
    private String pyStr;

    /** 药品名称五笔简码。 */
    @Schema(description = "药品名称五笔简码", example = "SGRM")
    private String wbStr;

    /** 药品分类编码。 */
    @Schema(description = "药品分类编码", example = "FL001")
    private String categoryCode;

    /** 药品商品名称。 */
    @Schema(description = "药品商品名称", example = "阿莫仙")
    private String merchandiseName;

    /** 商品名称拼音简码。 */
    @Schema(description = "商品名称拼音简码", example = "AMX")
    private String merchandisePyStr;

    /** 商品名称五笔简码。 */
    @Schema(description = "商品名称五笔简码", example = "SGR")
    private String merchandiseWbStr;

    /** 采购单位编码（字典码：unit_code）。 */
    @Schema(description = "采购单位编码（字典码：unit_code）", example = "DW001")
    private String purchaseUnitCode;

    /** 采购单位编码对应的文本值。 */
    @Schema(description = "采购单位编码对应的文本值", example = "盒")
    private String purchaseUnitCode_dictText;

    /** 拆零单位编码。 */
    @Schema(description = "拆零单位编码", example = "DW002")
    private String splitUnitCode;

    /** 药品销售单位编码。 */
    @Schema(description = "药品销售单位编码", example = "DW003")
    private String packageUnitCode;

    /** 药品库存单位编码。 */
    @Schema(description = "药品库存单位编码", example = "DW004")
    private String stockUnitCode;

    /** 药品包含的耗材说明。 */
    @Schema(description = "药品包含的耗材说明", example = "一次性注射器")
    private String comprisedText;

    /** 药品主要成分。 */
    @Schema(description = "药品主要成分", example = "阿莫西林")
    private String ingredient;


    /** 剂量形式（字典码：dose_form_code）。 */
    @Schema(description = "剂量形式（字典码：dose_form_code）", example = "1")
    private Integer doseFrom;

    /** 剂量形式对应的文本值。 */
    @Schema(description = "剂量形式对应的文本值", example = "胶囊")
    private String doseFrom_dictText;

    /** 药品批准文号。 */
    @Schema(description = "药品批准文号", example = "国药准字H20060575")
    private String approvalNumber;

    /** 医保是否对码 1-是 0-否。 */
    @Schema(description = "医保是否对码 1-是 0-否", example = "1", allowableValues = {"0", "1"})
    private Boolean ybMatchFlag;

    /** 医保目录编码。 */
    @Schema(description = "医保目录编码", example = "YB2025001")
    private String ybNo;

    /** 药理作用分类编码。 */
    @Schema(description = "药理作用分类编码", example = "YL001")
    private String pharmacologyCategoryCode;


    /** 生产厂家ID（写路径落库；cus_supplier.id，type=生产厂家）。 */
    @Schema(description = "生产厂家ID", example = "50001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long manufacturerId;

    /**
     * 生产厂家名称（可选；仅导入 Excel 文本，由 AppService 解析为 {@link #manufacturerId}；不写入实体 text 列）。
     */
    @Schema(description = "生产厂家名称（导入解析用，不落库）", example = "广州白云山医药集团股份有限公司")
    private String manufacturerText;

    /**
     * 供应商标识（历史字段；药品目录创建/编辑写路径忽略并清空，供应商改由进货等单据维护）。
     */
    @Schema(description = "供应商标识（目录维护已废弃，写路径忽略）", example = "60001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyId;


    /** 限制使用范围说明。 */
    @Schema(description = "限制使用范围说明", example = "仅限呼吸内科使用")
    private String restrictedScope;

    /** 产品特性枚举 1-处方药 2-非处方药 3-特殊管理药品。 */
    @Schema(description = "产品特性枚举 1-处方药 2-非处方药 3-特殊管理药品", example = "1", allowableValues = {"1", "2", "3"})
    private Integer characteristic;

    /**
     * 采购参考价（维护态 {@code PURCHASE}，元/包装单位）。
     *
     * <p>落库经 {@code MonetaryAmountSupport#scaleCalc} 保留 6 位；中药饮片（分类码 {@code 4}）
     * 维护页 UI 展示/录入允许 3 位小数，西药/中成药仍为 2 位展示。</p>
     */
    @Schema(description = "采购参考价（元/包装单位；中药 UI 可 3 位）", example = "10.5")
    private BigDecimal purchasePrice;

    /**
     * 销售参考价（维护态 {@code SALE}，元/包装单位）。
     *
     * <p>落库 6 位；中药饮片维护页 UI 展示/录入允许 3 位小数。</p>
     */
    @Schema(description = "销售参考价（元/包装单位；中药 UI 可 3 位）", example = "15.8")
    private BigDecimal retailPrice;

    /** 药品最高零售限价。 */
    @Schema(description = "药品最高零售限价", example = "18.0")
    private BigDecimal maximumRetailPrice;

    /** 医保类别编码。 */
    @Schema(description = "医保类别编码", example = "YB001")
    private String ybType;

    /** 财务类别编码（字典码：fin_type_code）。 */
    @Schema(description = "财务类别编码（字典码：fin_type_code）", example = "CW001")
    private String typeCode;

    /** 财务类别编码对应的文本值。 */
    @Schema(description = "财务类别编码对应的文本值", example = "西药")
    private String typeCode_dictText;

    /** 单次最小用药频次编码。 */
    @Schema(description = "单次最小用药频次编码", example = "PC001")
    private String minRateCode;

    /** 单次最大用药频次编码。 */
    @Schema(description = "单次最大用药频次编码", example = "PC002")
    private String maxRateCode;

    /** 药品状态枚举 1-启用 2-停用 3-待审核。 */
    @Schema(description = "药品状态枚举 1-启用 2-停用 3-待审核", example = "1", allowableValues = {"1", "2", "3"})
    private String statusEnum;

    /** 药品状态对应的文本值。 */
    @Schema(description = "药品状态对应的文本值", example = "启用")
    private String statusEnum_enumText;

    /** 拆分属性枚举 1-可拆分 2-不可拆分。 */
    @Schema(description = "拆分属性枚举 1-可拆分 2-不可拆分", example = "1", allowableValues = {"1", "2"})
    private String partAttributeEnum;

    /** 国家药品编码（贯标）。 */
    @Schema(description = "国家药品编码（贯标）", example = "860000123456789")
    private String nationalDrugCode;

    /** 药品69码 */
    @Schema(description = "药品69码", example = "6901234567890")
    private String drug69Code;

    /** 限定日剂量（DDD）编码。 */
    @Schema(description = "限定日剂量（DDD）编码", example = "DDD001")
    private String dddCode;

    /** 限定日剂量单位编码。 */
    @Schema(description = "限定日剂量单位编码", example = "DW001")
    private String dddUnitCode;

    /** 单次最大用量限定。 */
    @Schema(description = "单次最大用量限定", example = "2.0")
    private BigDecimal usageLimit;

    /** 抗生素分类编码（字典码：antibiotic_type_code）。 */
    @Schema(description = "抗生素分类编码（字典码：antibiotic_type_code）", example = "KS001")
    private String antibioticCode;

    /** 抗生素分类编码对应的文本值。 */
    @Schema(description = "抗生素分类编码对应的文本值", example = "青霉素类")
    private String antibioticCode_dictText;

    /** 权限限制枚举 1-普通权限 2-专科权限 3-主任权限。 */
    @Schema(description = "权限限制枚举 1-普通权限 2-专科权限 3-主任权限", example = "1", allowableValues = {"1", "2", "3"})
    private String restrictedEnum;

    /** 住院临时医嘱拆分属性 1-可拆分 2-不可拆分。 */
    @Schema(description = "住院临时医嘱拆分属性 1-可拆分 2-不可拆分", example = "1", allowableValues = {"1", "2"})
    private String thoPartAttributeEnum;

    /** 最小库存警戒数量。 */
    @Schema(description = "最小库存警戒数量", example = "10")
    private BigDecimal itemMinQuantity;

    /** 最大库存警戒数量。 */
    @Schema(description = "最大库存警戒数量", example = "100")
    private BigDecimal itemMaxQuantity;

    /** 剂量单位到拆零单位换算比例。 */
    @Schema(description = "剂量单位到拆零单位换算比例", example = "10")
    private BigDecimal doseUnitRatio;

    /** 采购单位到拆零单位换算比例。 */
    @Schema(description = "采购单位到拆零单位换算比例", example = "10")
    private BigDecimal purchaseUnitRatio;

    /** 库存单位到拆零单位换算比例。 */
    @Schema(description = "库存单位到拆零单位换算比例", example = "10")
    private BigDecimal stockUnitRatio;

    /** 销售单位到拆零单位换算比例。 */
    @Schema(description = "销售单位到拆零单位换算比例", example = "10")
    private BigDecimal packageUnitRatio;

    /**
     * 追溯码所贴单位编码（术语 {@code unit_code}；可游离于五类业务单位之外）。
     *
     * <p>药品目录新建/编辑必填；同意入库写入 {@code cus_trace_number.trace_unit_code}。</p>
     */
    @Schema(description = "追溯码单位编码", example = "DW003", maxLength = 64)
    private String traceUnitCode;

    /**
     * 1 个追溯码单位对应的拆零数量；与 {@link #traceUnitCode} 成对，必填且须 &gt; 0。
     */
    @Schema(description = "追溯码单位→拆零倍数", example = "10")
    private BigDecimal traceUnitRatio;

    /** 医保等级（与 yb_type 一致：1自费 2甲类 3乙类 4丙类）。 */
    @Schema(description = "医保等级（与 yb_type 一致：1自费 2甲类 3乙类 4丙类）", example = "2", allowableValues = {"1", "2", "3", "4"})
    private Integer chrgitmLv;

    /** 病案结算类型编码（术语 mr_settlement_type_code）。 */
    @Schema(description = "病案结算类型编码", example = "16", maxLength = 255)
    private String mrSettlementTypeCode;

    /** 处方标志 1-处方药 2-非处方药。 */
    @Schema(description = "处方标志 1-处方药 2-非处方药", example = "1", allowableValues = {"1", "2"})
    private Boolean rxFlag;

    /** 用药说明/注意事项。 */
    @Schema(description = "用药说明/注意事项", example = "饭后服用，每次1粒，每日3次")
    private String dosageInstruction;


    /** 是否为基本药物 1-是 0-否。 */
    @Schema(description = "是否为基本药物 1-是 0-否", example = "1", allowableValues = {"0", "1"})
    private Boolean basicFlag;
    /** 是否国家重点监控药品；勾选=是，口径对齐基药标识。 */
    @Schema(description = "是否国家重点监控药品 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean nationalMonitorFlag;

    /** 是否集采药品；勾选=是，口径对齐基药标识。 */
    @Schema(description = "是否集采药品 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean centralPurchaseFlag;


    /** 是否需要皮试 1-是 0-否。 */
    @Schema(description = "是否需要皮试 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean skinTestFlag;

    /** 是否为注射类药物 1-是 0-否。 */
    @Schema(description = "是否为注射类药物 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean injectFlag;

    /** 儿童用药标志 1-适用 0-不适用。 */
    @Schema(description = "儿童用药标志 1-适用 0-不适用", example = "1", allowableValues = {"0", "1"})
    private Boolean childrenFlag;

    /** 是否为自制药品 1-是 0-否。 */
    @Schema(description = "是否为自制药品 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean selfFlag;

    /** 是否为活性状态 1-是 0-否。 */
    @Schema(description = "是否为活性状态 1-是 0-否", example = "1", allowableValues = {"0", "1"})
    private Boolean activeFlag;

    /** 是否限制使用 1-是 0-否。 */
    @Schema(description = "是否限制使用 1-是 0-否", example = "0", allowableValues = {"0", "1"})
    private Boolean restrictedFlag;

    /** 是否为抗生素 1-是 0-否。 */
    @Schema(description = "是否为抗生素 1-是 0-否", example = "1", allowableValues = {"0", "1"})
    private Boolean antibioticFlag;
}
