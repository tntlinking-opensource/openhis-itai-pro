/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * OPEN 待办汇总领域视图。
 */
@Data
public class NoticeTodoSummaryView {
    /** OPEN 且类型 STOP 的数量。 */
    private long stopOpenCount;
    /** OPEN 且类型 PAY_CLASS 的数量。 */
    private long payClassOpenCount;
}
