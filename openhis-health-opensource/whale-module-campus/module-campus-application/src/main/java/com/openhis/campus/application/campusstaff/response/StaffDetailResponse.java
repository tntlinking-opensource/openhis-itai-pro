/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstaff.response;

import com.openhis.campus.application.campusarchive.response.PublicExpenseProfileDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StaffDetailResponse {

    private CampusStaffDto staff;
    private PublicExpenseProfileDto peProfile;
}
