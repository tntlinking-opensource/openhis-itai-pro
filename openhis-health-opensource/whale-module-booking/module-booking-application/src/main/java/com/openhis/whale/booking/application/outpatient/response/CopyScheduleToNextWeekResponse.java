/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 按周复制排班响应（2.11）。
 */
@Getter
@Setter
public class CopyScheduleToNextWeekResponse {

    /** 成功复制的 Schedule 条数。 */
    private Integer copiedScheduleCount;

    /** 重新放号的 Slot 个数。 */
    private Integer provisionedSlotCount;

    /** 最后一条新 Schedule 主键（兼容单条复制调试）。 */
    private Long newScheduleId;
}
