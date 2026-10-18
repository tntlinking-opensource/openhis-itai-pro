/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.encounterhistory.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 离院方式解析查询模型。
 */
@Getter
@Setter
/**
 * DischargeWayQueryModel。
 */
public class DischargeWayQueryModel {
    /** out方式编码。 */
    /**
     * outWayCode。
     */
    private String outWayCode;
    /** 就诊历史主键。 */
    /**
     * encounterHistoryId。
     */
    private Long encounterHistoryId;
}
