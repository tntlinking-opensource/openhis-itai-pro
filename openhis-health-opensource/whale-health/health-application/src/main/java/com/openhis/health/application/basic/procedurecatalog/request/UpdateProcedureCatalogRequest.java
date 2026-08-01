/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新手术目录请求：在 {@link CreateProcedureCatalogRequest} 字段基础上携带主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新手术目录请求")
public class UpdateProcedureCatalogRequest extends CreateProcedureCatalogRequest {

    @Schema(description = "主键ID")
    private Long id;
}
