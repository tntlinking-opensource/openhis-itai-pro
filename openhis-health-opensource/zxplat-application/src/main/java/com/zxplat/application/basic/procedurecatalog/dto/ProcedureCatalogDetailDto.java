/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 手术目录详情出参：与 {@link ProcedureCatalogDto} 字段一致，语义上表示详情接口完整载荷。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "手术操作目录详情")
public class ProcedureCatalogDetailDto extends ProcedureCatalogDto {
}
