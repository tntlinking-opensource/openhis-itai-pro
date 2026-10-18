/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.encounterprogress.queries.query;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 总检诊台待总检列表查询（DM-PEIS-008 MAS-01）。
 */
@Getter
@Setter
public class MasterReviewQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 团检标志过滤：{@code true} 仅团检；{@code false} 仅个检；{@code null} 不限。 */
    private Boolean groupFlag;
}
