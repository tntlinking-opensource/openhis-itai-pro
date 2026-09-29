/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    /** 文书状态 code：DRAFT/PENDING_ARCHIVE/ARCHIVED */
    private String status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
    /** 创建人 id（雪花，序列化为字符串避免前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    /** 最后更新人 id（雪花，序列化为字符串避免前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;
    /**
     * 创建时使用的模板 ID（溯源；列表挂到模板树下用）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceTemplateId;
    /** 就诊科室名称（列表树展示） */
    private String deptName;
    /** 保存人显示名（优先更新人，否则创建人） */
    private String savedByName;
    /**
     * 主诉摘要（列表展示；从正文 chiefComplaint 数据项轻量提取，无额外查询）
     */
    private String chiefComplaint;
}
