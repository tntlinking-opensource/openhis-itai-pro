/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

import com.openhis.health.domain.share.enums.DispenseStatus;

import java.util.List;

/**
 * 住院护士站医嘱执行（AS-IP-NUR-004）域查询共享常量。
 */
public interface NurseStationExecutionConstants {

    /**
     * 执行前待发预占统计发药状态（**不限就诊**，全局按药房+药品汇总；宽于 IF-DOR-11 目录预减）。
     *
     * <p>含已进入配药/汇总链路的占用；读侧另须 {@code now &lt; limitTime}
     * （与目录预减同窗：草稿 2h、待发 12h）。FIFO 分配与 Gate 编排归属 IF-IP-EXE-02 应用层，
     * 本常量仅约束发药聚合读侧预占<strong>状态</strong>范围。</p>
     */
    List<DispenseStatus> DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE = List.of(
        DispenseStatus.DRAFT,
        DispenseStatus.PREPARATION,
        DispenseStatus.PREPARED,
        DispenseStatus.IN_PROGRESS,
        DispenseStatus.SUMMARIZED);
}
