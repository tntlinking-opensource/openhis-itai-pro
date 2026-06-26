/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.lookup.request;

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
