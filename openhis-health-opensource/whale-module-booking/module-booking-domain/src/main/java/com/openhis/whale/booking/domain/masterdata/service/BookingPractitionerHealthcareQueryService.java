/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.masterdata.service;

import java.util.Set;

/**
 * 医师可接诊号源类型查询；实现由 booking-bridge 提供。
 */
public interface BookingPractitionerHealthcareQueryService {

    Set<Long> listHealthcareServiceIdsByPractitioner(Long practitionerId);

    void assertPractitionerAcceptsHealthcareService(Long practitionerId, Long healthcareServiceId);
}
