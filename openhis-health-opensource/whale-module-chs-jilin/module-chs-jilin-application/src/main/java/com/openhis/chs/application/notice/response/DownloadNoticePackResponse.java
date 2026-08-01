/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.response;

import lombok.Data;

/**
 * 下载标准落地包响应（IF-CHS-CAT-005-R-05）。
 */
@Data
public class DownloadNoticePackResponse {

    /** 下载文件名。 */
    private String fileName;

    /** 标准包内容 Base64。 */
    private String contentBase64;
}
