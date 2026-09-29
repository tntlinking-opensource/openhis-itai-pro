/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 手术操作目录列表行展示：含术语解析后的分类/状态等可读文案（IF-BAS-POC-02/04/05 回显）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "手术操作目录列表项")
public class ProcedureCatalogDto {

    /** 手术定义主键。 */
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 目录来源码。 */
    @Schema(description = "所属分类编码")
    private String sourceEnum;

    /** 目录来源展示文案（术语解析）。 */
    @Schema(description = "所属分类文本")
    private String sourceEnum_enumText;

    /** 手术操作主编码。 */
    @Schema(description = "手术操作编码")
    private String procedureCode;

    /** 手术/操作名称。 */
    @Schema(description = "手术操作名称")
    private String name;

    /** 类别/类型编码。 */
    @Schema(description = "类别编码")
    private String typeCode;

    /** 类别展示文案。 */
    @Schema(description = "类别文本")
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

    /** 医保标记（出参 0/1）。 */
    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    /** 医保手术码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 国家临床版手术编码（国临码）。 */
    @Schema(description = "国家临床版手术编码（国临码）")
    private String clinicCode;

    /** 医保对码标记（出参 0/1）。 */
    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    /** 发布状态码。 */
    @Schema(description = "状态编码")
    private String statusEnum;

    /** 发布状态展示文案。 */
    @Schema(description = "状态文本")
    private String statusEnum_enumText;

    /**
     * 挂靠诊疗项目定义主键（{@code wkf_activity_definition.id}）；可空。
     *
     * <p>国临选品后取本字段生成申请行计费（BR-POC-010）。</p>
     */
    @Schema(description = "挂靠诊疗项目定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityDefinitionId;
}
