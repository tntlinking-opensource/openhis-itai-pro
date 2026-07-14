/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.register.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 就诊维度工伤上下文快照。 */
@Data
@Accessors(chain = true)
public class WorkInjuryEncounterContextResult {

    private boolean workInjuryEncounter;
    private boolean qualificationValid;
    private String approvalStatusLabel;
    private Long contractId;
    private boolean needs2301Upload;
}
