/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.capture.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 删除关键图响应。
 */
@Getter
@Setter
public class DeleteKeyImageResponse {

    /** 删除后的 hasMedia。 */
    private Boolean hasMedia;
}
