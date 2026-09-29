/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.response;

import lombok.Getter;
import lombok.Setter;

/**
 * C-STORE 回写响应。
 */
@Getter
@Setter
public class OnCStoreReceivedResponse {

    /** 绑定的工单主键。 */
    private Long workItemId;

    /** ImagingStudy 主键。 */
    private Long studyId;

    /** Study Instance UID。 */
    private String studyInstanceUid;

    /** 回写后的 hasMedia。 */
    private Boolean hasMedia;
}
