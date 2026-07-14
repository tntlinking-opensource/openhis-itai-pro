/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.conditiondefinition.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊断定义分页查询入参。
 */
@Getter
@Setter
public class DiseasePageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** source枚举编码。 */
    private String sourceEnum;
    /** 状态枚举编码。 */
    private String statusEnum;
    /** 医保标志。 */
    private Boolean ybFlag;
}
