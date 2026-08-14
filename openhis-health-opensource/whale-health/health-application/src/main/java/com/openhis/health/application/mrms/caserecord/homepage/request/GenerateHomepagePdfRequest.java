/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 生成首页 PDF 请求。
 */
@Getter
@Setter
public class GenerateHomepagePdfRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
