/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检名册模板导入请求。
 */
@Getter
@Setter
public class ImportRosterFromTemplateRequest {

    /** 批次主键。 */
    private Long batchId;

    /** 人员类型：STUDENT / STAFF / ENTERPRISE。 */
    private String memberKind;

    /** xlsx 文件字节。 */
    private byte[] fileBytes;

    /** 导入成功后自动免收费建单。 */
    private Boolean autoEnroll;

    /** 教职工是否同步 Campus（默认 true；仅 STAFF 生效）。 */
    private Boolean syncCampus;
}
