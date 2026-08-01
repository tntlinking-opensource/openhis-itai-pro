/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import lombok.Data;

/**
 * 催办待办 OPEN 汇总（R-04 summary）。
 */
@Data
public class NoticeTodoSummaryResponse {
    /** OPEN 状态且类型为 STOP 的待办数。 */
    private long stopOpenCount;
    /** OPEN 状态且类型为 PAY_CLASS 的待办数。 */
    private long payClassOpenCount;
}
