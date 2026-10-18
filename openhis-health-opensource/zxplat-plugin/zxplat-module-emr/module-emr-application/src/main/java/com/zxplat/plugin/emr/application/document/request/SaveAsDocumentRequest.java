/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveAsDocumentRequest {

    /** sourceDocument 主键 id */
    private Long sourceDocumentId;
    /** 患者 id（字符串） */
    private String patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private String encounterId;
    /** 文书类型编码 */
    private String documentTypeCode;
}
