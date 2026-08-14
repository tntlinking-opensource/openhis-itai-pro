/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qcworkbench.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 高权限解档入参。
 */
@Getter
@Setter
public class UnarchiveCaseRecordRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 解档原因说明。 */
    private String unarchiveReason;
}
