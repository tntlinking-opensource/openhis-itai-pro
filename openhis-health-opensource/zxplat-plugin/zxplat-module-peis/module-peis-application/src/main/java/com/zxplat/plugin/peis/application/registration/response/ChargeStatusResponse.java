/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.registration.response;

import lombok.Data;

/**
 * 收费态响应（REG-08）。
 */
@Data
public class ChargeStatusResponse {

    /** 是否已收费（存在 SETTLED 明细）。 */
    private boolean paid;

    /** 查询失败时为 true，此时 paid=false。 */
    private boolean unknown;
}
