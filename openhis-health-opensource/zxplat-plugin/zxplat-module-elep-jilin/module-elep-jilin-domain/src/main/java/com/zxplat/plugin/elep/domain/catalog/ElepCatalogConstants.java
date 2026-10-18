/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.catalog;

/** 电子处方目录查询常量（对齐 CHS 镜像表约定）。 */
public final class ElepCatalogConstants {

    public static final String DEFAULT_VALI_FLAG = "1";
    public static final String LIST_TYPE_WESTERN_DRUG = "101";
    public static final String SOURCE_INFNO_1301 = "1301";
    public static final String SOURCE_INFNO_1312 = "1312";
    public static final String SOURCE_INFNO_1307 = "1307";

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MIN_KEYWORD_LENGTH = 2;

    /** 有批准文号/规格时优先查 1301 明细（对齐 CHS）。 */
    public static boolean prefers1301Detail(String aprvno, String spec) {
        return (aprvno != null && !aprvno.isBlank()) || (spec != null && !spec.isBlank());
    }

    private ElepCatalogConstants() {
    }
}
