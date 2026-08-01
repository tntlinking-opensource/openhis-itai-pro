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
}
