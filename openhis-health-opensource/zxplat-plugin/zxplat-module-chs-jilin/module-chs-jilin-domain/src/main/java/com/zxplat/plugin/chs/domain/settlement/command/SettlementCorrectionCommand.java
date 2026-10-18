/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2601 冲正命令（omsgid + oinfno） */
@Data
@Accessors(chain = true)
public class SettlementCorrectionCommand {

    /** 契约 busNo，供 gateway 路由机构连接 */
    private String contractBusNo;
    /** 人员编号 */
    private String psnNo;
    /** 原交易 msgid（如 2207 的 medins_setl_id / msgid_2207） */
    private String omsgid;
    /** 原交易编号（2207 / 2208 / 2304 / 2305 等） */
    private String oinfno;
    /** 本地结算回执 ID（可空） */
    private Long settlementRecordId;
    /** 操作员 ID */
    private Long operatorId;
    /** 租户 ID */
    private Long tenantId;
}
