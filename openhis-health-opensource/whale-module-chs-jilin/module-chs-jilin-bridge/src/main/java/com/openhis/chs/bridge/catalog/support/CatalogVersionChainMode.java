/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.catalog.support;

/**
 * 13xx 文件目录版本链推进方式（对齐 OpenHIS {@code ReadCsv}）。
 */
public enum CatalogVersionChainMode {

    /** 优先「版本名称」列，无则回退 max「版本号」列；用于 1301/1302/1305/1306 */
    VERSION_NAME_THEN_NUMBER,

    /** 仅使用 idxVersionNo 列；用于 1307/1308/1309/1314/1315（对齐 OpenHIS version_number / version） */
    VERSION_NUMBER_ONLY
}
