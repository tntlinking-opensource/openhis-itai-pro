/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

import java.util.List;

/**
 * 启动多文件异步应用（confirm 必须 true）。
 */
@Data
public class StartApplyNoticeDocsRequest {

    /** 文件头主键列表。 */
    private List<Long> docIds;

    /** 二次确认；必须为 true。 */
    private Boolean confirm;

    private Long operatorId;
}
