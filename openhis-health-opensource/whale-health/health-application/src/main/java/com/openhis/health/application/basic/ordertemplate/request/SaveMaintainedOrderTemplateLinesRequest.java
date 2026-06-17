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
import com.openhis.health.application.basic.ordertemplate.dto.OrderTemplateLineEditDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 维护端保存组套模板行请求（IF-BAS-OTL-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "维护端保存组套模板行请求", requiredProperties = {"orderTemplateId", "lines"})
public class SaveMaintainedOrderTemplateLinesRequest {

    @Schema(description = "组套模板头 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "组套模板 id 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderTemplateId;

    /** 全量对齐行集合。 */
    @Schema(description = "明细行全量集合", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "明细行不能为空")
    private List<OrderTemplateLineEditDto> lines;
}
