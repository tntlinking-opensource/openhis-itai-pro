/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.service;

import com.zxplat.plugin.booking.bridge.masterdata.context.BookingPractitionerHealthcareContextLoader;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingPractitionerHealthcareQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class BookingPractitionerHealthcareQueryServiceImpl implements BookingPractitionerHealthcareQueryService {

    private final BookingPractitionerHealthcareContextLoader practitionerHealthcareServiceSupport;

    @Override
    public Set<Long> listHealthcareServiceIdsByPractitioner(Long practitionerId) {
        return practitionerHealthcareServiceSupport.loadHealthcareServiceIdsByPractitioner(practitionerId);
    }

    @Override
    public void assertPractitionerAcceptsHealthcareService(Long practitionerId, Long healthcareServiceId) {
        practitionerHealthcareServiceSupport.assertPractitionerAcceptsHealthcareService(
            practitionerId, healthcareServiceId);
    }
}
