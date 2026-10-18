/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 更新疾病目录请求：在 {@link CreateDiagnosisCatalogRequest} 字段基础上携带主键。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新疾病目录请求")
public class UpdateDiagnosisCatalogRequest extends CreateDiagnosisCatalogRequest {

    @Schema(description = "主键ID")
    /** 主键ID。 */
    private Long id;
}
