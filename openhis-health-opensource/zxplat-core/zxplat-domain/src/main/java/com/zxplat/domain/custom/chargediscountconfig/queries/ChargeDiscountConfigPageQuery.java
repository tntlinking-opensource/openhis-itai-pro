/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 收费折扣配置分页查询条件（DM-CUS-016 §6.2.1）。
 */
@Getter
@Setter
public class ChargeDiscountConfigPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 产品表名过滤（可空不限）。
     */
    private String productTable;

    /**
     * 状态 code 过滤（可空不限）。
     */
    private String statusEnum;

    /**
     * 行形态过滤：{@code CATEGORY} / {@code PRODUCT}（见 {@link com.zxplat.domain.share.constants.ChargeDiscountConfigConstants}）；空不限。
     */
    private String rowShape;

    /**
     * 院区过滤（可空不限）。
     */
    private Long orgId;
}
