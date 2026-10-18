/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.groupcharge.request;

import com.zxplat.plugin.peis.application.group.request.PageGroupRosterRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检收费名册分页请求（继承 GRP 名册分页字段）。
 */
@Getter
@Setter
public class PageGroupChargeRosterRequest extends PageGroupRosterRequest {

    private static final long serialVersionUID = 1L;
}
