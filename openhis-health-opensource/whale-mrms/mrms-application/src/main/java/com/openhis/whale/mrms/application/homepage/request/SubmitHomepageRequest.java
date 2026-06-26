/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交病案首页请求。
 */
@Getter
@Setter
public class SubmitHomepageRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
