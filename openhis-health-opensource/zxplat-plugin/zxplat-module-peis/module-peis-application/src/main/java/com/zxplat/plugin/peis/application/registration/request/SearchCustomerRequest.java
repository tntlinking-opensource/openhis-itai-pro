/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.request;

import com.zxplat.framework.core.application.paging.PageQueryBinder.AbstractHealthPageRequest;
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
