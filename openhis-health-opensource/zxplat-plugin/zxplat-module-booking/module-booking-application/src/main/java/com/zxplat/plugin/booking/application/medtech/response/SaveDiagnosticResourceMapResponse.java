/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.medtech.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存医技资源映射响应（7.7）。
 */
@Getter
@Setter
public class SaveDiagnosticResourceMapResponse {

    /** 映射主键。 */
    private Long id;
}
