/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inventory.pharmacy.returnupstream.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class SubmitReturnUpstreamApprovalRequest {

    /** 业务单号。 */
    private String busNo;
    /** applyClock 字段。 */
    private LocalDateTime applyClock;
}
