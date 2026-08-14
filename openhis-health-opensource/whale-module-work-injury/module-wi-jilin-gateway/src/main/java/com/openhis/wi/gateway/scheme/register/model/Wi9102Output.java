/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.wi.gateway.scheme.register.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 【9102】审批信息查询 output 解析结果（10-P0 §4.2，多行）。
 */
@Data
@Accessors(chain = true)
public class Wi9102Output {

    /** 审批行列表；submit 时优先绑定门诊(12)，否则首条。 */
    private List<Wi9102QualificationRow> rows = new ArrayList<>();
}
