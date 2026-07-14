/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.service;

import com.openhis.whale.booking.domain.masterdata.result.PractitionerRoleScheduleItemDto;

import java.util.List;

/**
 * 医师岗位出诊模板查询；实现由 booking-bridge 提供。
 */
public interface BookingPractitionerRoleQueryService {

    List<PractitionerRoleScheduleItemDto> listScheduleItemsByPractitioner(Long practitionerId, Long organizationId);
}
