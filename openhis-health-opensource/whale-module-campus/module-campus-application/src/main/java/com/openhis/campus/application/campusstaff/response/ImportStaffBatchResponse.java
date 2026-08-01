/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusstaff.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ImportStaffBatchResponse {

    private int successCount;
    private int skipCount;
    private int failCount;
    private List<String> errors = new ArrayList<>();
    private List<Long> staffIds = new ArrayList<>();
}
