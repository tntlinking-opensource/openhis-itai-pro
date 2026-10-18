/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.caserecord.homepage.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 提交病案首页归档审批请求（IF-MRMS-HP-ARC-03，增量方法）。
 *
 * <p>与既有 {@link SubmitHomepageRequest} 并列；不修改旧 submit/receive/returnToDoctor。</p>
 */
@Getter
@Setter
public class SubmitHomepageArchiveRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;
}
