package com.openhis.whale.booking.contracts.port;

import com.openhis.whale.booking.contracts.command.ReleaseBedReservationCommand;
import com.openhis.whale.booking.contracts.command.ReserveBedCommand;

/**
 * 床位预留写 Port（写 health Location 运营状态）。
 */
public interface BookingBedPort {

    /**
     * 预留床位。
     */
    void reserveBed(ReserveBedCommand command);

    /**
     * 取消床位预留。
     */
    void releaseBedReservation(ReleaseBedReservationCommand command);
}
