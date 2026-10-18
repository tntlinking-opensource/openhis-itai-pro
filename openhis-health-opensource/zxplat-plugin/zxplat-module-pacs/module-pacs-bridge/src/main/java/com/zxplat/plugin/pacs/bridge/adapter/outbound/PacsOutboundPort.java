/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.adapter.outbound;

/**
 * PACS 外发端口（登记/申请同步、报告路径回传）。
 */
public interface PacsOutboundPort {

    /**
     * 外发检查申请/登记。
     *
     * @param command 外发命令
     * @return 外发结果
     */
    PacsOutboundResult sendApply(PacsSendApplyCommand command);

    /**
     * 报告路径回传 HIS（INT-06）。
     *
     * @param command 回传命令
     * @return 外发结果
     */
    PacsOutboundResult notifyReportPath(PacsNotifyReportPathCommand command);
}
