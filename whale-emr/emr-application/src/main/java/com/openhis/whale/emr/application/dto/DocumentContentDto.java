/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentContentDto {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String patientId;
    private String encounterId;
    private String documentTypeCode;
    private String title;
    /**
     * 文书状态：DRAFT / SUBMITTED / LOCKED / ARCHIVED 等
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

