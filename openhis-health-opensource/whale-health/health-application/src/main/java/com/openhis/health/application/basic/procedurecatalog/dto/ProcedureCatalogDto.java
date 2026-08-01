/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手术操作目录列表行展示：含术语解析后的分类/状态等可读文案。
 */
@Data
@Schema(description = "手术操作目录列表项")
public class ProcedureCatalogDto {

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属分类编码")
    private String sourceEnum;

    @Schema(description = "所属分类文本")
    private String sourceEnum_enumText;

    @Schema(description = "手术操作编码")
    private String procedureCode;

    @Schema(description = "手术操作名称")
    private String name;

    @Schema(description = "类别编码")
    private String typeCode;

    @Schema(description = "类别文本")
    private String typeCode_dictText;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "拼音助记码")
    private String pyStr;

    @Schema(description = "五笔助记码")
    private String wbStr;

    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    @Schema(description = "医保编码")
    private String ybNo;

    @Schema(description = "国家临床版手术编码（国临码）")
    private String clinicCode;

    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    @Schema(description = "状态编码")
    private String statusEnum;

    @Schema(description = "状态文本")
    private String statusEnum_enumText;
}
