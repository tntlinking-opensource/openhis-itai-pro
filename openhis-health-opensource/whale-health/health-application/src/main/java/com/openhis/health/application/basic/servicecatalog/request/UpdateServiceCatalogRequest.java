/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 诊疗目录更新请求：在 {@link CreateServiceCatalogRequest} 全部可编辑字段基础上增加主键。
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UpdateServiceCatalogRequest extends CreateServiceCatalogRequest {
    @Schema(description = "主键ID", example = "10001", type = "Long")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 主键ID。 */
    private Long id;

    /** 编辑业务码。 */
    @Schema(description = "编辑业务码")
    private String editabilityCode;
}
