/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support;

/**
 * 13xx 文件目录版本链推进方式（对齐平台 {@code ReadCsv}）。
 */
public enum CatalogVersionChainMode {

    /** 优先「版本名称」列，无则回退 max「版本号」列；用于 1301/1302/1305/1306 */
    VERSION_NAME_THEN_NUMBER,

    /** 仅使用 idxVersionNo 列；用于 1307/1308/1309/1310/1311/1313/1314/1315（对齐平台 version_number / version） */
    VERSION_NUMBER_ONLY
}
