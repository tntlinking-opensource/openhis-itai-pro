/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.medicalinfo.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 5201/5202/5204/5205/5206/5301 列表型查询统一出参（行数据透传 UI） */
@Data
@Accessors(chain = true)
public class MedicalInfoQueryResult {

    private String infno;
    private List<Map<String, Object>> rows = new ArrayList<>();
}
