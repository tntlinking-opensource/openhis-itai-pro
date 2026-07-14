/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-BAS-OTL-10：医生站另存医嘱模板响应。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站另存医嘱模板响应")
public class SaveOrderTemplateFromAdviceResponse {

    /** 新建模板 id。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "新建模板 id")
    private Long orderTemplateId;
}
