/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicedevicebinding.request;

import lombok.Data;

/**
 * 诊疗耗材绑定页：按关键字检索可选耗材定义下拉。
 */
@Data
public class ListDeviceDefOptionsRequest {

    /** 综合关键字（编码/名称/拼音/五笔）；空白则返回全部启用耗材。 */
    private String keyword;
}
