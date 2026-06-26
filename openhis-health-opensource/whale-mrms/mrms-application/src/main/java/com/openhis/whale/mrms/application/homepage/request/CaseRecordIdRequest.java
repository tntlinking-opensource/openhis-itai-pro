/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 通用病案主键请求。
 */
@Getter
@Setter
public class CaseRecordIdRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
