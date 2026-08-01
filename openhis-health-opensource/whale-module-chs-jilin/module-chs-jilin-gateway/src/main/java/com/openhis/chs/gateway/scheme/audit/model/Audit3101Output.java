/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.gateway.scheme.audit.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 【3101】output：result 为违规数组 */
@Data
@Accessors(chain = true)
public class Audit3101Output {

    /** 违规结果列表 */
    private List<Audit3101ViolationRow> result = new ArrayList<>();
}
