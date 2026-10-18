/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ArchiveDocumentRequest {

    /** 文书主键 id */
    private Long documentId;
    /** batchNo（业务字段） */
    private String batchNo;
}
