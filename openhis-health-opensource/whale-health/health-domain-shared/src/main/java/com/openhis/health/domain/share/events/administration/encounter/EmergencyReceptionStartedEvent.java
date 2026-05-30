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

package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 急诊接诊开始事件：携带接诊医师标识与展示名，供挂号列表与门诊信息读模型等与门诊接诊对称的投影。
 */
@Getter
@Setter
public class EmergencyReceptionStartedEvent extends DomainEvent {

    /** 接诊医师主键。 */
    private Long doctorId;
    /** 接诊医师姓名（展示快照）。 */
    private String doctorName;
    /** 接诊发生时刻。 */
    private LocalDateTime receptionTime;
    /** 是否抢救中。 */
    private Boolean rescueFlag;
    /** 接诊后就诊状态枚举 code（与 {@code Encounter.statusEnum} 一致）。 */
    private String statusEnum;

    /**
     * 反序列化用无参构造。
     */
    public EmergencyReceptionStartedEvent() {
        super();
    }

    /**
     * 常用构造：标识、医师、时刻与抢救标识；姓名可通过 {@link #setDoctorName(String)} 在发布前补全。
     *
     * @param encounterId   就诊标识
     * @param doctorId      接诊医师主键
     * @param receptionTime 接诊时刻
     * @param rescueFlag    是否抢救中
     */
    public EmergencyReceptionStartedEvent(Long encounterId, Long doctorId, LocalDateTime receptionTime, Boolean rescueFlag) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.doctorId = doctorId;
        this.receptionTime = receptionTime;
        this.rescueFlag = rescueFlag;
    }
}
