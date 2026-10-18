/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 下载标准落地包请求（IF-CHS-CAT-005-R-05）。
 */
@Data
public class DownloadNoticePackRequest {

    /** 文件头主键；必填。 */
    private Long docId;
}
