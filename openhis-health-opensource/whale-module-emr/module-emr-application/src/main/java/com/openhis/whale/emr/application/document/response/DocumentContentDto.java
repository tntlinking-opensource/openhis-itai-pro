/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentContentDto {

    /** 主键 id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    /** 患者 id（字符串） */
    private String patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private String encounterId;
    /** 文书类型编码 */
    private String documentTypeCode;
    /** 文书标题 */
    private String title;
    /**
     * 文书状态：DRAFT / PENDING_ARCHIVE / ARCHIVED
     */
    private String status;
    /**
     * 文档正文 JSON（Tiptap/ProseMirror）
     */
    private String content;
    /**
     * 是否只读（锁定、归档或权限不足）
     */
    private boolean readOnly;

    /**
     * 当前就诊上下文解析的元数据 fieldKey → value（已脱敏）
     */
    private java.util.Map<String, String> metadataValues;
}
