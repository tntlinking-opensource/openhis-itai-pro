/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.inpatientemr.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 批量住院文书提交归档（IF-MRMS-IEMR-04，增量）。
 */
@Getter
@Setter
public class SubmitInpatientDocumentArchiveBatchRequest {

    /** 文书主键列表。 */
    private List<Long> documentIds;
}
