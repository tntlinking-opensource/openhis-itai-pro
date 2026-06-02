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

package com.openhis.health.application.emergency.emgtriage.station.port;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 队列优先级推送占位实现。
 */
@Component
public class UnsupportedQueuePriorityPushPort implements QueuePriorityPushPort {

    /**
     * 推送队列排序要素（占位：院内队列适配尚未接入时抛出业务异常）。
     *
     * @param emgTriageId   分诊主键
     * @param encounterId   就诊标识
     * @param queuePriority 队列优先级（数值越小越优先）
     */
    @Override
    public void push(Long emgTriageId, Long encounterId, Integer queuePriority) {
        throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "院内队列适配尚未接入");
    }
}
