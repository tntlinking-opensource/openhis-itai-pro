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

package com.openhis.whale.emr.application.request;

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

