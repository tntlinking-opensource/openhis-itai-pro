/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.observationdefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 临床观察定义已保存事件（新建或更新后发布，DM-CLN-005）。
 */
@Getter
@Setter
public class ObservationDefinitionSavedEvent extends DomainEvent {

    /** 院内观察项编码。 */
    private String code;

    /** 分类编码（exam_item / dept_conclusion 等）。 */
    private String categoryCode;

    /** 发布状态（active / inactive）。 */
    private String statusEnum;

    /**
     * 构造已保存事件。
     *
     * @param id           观察定义主键
     * @param code         院内码
     * @param categoryCode 分类码
     * @param statusEnum   状态码
     */
    public ObservationDefinitionSavedEvent(Long id, String code, String categoryCode, String statusEnum) {
        super(id, AggregateTypeConstants.OBSERVATION_DEFINITION);
        this.code = code;
        this.categoryCode = categoryCode;
        this.statusEnum = statusEnum;
    }
}
