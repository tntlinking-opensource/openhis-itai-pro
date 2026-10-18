/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.response;

import lombok.Data;

/**
 * 团检名册汇总响应。
 */
@Data
public class LoadRosterSummaryResponse {

    /** 名册总人数。 */
    private int totalCount;

    /** 已建单人数（含免收费 ENROLLED 与真实收费 PAID）。 */
    private int enrolledCount;

    /** 未建单人数。 */
    private int unenrolledCount;

    /** 已到检人数。 */
    private int arrivedCount;

    /** 待到检人数（已建单且未到检）。 */
    private int pendingArrivalCount;
}
