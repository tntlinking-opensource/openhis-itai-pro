/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.metadataField.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckMetadataFieldReferenceRequest {

    /** 元数据字段键（模板引用标识，字母开头） */
    private String fieldKey;
}
