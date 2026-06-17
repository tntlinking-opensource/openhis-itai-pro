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

package com.openhis.health.application.basic.diagnosiscatalog.dto;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 疾病目录维护页初始化数据：分类、发布状态、医保诊断类型等下拉选项。
 *
 * <p>数据来自术语服务与 {@link com.openhis.health.domain.share.enums.PublicationStatus} 枚举选项。</p>
 */
@Data
@Schema(description = "疾病目录初始化数据")
public class DiagnosisCatalogInitDto {

    @Schema(description = "疾病分类")
    private List<OptionDto> diseaseCategoryList;

    @Schema(description = "状态选项")
    private List<OptionDto> statusFlagOptions;

    /** 医保诊断类型下拉，码值对齐术语集 {@code DIAG_TYPE}。 */
    @Schema(description = "医保诊断类型")
    private List<OptionDto> ybDiagTypes;
}
