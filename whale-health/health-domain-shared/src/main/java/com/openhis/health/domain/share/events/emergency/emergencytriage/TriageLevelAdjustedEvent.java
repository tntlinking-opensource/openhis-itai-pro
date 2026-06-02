/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.emergency.emergencytriage;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 分诊级别已调整事件。分诊级别因病情变化调整时发布。
 */
@Getter
@Setter
public class TriageLevelAdjustedEvent extends DomainEvent {
    /** 原级别枚举值。 */
    private String oldLevelEnum;
    /** 新级别枚举值。 */
    private String newLevelEnum;
    /** 新区域枚举值。 */
    private String newAreaEnum;
    /** 新分诊级别编码（字典 code）。 */
    private String triageLevelCode;
    /** 新分诊级别名称快照。 */
    private String triageLevelName;
    /** 新分诊区域编码（字典 code）。 */
    private String triageAreaCode;
    /** 新分诊区域名称快照。 */
    private String triageAreaName;
    /** 变更原因。 */
    private String reason;
    /** 变更记录 JSON（追溯用）。 */
    private String changeRecordsJson;

    /** 无参构造器（反序列化用）。 */
    public TriageLevelAdjustedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；文本标签字段通过 setter 注入。
     *
     * @param triageId     急诊分诊标识（聚合根 ID）
     * @param oldLevelEnum 原级别枚举值
     * @param newLevelEnum 新级别枚举值
     * @param newAreaEnum  新区域枚举值
     * @param reason       变更原因
     */
    public TriageLevelAdjustedEvent(Long triageId, String oldLevelEnum, String newLevelEnum,
                                    String newAreaEnum, String reason) {
        super(triageId, AggregateTypeConstants.EMERGENCY_TRIAGE);
        this.oldLevelEnum = oldLevelEnum;
        this.newLevelEnum = newLevelEnum;
        this.newAreaEnum = newAreaEnum;
        this.reason = reason;
    }
}
