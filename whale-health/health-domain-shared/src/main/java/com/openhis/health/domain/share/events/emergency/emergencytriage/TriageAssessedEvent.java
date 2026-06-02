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
 * 分诊已评估事件。分诊评估完成、级别和区域确定时发布。
 */
@Getter
@Setter
public class TriageAssessedEvent extends DomainEvent {
    /** 分诊级别枚举值。 */
    private String triageLevelEnum;
    /** 分诊区域枚举值。 */
    private String triageAreaEnum;
    /** 分诊级别编码（字典 code）。 */
    private String triageLevelCode;
    /** 分诊级别名称快照。 */
    private String triageLevelName;
    /** 分诊区域编码（字典 code）。 */
    private String triageAreaCode;
    /** 分诊区域名称快照。 */
    private String triageAreaName;
    /** 主诉。 */
    private String chiefComplaint;
    /** 症状描述。 */
    private String symptom;
    /** 疾病类型 JSON 快照。 */
    private String diseaseTypeJson;
    /** 评估结果 JSON 快照。 */
    private String assessmentsJson;
    /** 是否启用绿色通道。 */
    private Boolean greenChannelFlag;

    /** 无参构造器（反序列化用）。 */
    public TriageAssessedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；文本标签字段通过 setter 注入。
     *
     * @param triageId        急诊分诊标识（聚合根 ID）
     * @param triageLevelEnum 分诊级别枚举值
     * @param triageAreaEnum  分诊区域枚举值
     * @param chiefComplaint  主诉
     * @param greenChannelFlag 绿色通道标记
     */
    public TriageAssessedEvent(Long triageId, String triageLevelEnum, String triageAreaEnum,
                               String chiefComplaint, Boolean greenChannelFlag) {
        super(triageId, AggregateTypeConstants.EMERGENCY_TRIAGE);
        this.triageLevelEnum = triageLevelEnum;
        this.triageAreaEnum = triageAreaEnum;
        this.chiefComplaint = chiefComplaint;
        this.greenChannelFlag = greenChannelFlag;
    }
}
