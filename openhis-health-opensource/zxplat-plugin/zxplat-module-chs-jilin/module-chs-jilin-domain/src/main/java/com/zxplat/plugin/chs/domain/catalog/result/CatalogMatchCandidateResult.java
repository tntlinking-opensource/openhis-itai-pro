/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 带匹配得分的国家目录候选行 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "国家目录匹配候选")
public class CatalogMatchCandidateResult extends NationalCatalogItemResult {

    @Schema(description = "匹配得分 0-100")
    private int matchScore;

    @Schema(description = "命中说明，如「批准文号一致。")
    private String matchReason;
}
