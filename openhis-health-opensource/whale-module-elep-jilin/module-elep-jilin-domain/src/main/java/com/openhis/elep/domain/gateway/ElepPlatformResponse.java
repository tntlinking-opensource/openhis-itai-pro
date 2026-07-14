/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.gateway;

import lombok.Data;
import lombok.experimental.Accessors;

/** 电子处方中台原始响应（HSE 外层）。 */
@Data
@Accessors(chain = true)
public class ElepPlatformResponse {

    private int code;
    private String message;
    private Boolean success;
    private String encData;
    private String signData;
    /** SM4 解密后的业务 JSON。 */
    private String decryptedBody;
}
