/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.outbound;

import com.openhis.health.domain.share.extension.pacsintegration.PacsNotifyReportPathPort;
import lombok.RequiredArgsConstructor;

/**
 * 将应用端口委托给 {@link PacsNotifyReportPathCommandHandler}（infrastructure 落点）。
 */
@RequiredArgsConstructor
public class AdapterPacsNotifyReportPathPort implements PacsNotifyReportPathPort {

    private final PacsNotifyReportPathCommandHandler handler;

    @Override
    public boolean isOutboundEnabled() {
        return true;
    }

    @Override
    public boolean notifyReportPath(
        com.openhis.health.domain.share.extension.pacsintegration.PacsNotifyReportPathCommand command) {
        PacsNotifyReportPathCommand wire = new PacsNotifyReportPathCommand();
        if (command != null) {
            wire.setReportId(command.getReportId());
            wire.setWorkItemId(command.getWorkItemId());
            wire.setAccessionNumber(command.getAccessionNumber());
            wire.setPdfStorageKey(command.getPdfStorageKey());
            wire.setOperatorId(command.getOperatorId());
        }
        return handler.handle(wire);
    }
}
