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

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属分类编码")
    private String sourceEnum;

    /** 疾病来源/分类展示名（由 Mapper 经术语服务解析）。 */
    @Schema(description = "所属分类文本")
    private String sourceEnum_enumText;

    /** 疾病/条件业务编码。 */
    @Schema(description = "疾病编码")
    private String conditionCode;

    /** 特慢病等业务编码。 */
    @Schema(description = "特慢病编码")
    private String diseaseCode;

    /** 疾病名称。 */
    @Schema(description = "疾病名称")
    private String name;

    /** 医保诊断类型码（术语集 DIAG_TYPE）。 */
    @Schema(description = "诊断类型编码")
    private String typeCode;

    /** 医保诊断类型展示名。 */
    @Schema(description = "诊断类型文本")
    private String typeCode_dictText;

    /** 描述信息。 */
    @Schema(description = "描述")
    private String description;

    /** 拼音助记码。 */
    @Schema(description = "拼音助记码")
    private String pyStr;

    /** 五笔助记码。 */
    @Schema(description = "五笔助记码")
    private String wbStr;

    /** 是否医保相关：1 是，0 否（与前端整型约定一致）。 */
    @Schema(description = "医保标记（0/1）")
    private Integer ybFlag;

    /** 医保目录编码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 是否已完成医保对码：1 是，0 否。 */
    @Schema(description = "医保对码标记（0/1）")
    private Integer ybMatchFlag;

    /** 发布状态持久化码（{@link com.openhis.health.domain.share.enums.PublicationStatus#getCode()}）。 */
    @Schema(description = "状态编码")
    private String statusEnum;

    /** 发布状态展示文案。 */
    @Schema(description = "状态文本")
    private String statusEnum_enumText;
}
