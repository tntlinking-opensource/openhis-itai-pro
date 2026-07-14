/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.access;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.domain.prescription.result.ElepPlatformInvokeResult;

/** 【A0004】电子处方撤销出站端口（bridge {@code ElepA0004Invoker} 实现）。 */
public interface ElepA0004InvokeAccess {

    ElepPlatformInvokeResult invoke(String contractBusNo, JSONObject body);
}
