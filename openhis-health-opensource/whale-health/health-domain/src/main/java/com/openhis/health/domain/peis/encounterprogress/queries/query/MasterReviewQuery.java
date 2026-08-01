/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.peis.encounterprogress.queries.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 总检诊台待总检列表查询（DM-PEIS-008 MAS-01）。
 */
@Getter
@Setter
public class MasterReviewQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;
}
