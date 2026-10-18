/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 修改已写文书显示标题（{@code emr_document.title}），不改正文。
 */
@Getter
@Setter
public class UpdateDocumentTitleRequest {

    /**
     * 文书主键；雪花 ID 以字符串传递，避免前端 Number 精度丢失。
     */
    private String documentId;

    /**
     * 新标题；去首尾空白后非空，最长 256。
     */
    private String title;
}
