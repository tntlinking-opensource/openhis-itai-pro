/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.specimendefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 样本定义已创建事件（DM §5.1）；新建目录成功后发布，载荷含主键与关键快照。
 */
@Getter
@Setter
public class SpecimenDefinitionCreatedEvent extends DomainEvent {
    /** 业务编码。 */
    private String busNo;
    /** 样本名称。 */
    private String name;
    /** 标本种类。 */
    private String typeEnum;
    /** 发布状态。 */
    private String statusEnum;

    /**
     * @param specimenDefinitionId 样本定义主键
     * @param busNo 业务编码
     * @param name 样本名称
     * @param typeEnum 标本种类
     * @param statusEnum 发布状态
     */
    public SpecimenDefinitionCreatedEvent(Long specimenDefinitionId, String busNo, String name, String typeEnum, String statusEnum) {
        super(specimenDefinitionId, AggregateTypeConstants.SPECIMEN_DEFINITION);
        this.busNo = busNo;
        this.name = name;
        this.typeEnum = typeEnum;
        this.statusEnum = statusEnum;
    }
}
