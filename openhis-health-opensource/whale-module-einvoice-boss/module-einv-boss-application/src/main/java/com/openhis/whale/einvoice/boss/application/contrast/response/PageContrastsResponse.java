/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.contrast.response;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 收费对照分页响应。
 */
@Data
public class PageContrastsResponse {

    private List<ChargeContrastView> rows = new ArrayList<>();
    private long total;
}
