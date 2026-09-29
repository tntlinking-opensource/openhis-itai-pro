/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.pacsintegration;

/**
 * 报告路径回传 HIS 出站端口（IF-PACS-INT-06）。
 *
 * <p>应用层只依赖本接口；真实外发由 adapter 装配，缺省为 NoOp。</p>
 */
public interface PacsNotifyReportPathPort {

    /**
     * 是否已启用真实外发（adapter 已装配）。
     *
     * @return true 表示会调用 HIS；false 表示由 AppService 记本地日志即可
     */
    boolean isOutboundEnabled();

    /**
     * 回传报告路径。
     *
     * @param command 回传命令
     * @return 是否外发成功（仅 {@link #isOutboundEnabled()} 为 true 时有意义）
     */
    boolean notifyReportPath(PacsNotifyReportPathCommand command);
}
