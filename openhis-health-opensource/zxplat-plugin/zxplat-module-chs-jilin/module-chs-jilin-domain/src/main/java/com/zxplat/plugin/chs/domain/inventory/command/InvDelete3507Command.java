/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【3507】商品信息删除 */
@Data
@Accessors(chain = true)
public class InvDelete3507Command {

    private String contractBusNo;
    private Long contractId;
    /** 定点医药机构批次流水号 */
    private String fixmedinsBchno;
    /** 1 盘存 / 2 变更 / 3 采购 / 4 销售 */
    private String invDataType;
    /** 关联本地流水 sourceId（可选，运维补记） */
    private Long sourceId;
}
