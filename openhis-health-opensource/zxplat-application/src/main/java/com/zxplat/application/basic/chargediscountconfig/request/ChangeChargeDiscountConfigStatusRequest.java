/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargediscountconfig.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 启停收费折扣配置请求（IF-CDC-05）。
 */
@Getter
@Setter
public class ChangeChargeDiscountConfigStatusRequest {

    /**
     * 配置主键（必填）。
     */
    private Long id;

    /**
     * 目标状态 code（ACTIVE / INACTIVE）。
     */
    private String statusEnum;
}
