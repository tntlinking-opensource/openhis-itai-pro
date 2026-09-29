/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.request;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryDocumentRequest {

    /** 患者 id（字符串） */
    private String patientId;
    /** 就诊 id（字符串，门诊/住院会话） */
    private String encounterId;
    /**
     * 多就诊 id（字符串）；与 {@link #encounterId} 并存时取并集。
     * <p>用于显式传入关联就诊集合；通常由 {@link #includeLinkedEncounters} 服务端展开。</p>
     */
    private List<String> encounterIds;
    /**
     * 为 true 且传入 {@link #encounterId} 时，按门诊/住院 {@code amb_encounter_id} 关联展开为并集再查。
     */
    private Boolean includeLinkedEncounters;
    /** 文书类型编码 */
    private String documentTypeCode;
    /** 文书状态 code：DRAFT/PENDING_ARCHIVE/ARCHIVED */
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
