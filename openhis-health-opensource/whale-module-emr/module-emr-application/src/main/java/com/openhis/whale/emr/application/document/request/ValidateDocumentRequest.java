/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidateDocumentRequest {

    /** 文书主键 id */
    private Long documentId;
    /** Tiptap/ProseMirror 正文 JSON */
    private String content;
}
