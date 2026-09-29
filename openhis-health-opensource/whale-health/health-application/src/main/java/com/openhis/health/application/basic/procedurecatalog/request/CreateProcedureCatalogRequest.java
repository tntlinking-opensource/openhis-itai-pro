/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 新增手术目录请求：映射为临床域
 * {@link com.openhis.health.domain.clinical.proceduredefinition.command.AddProcedureDefinitionCommand}（IF-BAS-POC-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新增手术目录请求", requiredProperties = {"sourceEnum", "name"})
public class CreateProcedureCatalogRequest {

    /** 目录来源码（{@code clinical_proc_v3} / {@code general_op}）；必填。 */
    @Schema(description = "目录来源码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sourceEnum;

    /** 手术操作主编码；空则由领域生成业务序列号。 */
    @Schema(description = "手术操作编码")
    private String procedureCode;

    /** 手术/操作名称；必填。 */
    @Schema(description = "手术操作名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /** 类别/类型编码（国临「类别」列）。 */
    @Schema(description = "类别编码")
    private String typeCode;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 医保标记（请求侧 0/1；Mapper 转 Boolean）。 */
    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    /** 医保手术码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 国家临床版手术编码（国临码）。 */
    @Schema(description = "国家临床版手术编码（国临码）")
    private String clinicCode;

    /** 拼音助记码；空则按名称重算。 */
    @Schema(description = "拼音助记码")
    private String pyStr;

    /** 五笔助记码；空则按名称重算。 */
    @Schema(description = "五笔助记码")
    private String wbStr;

    /** 医保对码标记（请求侧 0/1；Mapper 转 Boolean）。 */
    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    /**
     * 发布状态码；空时由应用服务默认
     * {@link com.openhis.health.domain.share.enums.PublicationStatus#ACTIVE}。
     */
    @Schema(description = "状态编码")
    private String statusEnum;

    /**
     * 挂靠诊疗项目定义主键（{@code wkf_activity_definition.id}）；可选。
     *
     * <p>国临 PD 计费映射（BR-POC-010）；本模块只存外键，不写诊疗项目本体。</p>
     */
    @Schema(description = "挂靠诊疗项目定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityDefinitionId;
}
