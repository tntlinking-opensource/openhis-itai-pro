/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.constants;

/**
 * PEIS 复查任务状态 code（peis_recheck_task.status_enum，Peis 04 §4.5 方案 A 本地 Task 语义）。
 */
public final class PeisRecheckTaskStatusConstants {

    private PeisRecheckTaskStatusConstants() {
    }

    /** 已申请，待确认复查项目。 */
    public static final String REQUESTED = "requested";

    /** 复查项目已确认，执行中。 */
    public static final String IN_PROGRESS = "in-progress";

    /** 复查已完成。 */
    public static final String COMPLETED = "completed";
}
