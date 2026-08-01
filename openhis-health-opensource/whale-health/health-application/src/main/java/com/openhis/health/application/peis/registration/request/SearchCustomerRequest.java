/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.registration.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检索体检客户请求（REG-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchCustomerRequest extends AbstractHealthPageRequest {

    /** 身份证号。 */
    private String idCardNo;

    /** 姓名关键字。 */
    private String nameKeyword;
}
