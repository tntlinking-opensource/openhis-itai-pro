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
/**
 * CreateServiceCatalogRequest。
 */
public class CreateServiceCatalogRequest {

    @Schema(description = "目录类别编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "ZY001", maxLength = 255)
    @NotBlank(message = "目录类别不能为空")
    @Size(max = 255, message = "目录类别编码长度不能超过255个字符")
    /**
     * categoryCode。
     */
    private String categoryCode;

    @Schema(description = "诊疗项目业务编码", example = "ZL00001", maxLength = 255)
    @Size(max = 255, message = "业务编码长度不能超过255个字符")
    /**
     * busNo。
     */
    private String busNo;

    @Schema(description = "诊疗项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "血常规检查", maxLength = 255)
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 255, message = "项目名称长度不能超过255个字符")
    /**
     * name。
     */
    private String name;

    @Schema(description = "项目名称拼音简码", example = "XCJJC", maxLength = 255)
    @Size(max = 255, message = "拼音简码长度不能超过255个字符")
    /**
     * pyStr。
     */
    private String pyStr;

    @Schema(description = "项目名称五笔简码", example = "TGCR", maxLength = 255)
    @Size(max = 255, message = "五笔简码长度不能超过255个字符")
    /**
     * wbStr。
     */
    private String wbStr;

    @Schema(description = "诊疗类型枚举（1=检验，2=检查，3=护理，4=手术，5=其他）", example = "2", allowableValues = {"1", "2", "3", "4", "5"})
    @Min(value = 1, message = "诊疗类型枚举值必须≥1")
    @Max(value = 5, message = "诊疗类型枚举值必须≤5")
    /**
     * typeEnum。
     */
    private Integer typeEnum;

    @Schema(description = "销售/计价单位编码", example = "001", maxLength = 255)
    @Size(max = 255, message = "销售/计价单位编码长度不能超过255个字符")
    /**
     * packageUnitCode。
     */
    private String packageUnitCode;

    @Schema(description = "诊疗业务版本", example = "V1", maxLength = 255)
    @Size(max = 255, message = "业务版本长度不能超过255个字符")
    /**
     * activityVersion。
     */
    private String activityVersion;

    @Schema(description = "商品名称", maxLength = 255)
    @Size(max = 255, message = "商品名称长度不能超过255个字符")
    /**
     * merchandiseName。
     */
    private String merchandiseName;

    @Schema(description = "默认门诊/就诊单位编码", maxLength = 255)
    @Size(max = 255, message = "默认门诊单位编码长度不能超过255个字符")
    /**
     * defEncounterUnitCode。
     */
    private String defEncounterUnitCode;

    @Schema(description = "默认住院单位编码", maxLength = 255)
    @Size(max = 255, message = "默认住院单位编码长度不能超过255个字符")
    /**
     * defInhospitalUnitCode。
     */
    private String defInhospitalUnitCode;

    @Schema(description = "医保标识（0=非医保，1=医保）", example = "1", allowableValues = {"0", "1"})
    @Min(value = 0, message = "医保标识只能是0或1")
    @Max(value = 1, message = "医保标识只能是0或1")
    /**
     * ybFlag。
     */
    private Integer ybFlag;

    @Schema(description = "医保备案编码", example = "YB2025001", maxLength = 255)
    @Size(max = 255, message = "医保编号长度不能超过255个字符")
    /**
     * ybNo。
     */
    private String ybNo;

    @Schema(description = "医保对码标记（0=未匹配，1=已匹配）", example = "1", allowableValues = {"0", "1"})
    @Min(value = 0, message = "医保对码标记只能是0或1")
    @Max(value = 1, message = "医保对码标记只能是0或1")
    /**
     * ybMatchFlag。
     */
    private Integer ybMatchFlag;

    @Schema(description = "检查/治疗部位编码", example = "BW001", maxLength = 255)
    @Size(max = 255, message = "身体部位编码长度不能超过255个字符")
    /**
     * bodySiteCode。
     */
    private String bodySiteCode;

    @Schema(description = "检验所需标本编码", example = "BB001", maxLength = 255)
    @Size(max = 255, message = "所需标本编码长度不能超过255个字符")
    /**
     * specimenCode。
     */
    private String specimenCode;

    @Schema(description = "诊疗项目说明文本", example = "全血细胞计数+五分类", maxLength = 2000)
    @Size(max = 2000, message = "项目说明长度不能超过2000个字符")
    /**
     * descriptionText。
     */
    private String descriptionText;

    @Schema(description = "计费规则ID", example = "100")
    /**
     * ruleId。
     */
    private Integer ruleId;

    @Schema(description = "所属科室ID", example = "10002")
    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * orgId。
     */
    private Long orgId;

    @Schema(description = "所属科室名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "检验科")
    /**
     * orgId_dictText。
     */
    private String orgId_dictText;

    @Schema(description = "诊疗位置ID", example = "20001")
    @JsonSerialize(using = ToStringSerializer.class)
    /**
     * locationId。
     */
    private Long locationId;

    @Schema(description = "所在位置名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "门诊2楼检验室")
    /**
     * locationId_dictText。
     */
    private String locationId_dictText;

    @Schema(description = "财务类别编码（关联字典fin_type_code）", example = "CW001", maxLength = 255)
    @Size(max = 255, message = "财务类别编码长度不能超过255个字符")
    /**
     * itemTypeCode。
     */
    private String itemTypeCode;

    @Schema(description = "财务类别名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "检验费")
    /**
     * itemTypeCode_dictText。
     */
    private String itemTypeCode_dictText;

    @Schema(description = "医保类别编码（关联字典med_chrgitm_type）", example = "YBLC001", maxLength = 255)
    @Size(max = 255, message = "医保类别编码长度不能超过255个字符")
    /**
     * ybType。
     */
    private String ybType;

    @Schema(description = "医保类别名称（字典回显，无需传参）", accessMode = Schema.AccessMode.READ_ONLY, example = "甲类")
    /**
     * ybType_dictText。
     */
    private String ybType_dictText;

    @Schema(description = "项目购入价（精度：10位整数，2位小数）", example = "15.50")
    @Digits(integer = 10, fraction = 2, message = "购入价精度错误，最多10位整数，2位小数")
    @DecimalMin(value = "0.00", message = "购入价不能小于0")
    /**
     * purchasePrice。
     */
    private BigDecimal purchasePrice;

    @Schema(description = "项目零售价（精度：10位整数，2位小数）", example = "20.00")
    @Digits(integer = 10, fraction = 2, message = "零售价精度错误，最多10位整数，2位小数")
    @DecimalMin(value = "0.00", message = "零售价不能小于0")
    /**
     * retailPrice。
     */
    private BigDecimal retailPrice;

    @Schema(description = "项目最高零售价（精度：10位整数，2位小数）", example = "25.00")
    @Digits(integer = 10, fraction = 2, message = "最高零售价精度错误，最多10位整数，2位小数")
    @DecimalMin(value = "0.00", message = "最高零售价不能小于0")
    /**
     * maximumRetailPrice。
     */
    private BigDecimal maximumRetailPrice;

    @Schema(description = "医保收费项目等级（1=甲类，2=乙类，3=丙类）", example = "1", allowableValues = {"1", "2", "3"})
    @Min(value = 1, message = "医保收费项目等级只能是1、2、3")
    @Max(value = 3, message = "医保收费项目等级只能是1、2、3")
    /**
     * chrgitmLv。
     */
    private Integer chrgitmLv;

    @Schema(description = "划价标记（0=未划价，1=已划价）", example = "1", allowableValues = {"0", "1"})
    @Min(value = 0, message = "划价标记只能是0或1")
    @Max(value = 1, message = "划价标记只能是0或1")
    /**
     * pricingFlag。
     */
    private Integer pricingFlag;

    @Schema(description = "物价局备案编码", example = "WJ2025001", maxLength = 255)
    @Size(max = 255, message = "物价编码长度不能超过255个字符")
    /**
     * priceCode。
     */
    private String priceCode;

    @Schema(description = "子项列表")
    /**
     * children。
     */
    private List<ServiceCatalogChildDto> children;
}
