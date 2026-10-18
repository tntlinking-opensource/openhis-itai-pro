/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.publicexpense;

import com.zxplat.plugin.campus.application.campusarchive.mapper.CampusArchiveMapper;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.CampusPublicExpenseEligibilityReader;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.service.PublicExpenseEligibilityDomainService;
import com.zxplat.plugin.campus.domain.publicexpenseprofile.view.EligibilityView;
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
