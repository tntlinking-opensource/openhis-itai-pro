/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationdispense.query;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 住院护士站追溯码分配 · 待分配患者队列分页入参（**AS-IP-NUR-015 · IF-IP-TRC-01**）。
 *
 * <p>关键字收窄由应用层预解析为 {@link #encounterIds} 后传入；领域层不做 Encounter 跨聚合 join。</p>
 */
@Getter
@Setter
public class EncountersPendingTraceAssignmentPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 可见住院就诊主键集合（**BR-IP-TRC-001**）；非 null 且为空时返回空页。
     */
    private List<Long> encounterIds;
}
