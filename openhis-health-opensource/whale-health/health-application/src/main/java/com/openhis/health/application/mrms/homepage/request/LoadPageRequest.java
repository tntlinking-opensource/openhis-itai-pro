/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.homepage.request;

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
