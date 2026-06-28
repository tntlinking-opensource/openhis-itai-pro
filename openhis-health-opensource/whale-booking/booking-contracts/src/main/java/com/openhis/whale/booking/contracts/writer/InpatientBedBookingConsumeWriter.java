package com.openhis.whale.booking.contracts.writer;

/**
 * 住院分床消费床位预约写 Port（关闭预约扩展，不释 Location；占床已由 health 完成）。
 */
public interface InpatientBedBookingConsumeWriter {

    /**
     * 分床成功后关闭床位预约记录。
     *
     * @param appointmentId 预约主键
     * @param operatorId    操作人
     */
    void consumeReservationForBedAssign(Long appointmentId, Long operatorId);
}
