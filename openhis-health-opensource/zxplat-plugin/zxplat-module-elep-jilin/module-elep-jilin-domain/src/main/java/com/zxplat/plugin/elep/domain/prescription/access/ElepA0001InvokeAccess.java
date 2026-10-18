/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.domain.prescription.access;

import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.elep.domain.prescription.result.ElepPlatformInvokeResult;

/** 【A0001】上传预核验出站端口（bridge {@code ElepA0001Invoker} 实现）。 */
public interface ElepA0001InvokeAccess {

    ElepPlatformInvokeResult invoke(String contractBusNo, JSONObject body);
}
