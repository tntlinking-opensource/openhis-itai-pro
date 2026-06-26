/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.qc.request;

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
