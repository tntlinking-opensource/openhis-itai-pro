/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.borrow.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 借阅申请入参。
 */
@Getter
@Setter
public class ApplyBorrowRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 借阅类型码（查阅/复印等）。 */
    private String requestTypeCode;

    /** 申请人姓名。 */
    private String applicantName;

    /** 申请人科室名称。 */
    private String applicantDeptName;

    /** 借阅目的说明。 */
    private String purpose;

    /** 借阅范围描述。 */
    private String scopeDesc;

    /** 复印份数。 */
    private Integer copyCount;

    /** 患者授权信息摘要。 */
    private String patientAuthorizationInfo;
}
