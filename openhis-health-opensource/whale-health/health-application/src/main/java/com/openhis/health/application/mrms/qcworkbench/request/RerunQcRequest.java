/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qcworkbench.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 重新执行质控请求。
 */
@Getter
@Setter
public class RerunQcRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
