/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import com.openhis.health.application.peis.group.dto.RosterImportRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 导入团检名册请求（GRP-04）。
 */
@Getter
@Setter
public class ImportRosterRequest {

    /** 批次主键。 */
    private Long batchId;

    /** 名册行列表。 */
    private List<RosterImportRowDto> rows;

    /** 导入成功后自动免收费建单（默认 true；须批次已绑套餐）。 */
    private Boolean autoEnroll;

    /** 人员类型：STUDENT / STAFF / ENTERPRISE。 */
    private String memberKind;

    /** 教职工是否同步 Campus（默认 true；仅 STAFF 生效）。 */
    private Boolean syncCampus;
}
