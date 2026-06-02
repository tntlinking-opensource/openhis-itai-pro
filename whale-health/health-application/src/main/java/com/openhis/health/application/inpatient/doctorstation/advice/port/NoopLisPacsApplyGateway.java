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

package com.openhis.health.application.inpatient.doctorstation.advice.port;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 住院默认可用外发网关：记录日志占位，供未对接 LIS/PACS 环境编译与联调前运行。
 *
 * <p>实现 {@link LisPacsApplyGateway}；与门诊占位实现分 Bean、分包，禁止跨包引用。</p>
 */
@Slf4j
@Component("inpatientNoopLisPacsApplyGateway")
public class NoopLisPacsApplyGateway implements LisPacsApplyGateway {

    /**
     * 若目标为检验/检查类诊疗则提交外发，否则空操作；本实现不调用外部系统，仅打日志占位。
     *
     * @param command 外发上下文；{@code null} 时直接返回
     */
    @Override
    public void submitIfLabOrImaging(LisPacsAdviceApplyCommand command) {
        if (command == null) {
            return;
        }
        log.info("LIS/PACS 外发占位（住院）：encounterId={} patientId={}", command.getEncounterId(), command.getPatientId());
    }
}
