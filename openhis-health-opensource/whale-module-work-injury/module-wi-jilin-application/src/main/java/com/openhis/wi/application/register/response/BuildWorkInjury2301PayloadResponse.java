/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.application.register.response;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/** 【2301】审批上传 input 预览响应。 */
@Data
@Accessors(chain = true)
public class BuildWorkInjury2301PayloadResponse {

    /** 待下发 Win 的业务体 input。 */
    private Map<String, Object> input;
}
