/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.inventory.assembler;

import com.zxplat.plugin.chs.domain.share.enums.RxFlag;

/**
 * 进销存平台字段编码静态辅助（无 IO、无 Spring）。
 *
 * <p>将 HIS 侧布尔/业务值转为 350x 报文要求的字符串码，避免在 Loader 里写 {@code "0"}/{@code "1"}。</p>
 */
public final class InventoryPlatformFieldAssembler {

    private InventoryPlatformFieldAssembler() {
    }

    /**
     * 药典「是否处方药」→ 平台 {@code rx_flag}。
     *
     * @param rxFlag 药典维护的处方药标志；未维护（{@code null}）视为非处方药
     * @return {@link RxFlag} 对应 code（0/1）
     */
    public static String toRxFlagCode(Boolean rxFlag) {
        return RxFlag.toCode(rxFlag);
    }
}
