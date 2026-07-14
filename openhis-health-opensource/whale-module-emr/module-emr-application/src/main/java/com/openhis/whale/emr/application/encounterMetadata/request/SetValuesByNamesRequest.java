/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.encounterMetadata.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SetValuesByNamesRequest {

    /** 文书主键 id */
    private Long documentId;
    /** 数据项 name → 写入 value 的映射 */
    private Map<String, Object> nameToValue;
}
