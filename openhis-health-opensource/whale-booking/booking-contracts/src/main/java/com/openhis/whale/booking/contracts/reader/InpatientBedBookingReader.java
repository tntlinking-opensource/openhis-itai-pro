package com.openhis.whale.booking.contracts.reader;

import com.openhis.whale.booking.contracts.dto.InpatientBedReservationSnapshot;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 住院床位预约读 Port（供 health 护士站分床联动）。
 */
public interface InpatientBedBookingReader {

    /**
     * 按床位与拟入院日查有效预约。
     *
     * @param bedLocationId 床位主键
     * @param intendedDate  拟入院日
     * @return 快照；无则 empty
     */
    Optional<InpatientBedReservationSnapshot> findActiveByBedAndDate(Long bedLocationId, LocalDate intendedDate);

    /**
     * 按床位查当前有效预约（取最近一条扩展记录）。
     *
     * @param bedLocationId 床位主键
     * @return 快照；无则 empty
     */
    Optional<InpatientBedReservationSnapshot> findActiveByBed(Long bedLocationId);
}
