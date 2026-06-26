/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.booking.result;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 可约时段摘要（IF-REG-14）。 */
@Getter
@Setter
public class BookableSlotHintSnapshot {

    private Long slotId;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private Integer remainingCount;
}
