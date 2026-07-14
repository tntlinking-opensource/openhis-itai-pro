/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.common.model.wrapper;

import lombok.Data;
import lombok.experimental.Accessors;

/** 平台 input 包装：{@code { "data": T }} */
@Data
@Accessors(chain = true)
public class ChsDataInput<T> {

    private T data;
}
