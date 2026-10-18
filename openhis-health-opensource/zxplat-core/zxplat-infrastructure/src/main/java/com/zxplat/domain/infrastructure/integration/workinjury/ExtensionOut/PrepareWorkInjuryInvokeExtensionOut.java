/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 工伤 Win invoke prepare 结果（首步载荷）。 */
@Data
@Accessors(chain = true)
public class PrepareWorkInjuryInvokeExtensionOut {

    private String sessionId;
    private String stepToken;
    private String infno;
    private String fixmedinsCode;
    private String operatorNo;
    private Map<String, Object> input;
    private String nextAction;
    private String errMsg;
}
