/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.domain.settle.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2207 invoke 链完成后 HIS 本地落账挂接命令（不再次调 Win）。
 *
 * <p>invoke 链已将 2207 写入 {@code yb_settlement_record}；本命令绑定 {@code paymentId} 并返回金额摘要。</p>
 */
@Data
@Accessors(chain = true)
public class ConfirmWorkInjurySettleCommand {

    /** 收银草稿 id。 */
    private Long paymentDraftId;

    /** 本地 payment id（落账后回写，可空）。 */
    private Long paymentId;

    /** 收费批次号（混合结算时可指定）。 */
    private String chrgBchno;

    /** 就诊 id。 */
    private Long encounterId;
}
