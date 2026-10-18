/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.request;

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
