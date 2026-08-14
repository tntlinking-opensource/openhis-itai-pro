/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊护士站患者队列分页入参（IF-NUR-01 / BR-NUR-001）。
 *
 * <p>在通用分页窗之上增加可选开立科室筛选；空 {@link #orgId} 表示全部科室。</p>
 */
@Getter
@Setter
public class NurseStationOutpatientQueueQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 开立科室筛选（可选）。
     *
     * <p>非空时等值匹配就诊 {@code organization_id}（门诊挂号/开立科室上下文）；
     * 空或未传表示不按科室收窄（全部）。</p>
     */
    private Long orgId;
}
