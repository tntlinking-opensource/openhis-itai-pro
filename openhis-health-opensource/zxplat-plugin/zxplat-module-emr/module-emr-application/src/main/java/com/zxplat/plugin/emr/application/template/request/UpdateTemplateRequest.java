/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 更新模板请求
 */
@Getter
@Setter
public class UpdateTemplateRequest {

    /** 雪花 ID 以字符串传递 */
    private String id;
    /** 名称 */
    private String name;
    /** 业务编码（唯一） */
    private String code;
    /** 文书类型编码 */
    private String documentTypeCode;
    /** 使用范围 code：PERSONAL/DEPARTMENT/HOSPITAL */
    private String scopeLevel;
    /** 适用科室/机构 id（雪花，字符串传参） */
    private String organizationId;
    /** 个人模板所有者（医师）id（雪花，字符串传参） */
    private String ownerPractitionerId;
    /** 适用就诊类别 code：AMB/IMP/OBSENC */
    private String encounterClassCode;
    /** 就诊类型枚举（扩展） */
    private Integer encounterTypeEnum;
    /** Tiptap/ProseMirror 正文 JSON */
    private String content;
    /** 是否启用 */
    private Boolean enabled;
}
