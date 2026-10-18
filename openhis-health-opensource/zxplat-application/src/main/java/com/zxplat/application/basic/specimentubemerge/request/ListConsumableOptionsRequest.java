/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.specimentubemerge.request;

import lombok.Data;

/**
 * 耗材定义下拉检索请求。
 */
@Data
public class ListConsumableOptionsRequest {

    /** 关键字（名称/编码）。 */
    private String keyword;
}
