/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 病案室接收请求。
 */
@Getter
@Setter
public class ReceiveHomepageRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
