/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.diagnosiscatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 疾病（病种）目录列表行展示：含术语解析后的分类/状态/诊断类型等可读文案。
 */
@Data
@Schema(description = "疾病（病种）目录列表项")
public class DiagnosisCatalogDto {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 所属分类编码。 */
    private Long id;

    @Schema(description = "所属分类编码")
    private String sourceEnum;

    /** 所属分类文本。 */
    @Schema(description = "所属分类文本")
    private String sourceEnum_enumText;

    /** 疾病编码。 */
    @Schema(description = "疾病编码")
    private String conditionCode;

    /** 疾病名称。 */
    @Schema(description = "疾病名称")
    private String name;

    /** 诊断类型编码。 */
    @Schema(description = "诊断类型编码")
    private String typeCode;

    /** 诊断类型文本。 */
    @Schema(description = "诊断类型文本")
    private String typeCode_dictText;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 拼音助记码。 */
    @Schema(description = "拼音助记码")
    private String pyStr;

    /** 五笔助记码。 */
    @Schema(description = "五笔助记码")
    private String wbStr;

    /** 医保标记（0/1）。 */
    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    /** 医保编码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 国家临床版诊断编码（国临码）。 */
    @Schema(description = "国家临床版诊断编码（国临码）")
    private String clinicCode;

    /** 医保对码标记（0/1）。 */
    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    /** 状态编码。 */
    @Schema(description = "状态编码")
    private String statusEnum;

    /** 状态文本。 */
    @Schema(description = "状态文本")
    private String statusEnum_enumText;
}
