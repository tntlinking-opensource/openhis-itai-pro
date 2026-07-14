/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ordercancelexecute.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashSet;
import java.util.Set;

/**
 * 主路径 SUMMARIZED 发放触发的汇总重建上下文片段（应用层内部结构）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总重建提示（应用层内部）")
public class SummaryRebuildHint {

    @Schema(description = "原汇总单号")
    private String summaryNo;

    @Schema(description = "需剔除的发放行 id 集合")
    private Set<Long> excludedDispenseIds = new HashSet<>();
}
