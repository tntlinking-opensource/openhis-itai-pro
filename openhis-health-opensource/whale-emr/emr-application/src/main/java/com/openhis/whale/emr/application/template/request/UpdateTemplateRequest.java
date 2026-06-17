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

package com.openhis.whale.emr.application.template.request;

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
    /** 适用科室/机构 id */
    private Long organizationId;
    /** 个人模板所有者（医师）id */
    private Long ownerPractitionerId;
    /** 适用就诊类别 code：AMB/IMP/OBSENC */
    private String encounterClassCode;
    /** 就诊类型枚举（扩展） */
    private Integer encounterTypeEnum;
    /** Tiptap/ProseMirror 正文 JSON */
    private String content;
    /** 是否启用 */
    private Boolean enabled;
}
