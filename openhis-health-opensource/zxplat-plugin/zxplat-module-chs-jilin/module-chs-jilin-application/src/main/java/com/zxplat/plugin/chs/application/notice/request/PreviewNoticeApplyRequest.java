/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 应用差异预览请求（IF-CHS-CAT-005-R-06）。
 */
@Data
public class PreviewNoticeApplyRequest {

    /** 文件头主键；必填。 */
    private Long docId;
}
