/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.diagnosis.bodystructure;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 身体部位已删除事件。
 */
@Getter
@Setter
public class BodyStructureDeletedEvent extends DomainEvent {
    /** 本次软删的身体部位主键列表（批量删除同批发布）。 */
    private List<Long> ids;

    /**
     * 身体部位批量删除后发布；聚合根 id 取列表首项供事件路由。
     *
     * @param ids 已删除部位主键列表，可空
     */
    public BodyStructureDeletedEvent(List<Long> ids) {
        super(resolveAggregateId(ids), AggregateTypeConstants.BODY_STRUCTURE);
        this.ids = ids;
    }

    /**
     * 事件聚合标识：多行删除时以首条主键代表本批。
     *
     * @param ids 部位主键列表
     * @return 聚合 id；列表为空时为 null
     */
    private static Long resolveAggregateId(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }
}
