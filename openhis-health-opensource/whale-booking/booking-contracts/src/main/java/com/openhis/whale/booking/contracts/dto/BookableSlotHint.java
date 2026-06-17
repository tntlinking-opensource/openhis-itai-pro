package com.openhis.whale.booking.contracts.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 可约时段摘要（IF-REG-14）。 */
@Getter
@Setter
public class BookableSlotHint {

    private Long slotId;
    private LocalDateTime slotStart;
    private LocalDateTime slotEnd;
    private Integer remainingCount;
}
