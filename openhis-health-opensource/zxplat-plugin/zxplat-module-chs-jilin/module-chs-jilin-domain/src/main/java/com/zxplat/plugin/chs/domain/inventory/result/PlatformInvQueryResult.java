/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.inventory.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 【3508–3513】平台进销存查询出参（行 Map 透传） */
@Data
@Accessors(chain = true)
public class PlatformInvQueryResult {

    private String infno;
    private List<Map<String, Object>> rows = new ArrayList<>();
}
