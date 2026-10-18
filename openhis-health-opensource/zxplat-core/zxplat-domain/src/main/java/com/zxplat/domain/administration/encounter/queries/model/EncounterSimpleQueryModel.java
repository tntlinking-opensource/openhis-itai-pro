/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊摘要读模型：列表/下拉用最小字段集。
 */
@Getter
@Setter
public class EncounterSimpleQueryModel {
    /** 就诊主键。 */
    private Long id;
    /** 就诊业务单号。 */
    private String busNo;
    /** 就诊类别枚举编码。 */
    private String classEnum;
    /** 就诊状态枚举编码。 */
    private String statusEnum;
}
