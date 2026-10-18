/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存关键图响应。
 */
@Getter
@Setter
public class SaveKeyImageResponse {

    /** 关键图索引主键。 */
    private Long mediaId;

    /** 对象键。 */
    private String objectKey;

    /** 回写后的 hasMedia。 */
    private Boolean hasMedia;
}
