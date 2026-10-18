/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.support;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 医嘱执行列表读查询（IF-IP-EXE-01 / 打印中心共用 Loader）。
 *
 * <p>{@code planTimeFrom}、开嘱日区间仅由打印中心入口赋值；医嘱执行页入口保持为空。</p>
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

    /**
     * 计划执行日起（含）：按槽位 {@code planTime} 收窄；空表示不下界过滤。
     */
    private LocalDateTime planTimeFrom;

    /**
     * 开嘱日起（含）：按行 {@code reqOpenedTime} 收窄；空表示不下界过滤。
     */
    private LocalDateTime reqOpenedTimeFrom;

    /**
     * 开嘱日止（含）：按行 {@code reqOpenedTime} 收窄；空表示不上界过滤。
     */
    private LocalDateTime reqOpenedTimeTo;

    /** 中西药 Tab：{@code west} / {@code tcm} / {@code all}；空时默认 west。 */
    private String medicineCategoryTab;

    /**
     * 麻醉申请范围：true=仅麻醉申请（方号 AA 前缀）；false/空=排除麻醉申请。
     *
     * <p>与 {@code ListNurseStationOrderExecuteRequest#anesthesiaApplyOnly} 同口径（BR-IP-NUR-004-ANES-001）。</p>
     */
    private Boolean anesthesiaApplyOnly;

    /**
     * 执行记录历史模式：true 时终态槽位按 Procedure 事实行回传（含已被对冲的 COMPLETED）。
     */
    private Boolean executionRecordHistory;

    /**
     * 是否挂载检查/检验打印套餐与部位标本；医嘱执行页 false，打印中心 true。
     */
    private Boolean includePrintDetails;
}
