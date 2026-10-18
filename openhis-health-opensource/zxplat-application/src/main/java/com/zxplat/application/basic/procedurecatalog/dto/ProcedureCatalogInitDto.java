/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 手术目录维护页初始化数据：分类、发布状态等下拉选项。
 */
@Data
@Schema(description = "手术目录初始化数据")
public class ProcedureCatalogInitDto {

    @Schema(description = "手术分类")
    private List<OptionDto> procedureCategoryList;

    @Schema(description = "状态选项")
    private List<OptionDto> statusFlagOptions;
}
