package com.openhis.whale.booking.contracts.writer;

/** 医技医嘱预约回写（health 侧暂无写能力时由 adapter 占位）。 */
public interface HealthServiceRequestBookingWriter {

    void markServiceRequestBooked(Long serviceRequestId, Long appointmentId);
}
