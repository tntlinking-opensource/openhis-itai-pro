/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.list.result;

import lombok.Data;

/** 博思 getEBillAccountStatus 解析结果。 */
@Data
public class BossAccountStatusResult {

    private boolean success;
    private String resultCode;
    private String rawMessage;
    private String accountStatus;
    private String accountDate;
}
