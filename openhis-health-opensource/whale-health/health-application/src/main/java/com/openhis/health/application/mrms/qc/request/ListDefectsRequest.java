/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.qc.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 缺陷列表查询请求。
 */
@Getter
@Setter
public class ListDefectsRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 缺陷状态码过滤。 */
    private String statusCode;

    /** 严重级别码过滤。 */
    private String severityCode;
}
