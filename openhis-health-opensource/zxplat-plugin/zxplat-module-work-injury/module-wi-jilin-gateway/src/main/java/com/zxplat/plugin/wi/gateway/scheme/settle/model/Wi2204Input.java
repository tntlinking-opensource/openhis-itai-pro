/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.settle.model;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 【2204】处方明细上报 input（10-P0 §8.2；节点 inputs[]，最多 50 条/次）。
 */
@Data
@Accessors(chain = true)
public class Wi2204Input {

    /** 收费明细行列表。 */
    @JSONField(name = "inputs")
    private List<Wi2204FeeLine> inputs = new ArrayList<>();
}
