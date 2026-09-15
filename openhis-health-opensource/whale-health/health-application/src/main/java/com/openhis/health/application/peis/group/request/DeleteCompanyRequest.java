/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.group.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除团检单位请求。
 */
@Getter
@Setter
public class DeleteCompanyRequest {

    /** 单位主键。 */
    private Long companyId;
}
