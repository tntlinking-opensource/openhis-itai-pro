/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.model;

import lombok.Data;

/**
 * 未收费费用明细引用（供 apply_log.affected_refs_json）。
 */
@Data
public class HisUnchargedFeeLine {

    /** 收费单/账户侧单据主键；无则空。 */
    private Long billId;

    /** 单据号。 */
    private String billNo;

    /** 就诊主键。 */
    private Long encounterId;

    /** 费用明细行主键。 */
    private Long itemLineId;

    /** 同步前等级。 */
    private String beforeChrgitmLv;

    /** 关联产品主键。 */
    private Long productId;
}
