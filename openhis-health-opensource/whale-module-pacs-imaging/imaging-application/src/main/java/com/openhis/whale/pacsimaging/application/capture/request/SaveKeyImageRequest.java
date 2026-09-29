/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存关键图请求（IF-PACS-CAP-13）。
 */
@Getter
@Setter
public class SaveKeyImageRequest {

    /** 检查工单主键。 */
    private Long workItemId;

    /** 图像 Base64。 */
    private String contentBase64;

    /** 内容类型（image/jpeg 或 image/png）。 */
    private String contentType;

    /** 来源枚举 code；缺省 KEY_IMAGE。 */
    private String sourceEnum;
}
