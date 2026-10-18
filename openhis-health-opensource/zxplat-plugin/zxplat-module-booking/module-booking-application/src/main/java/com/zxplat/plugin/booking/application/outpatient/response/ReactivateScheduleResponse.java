/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 恢复开诊响应。
 */
@Getter
@Setter
public class ReactivateScheduleResponse {

    /** 已恢复日程主键。 */
    private Long scheduleId;

    /** 提示：恢复后须手动重新放号。 */
    private Boolean needReprovision;
}
