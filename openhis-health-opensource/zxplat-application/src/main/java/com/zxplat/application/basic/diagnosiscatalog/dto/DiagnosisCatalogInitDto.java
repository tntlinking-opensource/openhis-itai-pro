/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.dto;

import com.zxplat.framework.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 疾病目录维护页初始化数据：分类、发布状态、医保诊断类型等下拉选项。
 *
 * <p>数据来自术语服务与 {@link com.zxplat.domain.share.enums.PublicationStatus} 枚举选项。</p>
 */
@Data
@Schema(description = "疾病目录初始化数据")
public class DiagnosisCatalogInitDto {

    @Schema(description = "疾病分类")
    /** 状态选项。 */
    private List<OptionDto> diseaseCategoryList;

    @Schema(description = "状态选项")
    private List<OptionDto> statusFlagOptions;

    /** 医保诊断类型。 */
    @Schema(description = "医保诊断类型")
    private List<OptionDto> ybDiagTypes;
}
