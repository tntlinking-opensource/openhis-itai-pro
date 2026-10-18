/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import com.zxplat.plugin.peis.application.group.dto.RosterImportRowDto;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 按合同导入团检名册请求（GRP-04 · PEIS-GRP-TREE-PKG-001）。
 */
@Getter
@Setter
public class ImportRosterByContractRequest {

    /** 合同主键。 */
    private Long contractId;

    /** 默认体检分组主键。 */
    private Long defaultBatchId;

    /** 导入页选中的单位树节点（名册 company_id）。 */
    private Long selectedCompanyId;

    /** 名册行列表。 */
    private List<RosterImportRowDto> rows;

    /** 人员类型：STUDENT / STAFF / ENTERPRISE。 */
    private String memberKind;

    /** 教职工是否同步 Campus（默认 true；仅 STAFF 生效）。 */
    private Boolean syncCampus;
}
