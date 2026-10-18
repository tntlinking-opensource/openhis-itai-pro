/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printtemplate.constants;

/**
 * 画布打印模板状态码（存库 VARCHAR）。
 */
public final class PrintTemplateStatus {

    /** 草稿：可编辑，未对租户发布。 */
    public static final String DRAFT = "draft";
    /** 已发布：租户可见并可用于打印绑定。 */
    public static final String PUBLISHED = "published";
    /** 已归档：只读历史版本，不可再绑定。 */
    public static final String ARCHIVED = "archived";

    private PrintTemplateStatus() {
    }
}
