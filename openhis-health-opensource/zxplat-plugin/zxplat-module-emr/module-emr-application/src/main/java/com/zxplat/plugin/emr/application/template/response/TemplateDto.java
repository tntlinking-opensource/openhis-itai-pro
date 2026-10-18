/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.template.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 电子病历模板 DTO
 */
@Getter
@Setter
public class TemplateDto {

    @JsonSerialize(using = ToStringSerializer.class)
    /** 主键 id */
    private Long id;
    /** 名称 */
    private String name;
    /** 业务编码（唯一） */
    private String code;
    /** 文书类型编码 */
    private String documentTypeCode;
    /** PDF 页眉用文书类型名 */
    private String documentTypeName;
    /** PERSONAL | DEPARTMENT | HOSPITAL */
    private String scopeLevel;
    /** 使用范围展示名 */
    private String scopeLevelName;
    @JsonSerialize(using = ToStringSerializer.class)
    /** 适用科室/机构 id */
    private Long organizationId;
    /** organization 名称 */
    private String organizationName;
    @JsonSerialize(using = ToStringSerializer.class)
    /** 个人模板所有者（医师）id */
    private Long ownerPractitionerId;
    /** AMB | IMP | OBSENC */
    private String encounterClassCode;
    /** 适用就诊类别展示名 */
    private String encounterClassName;
    /** 就诊类别枚举（扩展） */
    private Integer encounterClassEnum;
    /** 就诊类型枚举（扩展） */
    private Integer encounterTypeEnum;
    /** Tiptap/ProseMirror 正文 JSON */
    private String content;
    /** 是否启用 */
    private Boolean enabled;
    /** 与前端 isEnabled 字段对齐 */
    private Boolean isEnabled;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
    @JsonSerialize(using = ToStringSerializer.class)
    /** 创建人 id */
    private Long createdBy;
    @JsonSerialize(using = ToStringSerializer.class)
    /** 最后更新人 id */
    private Long updatedBy;
}
