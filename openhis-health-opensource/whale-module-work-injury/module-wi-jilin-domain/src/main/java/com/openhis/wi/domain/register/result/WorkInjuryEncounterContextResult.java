/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.domain.register.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 就诊维度工伤上下文快照（备案详情 / 角标）。 */
@Data
@Accessors(chain = true)
public class WorkInjuryEncounterContextResult {

    private boolean workInjuryEncounter;
    private boolean qualificationValid;
    private String approvalStatusLabel;
    private Long contractId;
    private boolean needs2301Upload;

    /** 1101 人员编号。 */
    private String psnNo;
    /** 1101 认定 ID。 */
    private String injureAffirmId;
    /** 9102/2301 审批资格 ID。 */
    private String qualificationId;
    /** NONE / VALID / PENDING_9102。 */
    private String qualificationStatus;
    /** 医疗类别。 */
    private String medType;
}
