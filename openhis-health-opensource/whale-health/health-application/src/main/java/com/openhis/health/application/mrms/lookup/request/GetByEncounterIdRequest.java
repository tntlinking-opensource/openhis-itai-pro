/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.lookup.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按就诊查病案请求。
 */
@Getter
@Setter
public class GetByEncounterIdRequest {

    /** 就诊主键。 */
    private Long encounterId;
}
