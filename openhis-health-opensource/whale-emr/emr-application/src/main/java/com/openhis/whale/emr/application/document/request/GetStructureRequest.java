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

package com.openhis.whale.emr.application.document.request;

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

