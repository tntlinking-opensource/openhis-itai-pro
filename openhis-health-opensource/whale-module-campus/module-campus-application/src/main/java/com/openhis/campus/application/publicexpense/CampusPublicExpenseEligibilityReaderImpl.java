/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.publicexpense;

import com.openhis.campus.application.campusarchive.mapper.CampusArchiveMapper;
import com.openhis.campus.domain.publicexpenseprofile.CampusPublicExpenseEligibilityReader;
import com.openhis.campus.domain.publicexpenseprofile.service.PublicExpenseEligibilityDomainService;
import com.openhis.campus.domain.publicexpenseprofile.view.EligibilityView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CampusPublicExpenseEligibilityReaderImpl implements CampusPublicExpenseEligibilityReader {

    private final PublicExpenseEligibilityDomainService publicExpenseEligibilityDomainService;
    private final CampusArchiveMapper campusArchiveMapper;

    @Override
    public EligibilityView check(Long tenantId, Long patientId) {
        return campusArchiveMapper.toEligibilityView(
            publicExpenseEligibilityDomainService.check(tenantId, patientId));
    }
}
