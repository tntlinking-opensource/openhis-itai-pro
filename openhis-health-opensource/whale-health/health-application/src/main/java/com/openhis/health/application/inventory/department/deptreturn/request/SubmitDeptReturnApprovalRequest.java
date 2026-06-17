/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.department.deptreturn.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SubmitDeptReturnApprovalRequest {

    /** 业务单号。 */
    private String busNo;
    /** applyClock 字段。 */
    private LocalDateTime applyClock;
}
