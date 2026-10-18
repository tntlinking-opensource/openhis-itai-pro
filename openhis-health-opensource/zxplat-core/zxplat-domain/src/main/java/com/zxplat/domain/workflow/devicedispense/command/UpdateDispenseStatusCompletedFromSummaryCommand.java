/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicedispense.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 汇总完成回写发放单命令。
 *
 * <p>约定：{@code summaryKeyOrIdList} 为 {@code batchKey|id1,id2,...}；若无 {@code |} 则整串解析为 id 列表，批次键为空。</p>
 */
@Getter
@Setter
public class UpdateDispenseStatusCompletedFromSummaryCommand {

    /** 汇总key或标识列表。 */
    private String summaryKeyOrIdList;

    /** 操作人主键。 */
    private Long operatorId;
}
