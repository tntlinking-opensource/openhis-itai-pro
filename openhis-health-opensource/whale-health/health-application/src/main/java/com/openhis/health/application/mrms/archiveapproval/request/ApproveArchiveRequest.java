/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.archiveapproval.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 同意归档请求（IF-MRMS-APR-03）。
 */
@Getter
@Setter
public class ApproveArchiveRequest {

    /** 业务类别。 */
    private String bizCategory;

    /** 文书 id 或 caseRecordId。 */
    private Long bizId;

    /** 可选质量等级（首页）。 */
    private String qualityGradeCode;
}
