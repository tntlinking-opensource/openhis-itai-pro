/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.upload.weistat.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 卫统 N041 CSV 批量导出请求。
 */
@Getter
@Setter
public class ExportN041CsvRequest {

    /**
     * 病案主档主键列表（多选）；单批上限见 {@code WeistatN041Constants.MAX_EXPORT_CASE_RECORDS}。
     */
    private List<Long> caseRecordIds = new ArrayList<>();
}
