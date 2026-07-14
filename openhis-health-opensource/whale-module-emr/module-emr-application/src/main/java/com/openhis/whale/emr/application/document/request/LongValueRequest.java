/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.emr.application.document.request;

import lombok.Getter;
import lombok.Setter;

/**
 * 单 Long 参数（前端 delete 等接口传 { value }）
 */
@Getter
@Setter
public class LongValueRequest {

    /** 通用长整型参数值 */
    private Long value;
}
