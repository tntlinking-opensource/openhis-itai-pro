package com.openhis.whale.booking.contracts.query;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊检索条件（医技预约）。
 */
@Getter
@Setter
public class SearchEncounterQuery {

    private Long organizationId;
    private String keyword;
    private String encounterBusNo;
    private Long patientId;
}
