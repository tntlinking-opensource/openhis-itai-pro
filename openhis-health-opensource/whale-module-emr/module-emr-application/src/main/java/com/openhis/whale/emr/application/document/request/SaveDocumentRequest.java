/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveDocumentRequest {

    /** 雪花 ID 以字符串传递，避免前端 Number 精度丢失 */
    private String documentId;
    /**
     * 文档正文 JSON 字符串
     */
    private String content;
    /**
     * 是否生成版本快照
     */
    private Boolean saveVersion;
}
