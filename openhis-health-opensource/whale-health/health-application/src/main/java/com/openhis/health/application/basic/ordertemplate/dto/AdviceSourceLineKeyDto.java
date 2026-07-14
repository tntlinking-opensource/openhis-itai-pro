/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.ordertemplate.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生站另存医嘱模板时的源行键（IF-BAS-OTL-10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医嘱源行键")
public class AdviceSourceLineKeyDto {

    /** 请求主键。 */
    @NotNull(message = "请求主键不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "请求主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestId;

    /** 产品定义物理表名。 */
    @NotBlank(message = "产品定义表名不能为空")
    /** 产品定义表名。 */
    @Schema(description = "产品定义表名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String productTableName;

    /** 列表稳定键。 */
    @Schema(description = "列表稳定键")
    private String listRowKey;
}
