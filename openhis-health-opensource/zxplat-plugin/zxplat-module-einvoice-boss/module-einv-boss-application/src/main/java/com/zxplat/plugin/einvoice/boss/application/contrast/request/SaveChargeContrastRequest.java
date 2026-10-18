/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.contrast.request;

import lombok.Data;

/**
 * 保存收费对照请求。
 */
@Data
public class SaveChargeContrastRequest {

    private Long id;
    private Long orgId;
    private String internalCode;
    private String bossChargeCode;
    private String bossChargeName;
}
