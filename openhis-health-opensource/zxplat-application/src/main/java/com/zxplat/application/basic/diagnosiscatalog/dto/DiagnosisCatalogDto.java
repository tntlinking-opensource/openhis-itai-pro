/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.dto;

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

    /** 目录主键（字符串出站，防前端雪花 Long 精度丢失）。 */
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属分类编码（{@code source_enum} 术语 code）。 */
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

    /** 医保诊断名称（由 ybNo 反查疾病与诊断等医保目录 name；未对码为空）。 */
    @Schema(description = "医保诊断名称")
    private String ybName;

    /** 国家临床版诊断编码（国临码）。医保目录列表可能为一对多逗号拼接。 */
    @Schema(description = "国家临床版诊断编码（国临码）；医保目录列表可逗号拼接多个")
    private String clinicCode;

    /** 国家临床版诊断名称。医保目录列表与 clinicCode 同序逗号拼接；由国临 yb_no 反查。 */
    @Schema(description = "国家临床版诊断名称；医保目录列表可逗号拼接多个")
    private String clinicName;

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
