package com.openhis.whale.booking.contracts.reader;

import com.openhis.whale.booking.contracts.dto.BookableServiceRequestDto;

import java.util.List;

/** 医技医嘱只读：可预约检验/检查类医嘱及解析。 */
public interface HealthServiceRequestReader {

    List<BookableServiceRequestDto> listBookableServiceRequestsByEncounter(Long encounterId);

    Long resolveActivityIdByServiceRequestId(Long serviceRequestId);

    Long resolveEncounterIdByServiceRequestId(Long serviceRequestId);
}
