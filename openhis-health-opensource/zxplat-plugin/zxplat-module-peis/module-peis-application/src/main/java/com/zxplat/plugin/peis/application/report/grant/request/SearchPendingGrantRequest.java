/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.grant.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 待发放列表请求（IF-PEIS-REP-05）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchPendingGrantRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 机构标识。 */
    private Long orgId;

    /** 发放状态 code（默认 pending）。 */
    private String grantStatusCode;

    /** 发放日期起。 */
    private LocalDate dateFrom;

    /** 发放日期止。 */
    private LocalDate dateTo;
}
