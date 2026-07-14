/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.access;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.domain.prescription.result.ElepPlatformInvokeResult;

/** 【A0005】平台处方查询出站端口（bridge {@code ElepA0005Invoker} 实现）。 */
public interface ElepA0005InvokeAccess {

    ElepPlatformInvokeResult invoke(String contractBusNo, JSONObject body);
}
