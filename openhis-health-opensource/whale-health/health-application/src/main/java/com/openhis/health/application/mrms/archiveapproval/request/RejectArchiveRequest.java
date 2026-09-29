/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.archiveapproval.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 驳回归档请求（IF-MRMS-APR-04）。
 */
@Getter
@Setter
public class RejectArchiveRequest {

    /** 业务类别。 */
    private String bizCategory;

    /** 文书 id 或 caseRecordId。 */
    private Long bizId;

    /** 驳回原因（必填）。 */
    private String rejectReason;
}
