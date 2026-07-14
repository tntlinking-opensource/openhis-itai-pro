/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.diagnosiscatalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 疾病目录详情出参：与 {@link DiagnosisCatalogDto} 字段一致，语义上表示详情接口完整载荷。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "疾病（病种）目录详情")
public class DiagnosisCatalogDetailDto extends DiagnosisCatalogDto {
}
