/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GetStructureRequest {

    /** 文书主键 id */
    private Long documentId;
    /** Tiptap/ProseMirror 正文 JSON */
    private String content;
    private List<String> nodeTypes;     // region / section / dataElement
    /** 是否只解析第一层结构节点 */
    private Boolean firstLevelOnly;
    /** includeBlockPageNo（业务字段） */
    private Boolean includeBlockPageNo;
}
