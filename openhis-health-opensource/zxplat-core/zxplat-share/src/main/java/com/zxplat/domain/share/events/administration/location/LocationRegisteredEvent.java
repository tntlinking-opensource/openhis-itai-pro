/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.location;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 位置已登记事件。新位置登记成功时发布。
 */
@Getter
@Setter
public class LocationRegisteredEvent extends DomainEvent {
    /** 位置名称。 */
    private String name;
    /** 物理形式枚举值。 */
    private String formEnum;
    /** 所属机构标识。 */
    private Long organizationId;
    /** 床位业务号（床位类型位置使用）。 */
    private String locationBusNo;
    /** 所属病区标识。 */
    private Long wardId;
    /** 所属病区名称快照。 */
    private String wardName;

    /** 无参构造器（反序列化用）。 */
    public LocationRegisteredEvent() {
        super();
    }

    /**
     * 向后兼容构造器；新字段通过 setter 注入。
     *
     * @param locationId     位置标识（聚合根 ID）
     * @param name           位置名称
     * @param formEnum       物理形式枚举
     * @param organizationId 所属机构标识
     */
    public LocationRegisteredEvent(Long locationId, String name, String formEnum, Long organizationId) {
        super(locationId, AggregateTypeConstants.LOCATION);
        this.name = name;
        this.formEnum = formEnum;
        this.organizationId = organizationId;
    }
}
