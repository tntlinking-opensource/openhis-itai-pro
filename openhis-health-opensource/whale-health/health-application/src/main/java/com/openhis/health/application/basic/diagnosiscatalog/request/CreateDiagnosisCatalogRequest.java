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

package com.openhis.health.application.basic.diagnosiscatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 新增疾病目录请求：映射为临床域 {@link com.openhis.health.domain.clinical.conditiondefinition.command.AddDiseaseCommand}。
 */
@Data
@Schema(description = "新增疾病目录请求")
public class CreateDiagnosisCatalogRequest {

    /** 疾病来源/分类业务码（必填，与初始化下拉一致）。 */
    @Schema(description = "所属分类编码")
    private String sourceEnum;

    /** 疾病/条件编码。 */
    @Schema(description = "疾病编码")
    private String conditionCode;

    /** 疾病名称（必填）。 */
    @Schema(description = "疾病名称")
    private String name;

    /** 医保诊断类型码。 */
    @Schema(description = "诊断类型编码")
    private String typeCode;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 医保标记：1 是，0 否。 */
    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    /** 特慢病编码。 */
    @Schema(description = "特慢病编码")
    private String diseaseCode;

    /** 医保目录编码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 拼音助记。 */
    @Schema(description = "拼音助记码")
    private String pyStr;

    /** 五笔助记。 */
    @Schema(description = "五笔助记码")
    private String wbStr;

    /** 医保对码标记：1 已对照，0 未对照。 */
    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    /**
     * 发布状态码；空时由应用服务默认 {@link com.openhis.health.domain.share.enums.PublicationStatus#ACTIVE}。
     */
    @Schema(description = "状态编码")
    private String statusEnum;
}
