/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusgfapproval.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalActionResponse {
    private Long requestId;
    private String statusEnum;
}
