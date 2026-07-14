/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.template.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 复制模板请求（新名称、新编码，其余可继承）
 */
@Getter
@Setter
public class CopyTemplateRequest {

    /** 创建文书时使用的源模板 id */
    private String sourceTemplateId;
    /** 副本新名称 */
    private String newName;
    /** 副本新编码 */
    private String newCode;
}
