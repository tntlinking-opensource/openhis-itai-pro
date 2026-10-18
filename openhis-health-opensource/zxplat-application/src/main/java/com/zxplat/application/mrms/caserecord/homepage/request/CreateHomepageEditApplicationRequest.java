/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建首页编辑申请请求（IF-MRMS-HP-ARC-04，增量方法）。
 */
@Getter
@Setter
public class CreateHomepageEditApplicationRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 申请原因。 */
    private String applyReason;
}
