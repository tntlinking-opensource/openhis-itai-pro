/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.mrms.caserecord.vo;

import lombok.Getter;
import lombok.Setter;

/**
 * 护理天数汇总快照（自动取值中间结构）。
 *
 * <p>由护理级别医嘱（category=26）按级别累计日历天，对齐首页
 * {@code spga/lv1/scd/lv3_nurscare_days}。</p>
 */
@Getter
@Setter
public class NursingCareDaysSnapshot {

    /** 特级护理天数。 */
    private Integer spgaNurscareDays;

    /** 一级护理天数。 */
    private Integer lv1NurscareDays;

    /** 二级护理天数。 */
    private Integer scdNurscareDays;

    /** 三级护理天数。 */
    private Integer lv3NurscareDays;
}
