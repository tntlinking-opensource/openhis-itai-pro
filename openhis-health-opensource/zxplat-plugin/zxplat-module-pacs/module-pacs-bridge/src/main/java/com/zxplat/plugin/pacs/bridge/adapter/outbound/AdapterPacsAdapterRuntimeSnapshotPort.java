/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacs.bridge.adapter.outbound;

import com.zxplat.plugin.pacs.bridge.adapter.config.PacsAdapterProperties;
import com.zxplat.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshot;
import com.zxplat.domain.share.extension.pacsintegration.PacsAdapterRuntimeSnapshotPort;
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
