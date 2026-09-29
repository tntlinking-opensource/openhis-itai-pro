/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargediscountconfig.request;

import com.openhis.health.application.shared.pagination.AbstractHealthPageRequest;
import lombok.Getter;
import lombok.Setter;

/**
 * 检索折扣可选大类/项目请求（IF-CDC-07；searchMode 仅 UI，不落库）。
 */
@Getter
@Setter
public class SearchDiscountCatalogOptionsRequest extends AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /**
     * 产品定义表名（三大目录之一，必填）。
     */
    private String productTable;

    /**
     * 检索模式：{@code CATEGORY}=大类；{@code ITEM}=单项（见 {@code ChargeDiscountConfigConstants}）。
     */
    private String searchMode;
}
