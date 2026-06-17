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

@Getter
@Setter
public class QueryDocumentRequest {

    /** 患者 id（字符串） */
    private String patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private String encounterId;
    /** 文书类型编码 */
    private String documentTypeCode;
    /** 文书状态 code：DRAFT/SUBMITTED/LOCKED/ARCHIVED */
    private String status;

    /** 1-based 页码（与前端 pageNo 对齐） */
    private Integer pageNo;
    /** 页码别名，1-based */
    private Integer pageIndex;
    /** 每页条数 */
    private Integer pageSize;
    /** 关键字（匹配名称/编码等） */
    private String keyword;
}

