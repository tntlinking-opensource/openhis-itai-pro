/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.bed.service;

import com.openhis.whale.booking.domain.bed.command.ReleaseBedReservationCommand;
import com.openhis.whale.booking.domain.bed.command.ReserveBedCommand;

/**
 * 床位预留命令（写 health Location 运营状态）；实现由 booking-bridge 提供。
 */
public interface BookingBedReservationCommandService {

    void reserveBed(ReserveBedCommand command);

    void releaseBedReservation(ReleaseBedReservationCommand command);
}
