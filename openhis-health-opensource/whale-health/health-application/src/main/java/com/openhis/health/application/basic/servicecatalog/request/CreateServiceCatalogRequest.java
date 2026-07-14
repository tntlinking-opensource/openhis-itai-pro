/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.basic.servicecatalog.dto.ServiceCatalogChildDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * 诊疗目录（活动定义）创建请求：字段与领域创建命令项一一对应，子项用于组合项目场景。
 */
@Data
@Accessors(chain = true)
@Schema(description = "诊疗目录创建参数")
public class CreateServiceCatalogRequest {

    @Schema(description = "目录类别编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "ZY001", maxLength = 255)
    @NotBlank(message = "目录类别不能为空")
    @Size(max = 255, message = "目录类别编码长度不能超过255个字符")
    /** 目录类别编码。 */
    private String categoryCode;

    /** 诊疗项目业务编码。 */
    @Schema(description = "诊疗项目业务编码", example = "ZL00001", maxLength = 255)
    @Size(max = 255, message = "业务编码长度不能超过255个字符")
    private String busNo;

    /** 诊疗项目名称。 */
    @Schema(description = "诊疗项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "血常规检查", maxLength = 255)
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 255, message = "项目名称长度不能超过255个字符")
    private String name;

    /** 项目名称拼音简码。 */
    @Schema(description = "项目名称拼音简码", example = "XCJJC", maxLength = 255)
    @Size(max = 255, message = "拼音简码长度不能超过255个字符")
    private String pyStr;

    /** 项目名称五笔简码。 */
    @Schema(description = "项目名称五笔简码", example = "TGCR", maxLength = 255)
    @Size(max = 255, message = "五笔简码长度不能超过255个字符")
    private String wbStr;

    /** 诊疗类型枚举（1=检验，2=检查，3=护理，4=手术，5=其他）。 */
    @Schema(description = "诊疗类型枚举（1=检验，2=检查，3=护理，4=手术，5=其他）", example = "2", allowableValues = {"1", "2", "3", "4", "5"})
    @Min(value = 1, message = "诊疗类型枚举值必须≥1")
    @Max(value = 5, message = "诊疗类型枚举值必须≤5")
    private Integer typeEnum;

    /** 销售/计价单位编码。 */
    @Schema(description = "销售/计价单位编码", example = "001", maxLength = 255)
    @Size(max = 255, message = "销售/计价单位编码长度不能超过255个字符")
    private String packageUnitCode;

    /** 诊疗业务版本。 */
    @Schema(description = "诊疗业务版本", example = "V1", maxLength = 255)
    @Size(max = 255, message = "业务版本长度不能超过255个字符")
    private String activityVersion;

    /** 商品名称。 */
    @Schema(description = "商品名称", maxLength = 255)
    @Size(max = 255, message = "商品名称长度不能超过255个字符")
    private String merchandiseName;

    /** 默认门诊/就诊单位编码。 */
    @Schema(description = "默认门诊/就诊单位编码", maxLength = 255)
    @Size(max = 255, message = "默认门诊单位编码长度不能超过255个字符")
    private String defEncounterUnitCode;

    /** 默认住院单位编码。 */
    @Schema(description = "默认住院单位编码", maxLength = 255)
    @Size(max = 255, message = "默认住院单位编码长度不能超过255个字符")
    private String defInhospitalUnitCode;

    /** 医保标识（0=非医保，1=医保）。 */
    @Schema(description = "医保标识（0=非医保，1=医保）", example = "1", allowableValues = {"0", "1"})
    @Min(value = 0, message = "医保标识只能是0或1")
    @Max(value = 1, message = "医保标识只能是0或1")
    private Integer ybFlag;

    /** 医保备案编码。 */
    @Schema(description = "医保备案编码", example = "YB2025001", maxLength = 255)
    @Size(max = 255, message = "医保编号长度不能超过255个字符")
    private String ybNo;

    /** 医保对码标记（0=未匹配，1=已匹配）。 */
    @Schema(description = "医保对码标记（0=未匹配，1=已匹配）", example = "1", allowableValues = {"0", "1"})
    @Min(value = 0, message = "医保对码标记只能是0或1")
    @Max(value = 1, message = "医保对码标记只能是0或1")
    private Integer ybMatchFlag;

    /** 检查/治疗部位编码。 */
    @Schema(description = "检查/治疗部位编码", example = "BW001", maxLength = 255)
    @Size(max = 255, message = "身体部位编码长度不能超过255个字符")
    private String bodySiteCode;

    /** 检验所需标本编码。 */
    @Schema(description = "检验所需标本编码", example = "BB001", maxLength = 255)
    @Size(max = 255, message = "所需标本编码长度不能超过255个字符")
    private String specimenCode;

    /** 诊疗项目说明文本。 */
    @Schema(description = "诊疗项目说明文本", example = "全血细胞计数+五分类", maxLength = 2000)
    @Size(max = 2000, message = "项目说明长度不能超过2000个字符")
    private String descriptionText;

    /** 计费规则ID。 */
    @Schema(description = "计费规则ID", example = "100")
    private Integer ruleId;

    /** 所属科室ID。 */
    @Schema(description = "所属科室ID", example = "10002")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 所属科室名称（字典回显，无需传参）。 */
    @Schema(description = "所属科室名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "检验科")
    private String orgId_dictText;

    /** 诊疗位置ID。 */
    @Schema(description = "诊疗位置ID", example = "20001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 所在位置名称（字典回显，无需传参）。 */
    @Schema(description = "所在位置名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "门诊2楼检验室")
    private String locationId_dictText;

    /** 财务类别编码（关联字典fin_type_code）。 */
    @Schema(description = "财务类别编码（关联字典fin_type_code）", example = "CW001", maxLength = 255)
    @Size(max = 255, message = "财务类别编码长度不能超过255个字符")
    private String itemTypeCode;

    /** 财务类别名称（字典回显，无需传参）。 */
    @Schema(description = "财务类别名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "检验费")
    private String itemTypeCode_dictText;

    /** 医保类别编码（关联字典med_chrgitm_type）。 */
    @Schema(description = "医保类别编码（关联字典med_chrgitm_type）", example = "YBLC001", maxLength = 255)
    @Size(max = 255, message = "医保类别编码长度不能超过255个字符")
    private String ybType;

    /** 医保类别名称（字典回显，无需传参）。 */
    @Schema(description = "医保类别名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "甲类")
    private String ybType_dictText;

    /** 项目购入价（精度：10位整数，2位小数）。 */
    @Schema(description = "项目购入价（精度：10位整数，2位小数）", example = "15.50")
    @Digits(integer = 10, fraction = 2, message = "购入价精度错误，最多10位整数，2位小数")
    @DecimalMin(value = "0.00", message = "购入价不能小于0")
    private BigDecimal purchasePrice;

    /** 项目零售价（精度：10位整数，2位小数）。 */
    @Schema(description = "项目零售价（精度：10位整数，2位小数）", example = "20.00")
    @Digits(integer = 10, fraction = 2, message = "零售价精度错误，最多10位整数，2位小数")
    @DecimalMin(value = "0.00", message = "零售价不能小于0")
    private BigDecimal retailPrice;

    /** 项目最高零售价（精度：10位整数，2位小数）。 */
    @Schema(description = "项目最高零售价（精度：10位整数，2位小数）", example = "25.00")
    @Digits(integer = 10, fraction = 2, message = "最高零售价精度错误，最多10位整数，2位小数")
    @DecimalMin(value = "0.00", message = "最高零售价不能小于0")
    private BigDecimal maximumRetailPrice;

    /** 医保收费项目等级（1=甲类，2=乙类，3=丙类）。 */
    @Schema(description = "医保收费项目等级（1=甲类，2=乙类，3=丙类）", example = "1", allowableValues = {"1", "2", "3"})
    @Min(value = 1, message = "医保收费项目等级只能是1、2、3")
    @Max(value = 3, message = "医保收费项目等级只能是1、2、3")
    private Integer chrgitmLv;

    /** 病案结算类型编码（术语 mr_settlement_type_code）。 */
    @Schema(description = "病案结算类型编码", example = "6", maxLength = 255)
    @Size(max = 255, message = "病案结算类型编码长度不能超过255个字符")
    private String mrSettlementTypeCode;

    /** 划价标记（0=未划价，1=已划价）。 */
    @Schema(description = "划价标记（0=未划价，1=已划价）", example = "1", allowableValues = {"0", "1"})
    @Min(value = 0, message = "划价标记只能是0或1")
    @Max(value = 1, message = "划价标记只能是0或1")
    private Integer pricingFlag;

    /** 物价局备案编码。 */
    @Schema(description = "物价局备案编码", example = "WJ2025001", maxLength = 255)
    @Size(max = 255, message = "物价编码长度不能超过255个字符")
    private String priceCode;

    /** 子项列表。 */
    @Schema(description = "子项列表")
    private List<ServiceCatalogChildDto> children;
}
