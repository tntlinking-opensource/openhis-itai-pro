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

package com.openhis.health.application.basic.ordertemplate.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 维护端保存组套模板头请求（IF-BAS-OTL-04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "维护端保存组套模板头请求", requiredProperties = {"bindingEnum", "sceneEnum", "templateKindEnum", "name"})
public class SaveMaintainedOrderTemplateHeaderRequest {

    @Schema(description = "组套模板头 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderTemplateId;

    @Schema(description = "绑定类型 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "绑定类型不能为空")
    private String bindingEnum;

    /** 归属对象 id；缺省由 ScopeResolver 补全。 */
    @Schema(description = "归属对象 id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long objectId;

    /** 适用场景。 */
    @Schema(description = "适用场景 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "适用场景不能为空")
    private String sceneEnum;

    /** 组套种类。 */
    @Schema(description = "组套种类 code", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "组套种类不能为空")
    private String templateKindEnum;

    /** 模板编码。 */
    @Schema(description = "模板编码")
    private String templateCode;

    /** 模板名称。 */
    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "模板名称不能为空")
    private String name;

    /** 描述。 */
    @Schema(description = "描述")
    private String description;

    /** 排序权重。 */
    @Schema(description = "排序权重")
    private Integer sortWeight;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;

    /** TCM 默认付数。 */
    @Schema(description = "TCM 默认付数")
    private BigDecimal defaultChineseHerbsDoseQuantity;

    /** TCM 默认代煎。 */
    @Schema(description = "TCM 默认代煎")
    private Boolean defaultSufferingFlag;
}
