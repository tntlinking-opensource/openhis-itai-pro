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

package com.openhis.health.domain.share.events.clinical.condition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊诊断已保存事件。
 */
@Getter
@Setter
/**
 * EncounterDiagnosisSavedEvent。
 */
public class EncounterDiagnosisSavedEvent extends DomainEvent {
    /**
     * patientId。
     */
    private Long patientId;
    /**
     * definitionId。
     */
    private Long definitionId;
    /**
     * verificationStatusEnum。
     */
    private String verificationStatusEnum;
    /**
     * clinicalStatusEnum。
     */
    private String clinicalStatusEnum;

    public EncounterDiagnosisSavedEvent(Long conditionId, Long patientId, Long definitionId,
                                        String verificationStatusEnum, String clinicalStatusEnum) {
        super(conditionId, AggregateTypeConstants.CONDITION);
        this.patientId = patientId;
        this.definitionId = definitionId;
        this.verificationStatusEnum = verificationStatusEnum;
        this.clinicalStatusEnum = clinicalStatusEnum;
    }
}
