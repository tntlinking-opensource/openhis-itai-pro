/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 上传 DCM 请求（IF-PACS-CAP-09 骨架）。
 */
@Getter
@Setter
public class UploadDicomRequest {

    /** 检查工单主键。 */
    private Long workItemId;

    /** DICOM 文件 Base64。 */
    private String contentBase64;
}
