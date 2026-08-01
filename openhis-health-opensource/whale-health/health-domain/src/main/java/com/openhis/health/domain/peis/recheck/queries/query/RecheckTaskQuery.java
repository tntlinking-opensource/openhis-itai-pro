/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.recheck.queries.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 复查任务列表查询（AS-PEIS-REC-001）。
 */
@Getter
@Setter
public class RecheckTaskQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 机构标识。 */
    private Long orgId;

    /** 任务状态 code（可选）。 */
    private String statusCode;
}
