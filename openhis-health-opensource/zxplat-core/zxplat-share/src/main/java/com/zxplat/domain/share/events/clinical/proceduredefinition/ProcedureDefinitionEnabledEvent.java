/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.proceduredefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 手术操作定义已启用事件（DM-CLN-009 §5；beforeCommit）。
 */
@Getter
@Setter
public class ProcedureDefinitionEnabledEvent extends DomainEvent {

    /** 批量启用的主键列表。 */
    private List<Long> idList;
    /** 目标发布状态（ACTIVE）。 */
    private String statusEnum;

    /**
     * 构造已启用事件。
     *
     * @param idList     主键列表；首个 id 作为聚合根 id 快照
     * @param statusEnum 目标状态码
     */
    public ProcedureDefinitionEnabledEvent(List<Long> idList, String statusEnum) {
        super(firstId(idList), AggregateTypeConstants.PROCEDURE_DEFINITION);
        this.idList = idList;
        this.statusEnum = statusEnum;
    }

    /**
     * 取列表首个主键作为事件聚合根 id；空列表返回 null。
     *
     * @param idList 主键列表
     * @return 首个 id 或 null
     */
    private static Long firstId(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return null;
        }
        return idList.get(0);
    }
}
