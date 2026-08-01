/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 反结/召回后冲销清床汇总径净销售 3506 命令。
 *
 * <p>明细数量/对人仍按本就诊已成功的 {@code CLEAR_BED_SUMMARY} 3505 审计组装；
 * {@code fixmedins_bchno}=原清床 3505 批次号；{@code medins_prod_sel_no}=3511 平台销售流水（吉林口径）；
 * 可传入 {@link #dispenseIds} 作为本地 {@code source_line_id}（未传则 bridge 按审计生成退药行）。</p>
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
     * 已生成的退药发放行主键（与待冲销清床 3505 按序配对）；可空。
     * <p>空时由 bridge 按每条成功 3505 现场创建 REFUND 退药单以取得新单据号。</p>
     */
    private List<Long> dispenseIds;
}
