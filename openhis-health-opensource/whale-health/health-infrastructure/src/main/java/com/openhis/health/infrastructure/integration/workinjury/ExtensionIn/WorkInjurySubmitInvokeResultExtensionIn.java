/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 工伤 Win invoke submit 入参。 */
@Data
@Accessors(chain = true)
public class WorkInjurySubmitInvokeResultExtensionIn {

    private String sessionId;
    private String stepToken;
    private boolean success;
    private String infcode;
    private String errMsg;
    private Map<String, Object> output;
    private String winMsgid;
    private Map<String, Object> context;
    private String winRawInput;
}
