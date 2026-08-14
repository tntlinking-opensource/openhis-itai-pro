/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 创建模板请求
 */
@Getter
@Setter
public class CreateTemplateRequest {

    /** 名称 */
    private String name;
    /** 业务编码（唯一） */
    private String code;
    /** 文书类型编码 */
    private String documentTypeCode;
    /** PERSONAL | DEPARTMENT | HOSPITAL */
    private String scopeLevel;
    /** 适用科室/机构 id（雪花，字符串传参） */
    private String organizationId;
    /** 个人模板所有者（医师）id（雪花，字符串传参） */
    private String ownerPractitionerId;
    /** AMB | IMP | OBSENC */
    private String encounterClassCode;
    /** 就诊类型枚举（扩展） */
    private Integer encounterTypeEnum;
    /** Tiptap/ProseMirror 正文 JSON */
    private String content;
    /** 是否启用 */
    private Boolean enabled = true;
    /**
     * 医生站「存为模版」：仅允许 PERSONAL / DEPARTMENT；
     * 为 true 时所有者与科室取当前登录会话（忽略请求体中的 id）。
     */
    private Boolean fromDoctorStation;
}
