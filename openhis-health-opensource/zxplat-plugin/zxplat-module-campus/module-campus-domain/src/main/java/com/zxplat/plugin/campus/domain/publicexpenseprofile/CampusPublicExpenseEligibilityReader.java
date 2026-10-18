/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.publicexpenseprofile;

import com.zxplat.plugin.campus.domain.publicexpenseprofile.view.EligibilityView;

public interface CampusPublicExpenseEligibilityReader {

    EligibilityView check(Long tenantId, Long patientId);
}
