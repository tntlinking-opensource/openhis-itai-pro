/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.viewer.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 打开阅片响应。
 */
@Getter
@Setter
public class LaunchViewerResponse {

    /** OHIF 启动完整 URL（含 StudyUID、lng、Token）。 */
    private String launchUrl;

    /** DICOM Study Instance UID。 */
    private String studyInstanceUid;

    /** Token 过期时间（ISO-8601）。 */
    private String expiresAt;
}
