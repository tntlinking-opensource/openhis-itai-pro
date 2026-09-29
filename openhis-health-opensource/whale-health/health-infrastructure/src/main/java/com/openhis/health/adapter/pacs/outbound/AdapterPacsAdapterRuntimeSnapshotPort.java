/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.adapter.pacs.outbound;

import com.openhis.health.adapter.pacs.config.PacsAdapterProperties;
import com.openhis.health.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshot;
import com.openhis.health.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;
import lombok.RequiredArgsConstructor;

/**
 * 从 {@link PacsAdapterProperties} 填充 CFG-04 运行快照（infrastructure 落点）。
 */
@RequiredArgsConstructor
public class AdapterPacsAdapterRuntimeSnapshotPort implements PacsAdapterRuntimeSnapshotPort {

    private final PacsAdapterProperties properties;

    @Override
    public PacsAdapterRuntimeSnapshot getSnapshot() {
        return new PacsAdapterRuntimeSnapshot(properties.isEnabled(), properties.isMockOutboundSuccess());
    }
}
