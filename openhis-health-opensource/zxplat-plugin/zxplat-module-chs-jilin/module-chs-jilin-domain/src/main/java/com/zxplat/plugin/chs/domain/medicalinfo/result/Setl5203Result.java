/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.medicalinfo.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 【5203】结算信息查询出参（setlinfo 头 + 基金分项列表） */
@Data
@Accessors(chain = true)
public class Setl5203Result {

    private String infno;
    private Map<String, Object> header = new HashMap<>();
    private List<Map<String, Object>> setlDetails = new ArrayList<>();
}
