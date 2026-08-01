/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.publicexpenseprofile.view;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EligibilityView {

    private Boolean passed;
    private String reasonCode;
    private String reasonMessage;
    private String gfStatusEnum;
    private String cardNo;
    private String medicalCode;
    private String campusCode;
}
