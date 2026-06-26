/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

/**
 * 13xx 文件目录版本链推进方式（对齐 OpenHIS {@code ReadCsv}）。
 */
public enum CatalogVersionChainMode {

    /** 优先 col「版本名称」，无则回退 max(col「版本号」) — 1301/1302/1305/1306 */
    VERSION_NAME_THEN_NUMBER,

    /** 仅 idxVersionNo 列 — 1307/1308/1309/1314/1315（对齐 OpenHIS ReadCsv version_number / version） */
    VERSION_NUMBER_ONLY,

    /** @deprecated 1315 已改用 {@link #VERSION_NUMBER_ONLY} index 12 */
    @Deprecated
    VERSION_FIELD
}
