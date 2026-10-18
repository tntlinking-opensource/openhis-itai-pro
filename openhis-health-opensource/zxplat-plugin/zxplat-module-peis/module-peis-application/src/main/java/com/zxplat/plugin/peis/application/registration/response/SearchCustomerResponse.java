/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.response;

import com.zxplat.plugin.peis.application.registration.dto.CustomerRowDto;
import lombok.Data;

import java.util.List;

/**
 * 检索体检客户响应（REG-01）。
 */
@Data
public class SearchCustomerResponse {

    /** 客户行。 */
    private List<CustomerRowDto> rows;

    /** 总条数。 */
    private Long total;
}
