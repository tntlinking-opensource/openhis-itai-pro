/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方平台 A000x 出站统一结果（与 gateway scheme 解耦）。 */
@Data
@Accessors(chain = true)
public class ElepPlatformInvokeResult {

    private boolean success;
    private String code;
    private String message;
    private String bodyJson;
}
