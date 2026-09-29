/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 绑定 Study 响应。
 */
@Getter
@Setter
public class EnsureStudyResponse {

    /** ImagingStudy 主键。 */
    private Long studyId;

    /** Study Instance UID。 */
    private String studyInstanceUid;

    /** Series Instance UID。 */
    private String seriesInstanceUid;

    /** 回写后的 hasMedia。 */
    private Boolean hasMedia;
}
