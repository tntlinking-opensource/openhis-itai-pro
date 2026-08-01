/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.publicexpenseprofile;

import com.openhis.campus.domain.publicexpenseprofile.view.EligibilityView;

public interface CampusPublicExpenseEligibilityReader {

    EligibilityView check(Long tenantId, Long patientId);
}
