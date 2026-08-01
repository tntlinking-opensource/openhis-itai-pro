/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新增手术目录请求：映射为临床域 {@link com.openhis.health.domain.clinical.proceduredefinition.command.AddProcedureDefinitionCommand}。
 */
@Data
@Schema(description = "新增手术目录请求")
public class CreateProcedureCatalogRequest {

    @Schema(description = "所属分类编码")
    private String sourceEnum;

    @Schema(description = "手术操作编码")
    private String procedureCode;

    @Schema(description = "手术操作名称")
    private String name;

    @Schema(description = "类别编码")
    private String typeCode;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    @Schema(description = "医保编码")
    private String ybNo;

    @Schema(description = "国家临床版手术编码（国临码）")
    private String clinicCode;

    @Schema(description = "拼音助记码")
    private String pyStr;

    @Schema(description = "五笔助记码")
    private String wbStr;

    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    /**
     * 发布状态码；空时由应用服务默认 {@link com.openhis.health.domain.share.enums.PublicationStatus#ACTIVE}。
     */
    @Schema(description = "状态编码")
    private String statusEnum;
}
