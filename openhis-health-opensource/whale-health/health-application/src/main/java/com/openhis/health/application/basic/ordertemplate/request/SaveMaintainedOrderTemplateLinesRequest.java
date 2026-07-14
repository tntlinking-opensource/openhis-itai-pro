/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    /** 组套模板头 id。 */
    private Long orderTemplateId;

    /** 明细行全量集合。 */
    @Schema(description = "明细行全量集合", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "明细行不能为空")
    private List<OrderTemplateLineEditDto> lines;
}
