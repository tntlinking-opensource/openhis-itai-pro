/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 平台 input 包装：{@code { "feedetail": [...] }} */
@Data
@Accessors(chain = true)
public class ChsFeedetailInput<T> {

    private List<T> feedetail;

    @JSONField(name = "insuplc_admdvs")
    private String insuplcAdmdvs;
}
