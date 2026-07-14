/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.orderexecute.support;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行列表读查询（IF-IP-EXE-01），供 Loader 与 report-bridge 共用。
 */
@Data
@Accessors(chain = true)
public class NurseStationOrderExecuteListQuery {

    /** 待加载执行列表的就诊主键（须落在护士站管辖病区可见范围）。 */
    private List<Long> encounterIds;

    /** 执行 Tab：pending / executed / skipped / cancelled。 */
    private String executeTab;

    /** 长临筛选：all / long / temp；空表示不过滤。 */
    private String therapyType;

    /** 计划执行截止时刻（长期医嘱展开计划点的上界）。 */
    private LocalDateTime executionDeadline;

    /** 中西药 Tab：{@code west} / {@code tcm}；空时默认 west。 */
    private String medicineCategoryTab;
}
