/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 绑定 Study 请求（IF-PACS-CAP-06）。
 */
@Getter
@Setter
public class EnsureStudyRequest {

    /** 检查工单主键。 */
    private Long workItemId;

    /** DICOM Study Instance UID。 */
    private String studyInstanceUid;

    /** 主 Series Instance UID；可选。 */
    private String seriesInstanceUid;
}
