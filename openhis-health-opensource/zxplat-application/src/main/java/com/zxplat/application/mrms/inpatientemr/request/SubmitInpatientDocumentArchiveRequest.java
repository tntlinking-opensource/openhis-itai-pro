/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.inpatientemr.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 单份住院文书提交归档（IF-MRMS-IEMR-03，增量）。
 */
@Getter
@Setter
public class SubmitInpatientDocumentArchiveRequest {

    /** 文书主键。 */
    private Long documentId;
}
