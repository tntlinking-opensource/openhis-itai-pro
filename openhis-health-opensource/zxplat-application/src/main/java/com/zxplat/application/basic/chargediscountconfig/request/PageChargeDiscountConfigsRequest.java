/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargediscountconfig.request;

import com.zxplat.application.shared.pagination.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询收费折扣配置请求（IF-CDC-02）。
 */
@Getter
@Setter
public class PageChargeDiscountConfigsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /**
     * 产品表名过滤；空表示不限。
     */
    private String productTable;

    /**
     * 状态 code 过滤（ACTIVE/INACTIVE）；空表示不限。
     */
    private String statusEnum;
}
