/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.activityobservationlink.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 项目↔小项绑定读模型。
 *
 * <p>{@code odDisplay}/{@code odCode} 由 AppService 经 OD QueryService enrich（B.6.4：本 QS 不跨聚合读）。</p>
 */
@Getter
@Setter
public class ActivityObservationLinkModel {

    /** 链接行主键。 */
    private Long id;

    /** 项目 AD 主键。 */
    private Long activityDefinitionId;

    /** 小项 OD 主键。 */
    private Long observationDefinitionId;

    /** 排序号。 */
    private Integer sortNum;

    /** 是否必检。 */
    private Boolean requiredFlag;

    /** 小项展示名（应用层 enrich）。 */
    private String odDisplay;

    /** 小项编码（应用层 enrich）。 */
    private String odCode;
}
