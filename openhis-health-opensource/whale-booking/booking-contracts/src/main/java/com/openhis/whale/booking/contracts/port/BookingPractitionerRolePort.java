package com.openhis.whale.booking.contracts.port;

import com.openhis.whale.booking.contracts.dto.PractitionerRoleScheduleItemDto;

import java.util.List;

public interface BookingPractitionerRolePort {

    List<PractitionerRoleScheduleItemDto> listScheduleItemsByPractitioner(Long practitionerId, Long organizationId);
}
