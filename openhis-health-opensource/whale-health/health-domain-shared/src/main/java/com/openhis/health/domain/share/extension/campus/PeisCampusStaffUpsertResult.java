/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.campus;

import lombok.Getter;
import lombok.Setter;

/**
 * Campus 教职工 upsert 结果。
 */
@Getter
@Setter
public class PeisCampusStaffUpsertResult {

    private Long staffSubjectId;
    private boolean created;
    private Long patientId;
}
