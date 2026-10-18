/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.recheck.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 复查列表请求（IF-PEIS-REC-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchRecheckRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 机构标识。 */
    private Long orgId;

    /** 任务状态 code。 */
    private String statusCode;
}
