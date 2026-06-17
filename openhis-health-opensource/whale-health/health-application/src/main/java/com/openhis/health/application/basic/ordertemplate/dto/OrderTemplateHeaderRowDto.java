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

package com.openhis.health.application.basic.ordertemplate.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 组套模板头列表/详情行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板头行")
public class OrderTemplateHeaderRowDto {

    @Schema(description = "组套模板头 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderTemplateId;

    @Schema(description = "模板名称")
    private String name;

    /** 模板编码。 */
    @Schema(description = "模板编码")
    private String templateCode;

    /** 绑定类型 code。 */
    @Schema(description = "绑定类型 code")
    private String bindingEnum;

    /** 绑定类型展示名。 */
    @Schema(description = "绑定类型展示名")
    private String bindingEnumText;

    /** 适用场景 code。 */
    @Schema(description = "适用场景 code")
    private String sceneEnum;

    /** 适用场景展示名。 */
    @Schema(description = "适用场景展示名")
    private String sceneEnumText;

    /** 组套种类 code。 */
    @Schema(description = "组套种类 code")
    private String templateKindEnum;

    /** 组套种类展示名。 */
    @Schema(description = "组套种类展示名")
    private String templateKindEnumText;

    /** 模板来源 code。 */
    @Schema(description = "模板来源 code")
    private String templateSourceEnum;

    /** 模板来源展示名。 */
    @Schema(description = "模板来源展示名")
    private String templateSourceEnumText;

    /** 模板状态 code。 */
    @Schema(description = "模板状态 code")
    private String statusEnum;

    /** 模板状态展示名。 */
    @Schema(description = "模板状态展示名")
    private String statusEnumText;

    /** 列表排序权重。 */
    @Schema(description = "排序权重")
    private Integer sortWeight;

    /** TCM 默认付数。 */
    @Schema(description = "TCM 默认付数")
    private BigDecimal defaultChineseHerbsDoseQuantity;

    /** TCM 默认代煎标志。 */
    @Schema(description = "TCM 默认代煎")
    private Boolean defaultSufferingFlag;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
