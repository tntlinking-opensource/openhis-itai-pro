/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstudent.response;

import com.openhis.campus.application.campusarchive.response.PublicExpenseProfileDto;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudentDetailResponse {

    private CampusStudentDto student;
    private PublicExpenseProfileDto peProfile;
}
