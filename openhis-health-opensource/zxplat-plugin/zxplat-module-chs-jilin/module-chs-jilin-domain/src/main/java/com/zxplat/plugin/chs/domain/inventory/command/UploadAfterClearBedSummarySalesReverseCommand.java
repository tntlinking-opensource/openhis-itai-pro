/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 反结/召回后冲销清床/无床直结合并净销售 3506 命令。
 *
 * <p>明细数量/对人仍按本就诊已成功的 {@code CLEAR_BED_SUMMARY} 3505 审计组装；
 * {@code fixmedins_bchno}=原清床 3505 批次号；{@code medins_prod_sel_no}=3511 平台销售流水（吉林口径）。
 * <b>不写</b> {@code MedicationDispense}；本地幂等键为原 3505 上报流水 id。</p>
 */
@Data
@Accessors(chain = true)
public class UploadAfterClearBedSummarySalesReverseCommand {

    /** 就诊主键（与清床 3505 的 {@code source_id} 一致）。 */
    private Long encounterId;

    /** 契约业务号；可空，回落原 3505 流水上的契约。 */
    private String contractBusNo;

    /** 契约主键；可空。 */
    private Long contractId;

    /**
     * 已废弃：清床冲销不再读写发放表；保留字段仅为兼容旧调用方，bridge 忽略。
     */
    @Deprecated
    private List<Long> dispenseIds;
}
