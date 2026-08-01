/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.archive.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveArchiveResponse {
    private Long archiveId;
    private Long patientId;
}
