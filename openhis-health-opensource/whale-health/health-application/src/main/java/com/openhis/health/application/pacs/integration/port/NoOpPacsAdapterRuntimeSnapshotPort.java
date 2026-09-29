/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.integration.port;

import com.openhis.health.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshot;
import com.openhis.health.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;

/**
 * adapter 未装配时的运行参数占位：enabled=false，mockOutboundSuccess=true。
 */
public class NoOpPacsAdapterRuntimeSnapshotPort implements PacsAdapterRuntimeSnapshotPort {

    @Override
    public PacsAdapterRuntimeSnapshot getSnapshot() {
        return new PacsAdapterRuntimeSnapshot(false, true);
    }
}
