/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

/**
 * 目录检查问题码（只读查询，不落待办）。
 */
public final class CatalogCheckIssueCodes {

    /** 国家目录结束日已过。 */
    public static final String EXPIRED = "EXPIRED";
    /** 国家目录 vali_flag 非有效。 */
    public static final String DISABLED = "DISABLED";
    /** 局文停用已生效。 */
    public static final String NOTICE_STOP = "NOTICE_STOP";
    /** 局文有效期已过（叠加 begin/end）。 */
    public static final String NOTICE_EXPIRED = "NOTICE_EXPIRED";
    /** 局文尚未到生效日。 */
    public static final String NOTICE_NOT_EFFECTIVE = "NOTICE_NOT_EFFECTIVE";
    /** 院内售价超过医保限价。 */
    public static final String PRICE_OVER = "PRICE_OVER";
    /** 已启用医保项目未填国家码。 */
    public static final String MISSING_YB_NO = "MISSING_YB_NO";
    /** 本地国家目录镜像无此码。 */
    public static final String NOT_IN_CATALOG = "NOT_IN_CATALOG";

    public static final String LABEL_EXPIRED = "已过期";
    public static final String LABEL_DISABLED = "已停用";
    public static final String LABEL_NOTICE_STOP = "局文停用";
    public static final String LABEL_NOTICE_EXPIRED = "局文过期";
    public static final String LABEL_NOTICE_NOT_EFFECTIVE = "局文未生效";
    public static final String LABEL_PRICE_OVER = "超限价";
    public static final String LABEL_MISSING_YB_NO = "缺医保码";
    public static final String LABEL_NOT_IN_CATALOG = "镜像无此码";

    private CatalogCheckIssueCodes() {
    }

    /**
     * 问题码对应中文标签。
     *
     * @param code 问题码
     * @return 标签；未知码原样返回
     */
    public static String labelOf(String code) {
        if (EXPIRED.equals(code)) {
            return LABEL_EXPIRED;
        }
        if (DISABLED.equals(code)) {
            return LABEL_DISABLED;
        }
        if (NOTICE_STOP.equals(code)) {
            return LABEL_NOTICE_STOP;
        }
        if (NOTICE_EXPIRED.equals(code)) {
            return LABEL_NOTICE_EXPIRED;
        }
        if (NOTICE_NOT_EFFECTIVE.equals(code)) {
            return LABEL_NOTICE_NOT_EFFECTIVE;
        }
        if (PRICE_OVER.equals(code)) {
            return LABEL_PRICE_OVER;
        }
        if (MISSING_YB_NO.equals(code)) {
            return LABEL_MISSING_YB_NO;
        }
        if (NOT_IN_CATALOG.equals(code)) {
            return LABEL_NOT_IN_CATALOG;
        }
        return code == null ? "" : code;
    }
}
