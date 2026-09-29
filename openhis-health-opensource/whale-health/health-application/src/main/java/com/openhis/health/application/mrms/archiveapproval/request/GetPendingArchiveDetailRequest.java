/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.archiveapproval.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 待审详情请求（IF-MRMS-APR-02）。
 */
@Getter
@Setter
public class GetPendingArchiveDetailRequest {

    /** 业务类别。 */
    private String bizCategory;

    /**
     * 业务主键：文书 id 或病案 caseRecordId。
     */
    private Long bizId;
}
