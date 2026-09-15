/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.hqms.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * HQMS 住院病案首页 CSV 批量导出请求。
 */
@Getter
@Setter
public class ExportHqmsCsvRequest {

    /**
     * 病案主档主键列表（多选）；单批上限见 {@code HqmsConstants.MAX_EXPORT_CASE_RECORDS}。
     */
    private List<Long> caseRecordIds = new ArrayList<>();
}
