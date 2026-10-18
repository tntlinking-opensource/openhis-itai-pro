/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 文件明细分页请求（IF-CHS-CAT-005-R-02）。
 */
@Data
public class PageNoticeLineRequest {

    /** 文件头主键；必填。 */
    private Long docId;

    /** 页码（从 1 开始）。 */
    private Integer page;

    /** 每页条数。 */
    private Integer size;

    /** 动作过滤。 */
    private String action;

    /** 对照状态过滤。 */
    private String matchStatus;
}
