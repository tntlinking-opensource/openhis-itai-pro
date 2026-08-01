/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterprogress.queries.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 可打印报告列表查询（DM-PEIS-008 REP-01）。
 */
@Getter
@Setter
public class ReportPrintQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 机构/院区标识（可选过滤）。 */
    private Long orgId;
}
