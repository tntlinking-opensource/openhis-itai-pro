/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 加载首页编辑页请求。
 */
@Getter
@Setter
public class LoadPageRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
