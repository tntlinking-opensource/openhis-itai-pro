/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 病案室退回医生请求。
 */
@Getter
@Setter
public class ReturnToDoctorRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 退回原因。 */
    private String returnReason;
}
