/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterprogress.queries.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 分科诊台待检列表查询（DM-PEIS-008 DES-01）。
 */
@Getter
@Setter
public class DeptDeskQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 科室机构主键（可选；非空时过滤 SR.orgId）。 */
    private Long deptOrgId;
}
