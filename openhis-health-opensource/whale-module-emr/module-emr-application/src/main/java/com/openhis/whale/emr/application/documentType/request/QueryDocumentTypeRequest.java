/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.documentType.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryDocumentTypeRequest {

    /** 是否启用 */
    private Boolean enabled;
    /** 关键字（匹配名称/编码等） */
    private String keyword;
}
