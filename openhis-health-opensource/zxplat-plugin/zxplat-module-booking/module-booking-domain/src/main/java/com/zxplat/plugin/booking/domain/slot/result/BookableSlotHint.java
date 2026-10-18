/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.result;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 可约时段摘要（IF-REG-14）。 */
@Getter
@Setter
public class BookableSlotHint {

    /** 号源主键。 */
    private Long slotId;

    /** 时段开始时刻。 */
    private LocalDateTime slotStart;

    /** 时段结束时刻。 */
    private LocalDateTime slotEnd;

    /** 剩余可约数量。 */
    private Integer remainingCount;
}
