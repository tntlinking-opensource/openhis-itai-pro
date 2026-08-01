/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.diagnosiscatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新增疾病目录请求：映射为临床域 {@link com.openhis.health.domain.clinical.conditiondefinition.command.AddDiseaseCommand}。
 */
@Data
@Schema(description = "新增疾病目录请求")
public class CreateDiagnosisCatalogRequest {

    @Schema(description = "所属分类编码")
    /** 疾病编码。 */
    private String sourceEnum;

    @Schema(description = "疾病编码")
    private String conditionCode;

    /** 疾病名称。 */
    @Schema(description = "疾病名称")
    private String name;

    /** 诊断类型编码。 */
    @Schema(description = "诊断类型编码")
    private String typeCode;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 医保标记（0/1）。 */
    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    /** 医保编码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 国家临床版诊断编码（国临码）。 */
    @Schema(description = "国家临床版诊断编码（国临码）")
    private String clinicCode;

    /** 拼音助记码。 */
    @Schema(description = "拼音助记码")
    private String pyStr;

    /** 五笔助记码。 */
    @Schema(description = "五笔助记码")
    private String wbStr;

    /** 医保对码标记（0/1）。 */
    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    /**
     * 发布状态码；空时由应用服务默认 {@link com.openhis.health.domain.share.enums.PublicationStatus#ACTIVE}。
     */
    @Schema(description = "状态编码")
    private String statusEnum;
}
