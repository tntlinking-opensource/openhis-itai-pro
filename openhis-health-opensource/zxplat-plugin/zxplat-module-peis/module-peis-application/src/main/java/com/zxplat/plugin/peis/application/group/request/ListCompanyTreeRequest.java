/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 查询团检单位树请求。
 */
@Getter
@Setter
public class ListCompanyTreeRequest {

    /** 体检中心机构主键（可选）。 */
    private Long orgId;
}
