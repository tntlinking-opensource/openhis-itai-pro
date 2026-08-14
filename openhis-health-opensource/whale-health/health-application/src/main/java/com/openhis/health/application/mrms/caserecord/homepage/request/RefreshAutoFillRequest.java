/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 首页自动取值刷新请求。
 */
@Getter
@Setter
public class RefreshAutoFillRequest {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 刷新段：basic/inpatient/diagnosis/procedure/staff/all。 */
    private List<String> sections = new ArrayList<>();

    /** 是否强制覆盖 auto/mixed 字段。 */
    private boolean forceOverwrite;
}
