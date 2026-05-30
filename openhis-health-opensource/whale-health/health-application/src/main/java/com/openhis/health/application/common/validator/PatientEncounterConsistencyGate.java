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

package com.openhis.health.application.common.validator;

import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 请求上下文患者与就诊读模型患者一致性门控（B.6.1 {@code common.validator}）。
 *
 * <p>仅校验两侧患者主键非空且相等；错误码与文案由调用方按 AS 契约传入，避免各 Mapper 复制同构判断。</p>
 */
@Component
public class PatientEncounterConsistencyGate {

    /**
     * 要求请求携带的患者与就诊侧患者主键一致。
     *
     * @param requestPatientId   请求/卡片上下文中的患者主键
     * @param encounterPatientId 就诊读模型或快照中的患者主键
     * @param errorCode          不一致或缺失时的业务错误码
     * @param message            不一致或缺失时的用户提示
     * @throws BusinessException 任一侧为空或不相等时
     */
    public void requireRequestPatientMatchesEncounter(
        Long requestPatientId,
        Long encounterPatientId,
        String errorCode,
        String message) {
        if (requestPatientId == null || !requestPatientId.equals(encounterPatientId)) {
            throw new BusinessException(errorCode, message);
        }
    }
}
