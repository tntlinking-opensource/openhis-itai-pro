/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新药品目录请求：继承新建字段并携带药品定义主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateMedicationCatalogRequest extends CreateMedicationCatalogRequest {
    /**
     * 药品定义主键（序列化为字符串给前端）。
     */
    @Schema(description = "主键ID", example = "10001", type = "string")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 编辑业务码。 */
    @Schema(description = "编辑业务码")
    private String editabilityCode;
}
