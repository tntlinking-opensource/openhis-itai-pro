/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.elep.domain.prescription.access;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.elep.domain.prescription.result.ElepPlatformInvokeResult;

/** 【A0003】电子处方上传出站端口（bridge {@code ElepA0003Invoker} 实现）。 */
public interface ElepA0003InvokeAccess {

    ElepPlatformInvokeResult invoke(String contractBusNo, JSONObject body);
}
