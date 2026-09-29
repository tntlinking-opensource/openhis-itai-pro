/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.extension.pacsintegration;

/**
 * PACS adapter 运行参数只读视图（CFG-04）。
 *
 * @param enabled adapter 是否已装配启用
 * @param mockOutboundSuccess Mock 外发是否默认成功
 */
public record PacsAdapterRuntimeSnapshot(boolean enabled, boolean mockOutboundSuccess) {
}
