/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.reportgrantext.queries.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 待发放列表查询（DM-PEIS-005 REP-05）。
 */
@Getter
@Setter
public class GrantSearchQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 机构标识。 */
    private Long orgId;

    /** 发放状态 code（默认 pending）。 */
    private String grantStatusCode;
}
