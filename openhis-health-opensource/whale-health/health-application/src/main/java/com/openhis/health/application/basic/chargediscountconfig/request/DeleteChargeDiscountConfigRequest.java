/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargediscountconfig.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 软删收费折扣配置请求（IF-CDC-06）。
 */
@Getter
@Setter
public class DeleteChargeDiscountConfigRequest {

    /**
     * 配置主键（必填）。
     */
    private Long id;
}
