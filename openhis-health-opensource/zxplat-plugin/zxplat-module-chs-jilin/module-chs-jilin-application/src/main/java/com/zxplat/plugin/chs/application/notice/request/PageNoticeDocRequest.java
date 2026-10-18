/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 文件列表分页请求（IF-CHS-CAT-005-R-01）。
 */
@Data
public class PageNoticeDocRequest {

    /** 页码（从 1 开始）。 */
    private Integer page;

    /** 每页条数。 */
    private Integer size;

    /** 文件种类过滤。 */
    private String docKind;

    /** 品类过滤。 */
    private String category;

    /** 应用状态过滤。 */
    private String applyStatus;

    /** 标题/文件名关键字。 */
    private String keyword;
}
