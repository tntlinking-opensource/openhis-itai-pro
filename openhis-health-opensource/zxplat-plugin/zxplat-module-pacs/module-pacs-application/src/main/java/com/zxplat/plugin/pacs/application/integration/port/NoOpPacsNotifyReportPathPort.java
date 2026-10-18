/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.integration.port;

import com.zxplat.domain.share.extension.pacsintegration.PacsNotifyReportPathCommand;
import com.zxplat.domain.share.extension.pacsintegration.PacsNotifyReportPathPort;

/**
 * 报告路径回传 NoOp：adapter 未启用时占位，由 AppService 记本地集成日志。
 */
public class NoOpPacsNotifyReportPathPort implements PacsNotifyReportPathPort {

    @Override
    public boolean isOutboundEnabled() {
        return false;
    }

    @Override
    public boolean notifyReportPath(PacsNotifyReportPathCommand command) {
        return true;
    }
}
