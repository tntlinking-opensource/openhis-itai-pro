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

package com.openhis.whale.emr.application.document.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class DocumentSummaryDto {

    @JsonSerialize(using = ToStringSerializer.class)
    /** 主键 id */
    private Long id;
    /** 患者 id（字符串） */
    private String patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private String encounterId;
    /** 文书类型编码 */
    private String documentTypeCode;
    /** PDF 页眉用文书类型名 */
    private String documentTypeName;
    /** 文书标题 */
    private String title;
    /** 文书状态 code：DRAFT/SUBMITTED/LOCKED/ARCHIVED */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
    /** 创建人 id */
    private Long createdBy;
    /** 最后更新人 id */
    private Long updatedBy;
}

