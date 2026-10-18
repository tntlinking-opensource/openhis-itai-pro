/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.application.integration.port;

import com.zxplat.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshot;
import com.zxplat.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;

/**
 * adapter 未装配时的运行参数占位：enabled=false，mockOutboundSuccess=true。
 */
public class NoOpPacsAdapterRuntimeSnapshotPort implements PacsAdapterRuntimeSnapshotPort {

    @Override
    public PacsAdapterRuntimeSnapshot getSnapshot() {
        return new PacsAdapterRuntimeSnapshot(false, true);
    }
}
