/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ordercancelexecute.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 按 summaryNo 聚合剔除行与 Supply 态快照的汇总过滤重建上下文（应用层内部结构）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "汇总过滤重建上下文（应用层内部）")
public class CancelExecutionSummaryRebuildContext {

    /** 按汇总单号聚合的剔除发放行 id。 */
    @Schema(description = "按汇总单号聚合的剔除发放行 id")
    private Map<String, Set<Long>> excludedDispenseIdsBySummaryNo = new HashMap<>();

    /** 撤单前记录的 Supply 状态快照，用于并发改态 Gate。 */
    @Schema(description = "按汇总单号记录的 Supply 状态快照")
    private Map<String, String> supplyStatusSnapshotBySummaryNo = new HashMap<>();

    /**
     * 登记一条 SUMMARIZED 发放行进入剔除集。
     *
     * @param summaryNo  汇总单号
     * @param dispenseId 发放行主键
     */
    public void registerExcludedDispense(String summaryNo, Long dispenseId) {
        if (summaryNo == null || dispenseId == null) {
            return;
        }
        Set<Long> bucket = this.excludedDispenseIdsBySummaryNo.get(summaryNo);
        if (bucket == null) {
            bucket = new HashSet<>();
            this.excludedDispenseIdsBySummaryNo.put(summaryNo, bucket);
        }
        bucket.add(dispenseId);
    }
}
