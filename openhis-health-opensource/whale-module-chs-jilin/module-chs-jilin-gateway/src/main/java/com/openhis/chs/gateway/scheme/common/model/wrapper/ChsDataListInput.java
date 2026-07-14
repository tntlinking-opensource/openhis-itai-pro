/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.common.model.wrapper;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 平台 input 包装：{@code { "data": [...] }} */
@Data
@Accessors(chain = true)
public class ChsDataListInput<T> {

    private List<T> data;
}
