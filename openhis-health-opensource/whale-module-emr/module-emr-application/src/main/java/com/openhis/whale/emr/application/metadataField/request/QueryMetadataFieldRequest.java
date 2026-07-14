/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.metadataField.request;

import com.openhis.whale.core.application.dto.BaseQueryRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryMetadataFieldRequest extends BaseQueryRequest {

    /** 关键字（匹配名称/编码等） */
    private String keyword;
    /** 取值来源枚举 code（CONST/SYSTEM/PATIENT 等） */
    private String valueSource;
    /** 是否启用 */
    private Boolean isEnabled;
}
