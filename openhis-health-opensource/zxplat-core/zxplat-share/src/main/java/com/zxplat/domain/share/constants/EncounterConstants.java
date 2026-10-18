/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.constants;


/**
 * EncounterConstants：health-domain-shared 跨领域共享常量。
 */
public interface EncounterConstants {

    /**
     * 接诊医生角色编码。
     */
    String ROLE_ADMITTER = "ADMITTER";

    /**
     * 分诊护士角色编码。
     */
    String ROLE_TRIAGE_NURSE = "TRIAGE_NURSE";

    /**
     * 门诊同日同科室防重失败提示（读/写侧与 IF-REG-06 {@code REG-BIZ-409} 对齐）。
     */
    String OUTPATIENT_DUPLICATE_REGISTRATION_MESSAGE =
        "本科室当日已有门诊挂号记录（含待支付/诊毕），须撤销待挂号或退号后方可再挂";
}
