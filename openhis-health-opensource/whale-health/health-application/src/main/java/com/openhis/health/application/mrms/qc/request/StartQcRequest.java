/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qc.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 开始质控请求。
 */
@Getter
@Setter
public class StartQcRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
