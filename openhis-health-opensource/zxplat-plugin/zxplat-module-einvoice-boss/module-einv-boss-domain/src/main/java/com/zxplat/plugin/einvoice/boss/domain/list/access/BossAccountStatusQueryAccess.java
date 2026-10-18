/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.list.access;

import com.zxplat.plugin.einvoice.boss.domain.list.result.BossAccountStatusResult;

/** 入账状态查询出站端口（bridge 实现）。 */
public interface BossAccountStatusQueryAccess {

    BossAccountStatusResult query(Long tenantId, String billBatchCode, String billNo);
}
