/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import java.util.function.Consumer;

/**
 * 药品/耗材目录列表的统一展示辅助（启用文案、医保对码标志等）。
 *
 * <p>读模型映射层多处需要相同的布尔与中文字典组合，集中于此避免 Mapper 间复制粘贴。</p>
 *
 */
public final class CatalogPresentationUtil {

    private CatalogPresentationUtil() {
    }

    /**
     * 「启用 / 停用」布尔列与中文字典文案成对写入。
     *
     * @param activeFlag 是否启用
     * @param flagSetter 布尔字段 setter
     * @param textSetter 展示文案 setter
     */
    public static void fillEnableDisableLabels(
        Boolean activeFlag,
        Consumer<Boolean> flagSetter,
        Consumer<String> textSetter) {
        if (Boolean.TRUE.equals(activeFlag)) {
            flagSetter.accept(Boolean.TRUE);
            textSetter.accept("启用");
            return;
        }
        flagSetter.accept(Boolean.FALSE);
        textSetter.accept("停用");
    }

    /**
     * 列表「医保对码」：医保编码非空则视为已对码。
     *
     * @param ybNo 医保编码
     * @return 是否已对码
     */
    public static Boolean insuranceMatchedFlag(String ybNo) {
        if (ybNo != null && !ybNo.isBlank()) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }
}
