/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.archive.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckArchiveCompletenessRequest {
    private Long patientId;
    private String studentNo;
}
