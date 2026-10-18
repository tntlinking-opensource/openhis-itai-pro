/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.pacsintegration;

/**
 * PACS adapter 运行时只读快照（IF-PACS-CFG-04）。
 *
 * <p>应用层只依赖本接口；真实值由 infrastructure 读 {@code PacsAdapterProperties} 填充，缺省为 NoOp。</p>
 */
public interface PacsAdapterRuntimeSnapshotPort {

    /**
     * 读取当前 adapter 运行参数快照。
     *
     * @return 非空快照（enabled / mockOutboundSuccess）
     */
    PacsAdapterRuntimeSnapshot getSnapshot();
}
