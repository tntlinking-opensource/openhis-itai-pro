/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除关键图请求（IF-PACS-CAP-14）。
 */
@Getter
@Setter
public class DeleteKeyImageRequest {

    /** 检查工单主键。 */
    private Long workItemId;

    /** 关键图索引主键。 */
    private Long mediaId;
}
