/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 药品医保目录推荐结果（须人工确认后写yb_no，不自动落库） */
@Data
@Accessors(chain = true)
@Schema(description = "药品医保目录推荐结果")
public class MedicationCatalogMappingSuggestResult {

    /**
     * 推荐强度：UNIQUE_SUGGEST=可默认选中但仍需确认；MULTI_CHOICE=多条需人选；NO_MATCH=无候选 */
    @Schema(description = "UNIQUE_SUGGEST | MULTI_CHOICE | NO_MATCH")
    private String recommendLevel;

    @Schema(description = "说明文案")
    private String hint;

    @Schema(description = "候选列表（matchScore 降序）")
    private List<CatalogMatchCandidateResult> candidates;
}
