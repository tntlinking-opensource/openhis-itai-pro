/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 按单位列出团检合同请求。
 */
@Getter
@Setter
public class ListContractsRequest {

    /** 单位主键。 */
    private Long companyId;
}
