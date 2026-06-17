/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RejectReturnUpstreamRequest {

    /** 业务单号。 */
    private String busNo;
    /** 审批人主键。 */
    private Long approverId;
    /** approvalClock 字段。 */
    private LocalDateTime approvalClock;
    /** 驳回原因。 */
    private String rejectReason;
}
