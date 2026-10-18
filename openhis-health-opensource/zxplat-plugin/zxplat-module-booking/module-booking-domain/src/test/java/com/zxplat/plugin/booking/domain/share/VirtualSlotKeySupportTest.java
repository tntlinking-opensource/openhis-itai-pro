/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.share;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualSlotKeySupportTest {

    @Test
    void encodeAndDecode_roundTrip() {
        LocalDate date = LocalDate.of(2026, 6, 20);
        LocalDateTime start = date.atTime(8, 0);
        LocalDateTime end = date.atTime(9, 0);

        String key = VirtualSlotKeySupport.encode(100L, date, 501L, start, end);
        VirtualSlotKeySupport.VirtualSlotKeyParts parsed = VirtualSlotKeySupport.decode(key);

        assertThat(parsed.getOrganizationId()).isEqualTo(100L);
        assertThat(parsed.getDayScheduleTemplateId()).isEqualTo(501L);
        assertThat(parsed.getDate()).isEqualTo(date);
        assertThat(parsed.getStartTime()).isEqualTo(start);
        assertThat(parsed.getEndTime()).isEqualTo(end);
    }
}
