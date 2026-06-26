package com.openhis.whale.booking.contracts.reader;

import com.openhis.whale.booking.contracts.dto.PractitionerRoleScheduleItemDto;

import java.util.List;

public interface HealthPractitionerRoleReader {

    List<PractitionerRoleScheduleItemDto> listScheduleItemsByPractitioner(Long practitionerId, Long organizationId);
}
