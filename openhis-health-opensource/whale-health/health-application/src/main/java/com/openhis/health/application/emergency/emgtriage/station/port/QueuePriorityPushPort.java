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

/**
 * 院内队列优先级推送端口（叫号子系统适配）。
 */
public interface QueuePriorityPushPort {

    /**
     * 推送队列排序要素。
     *
     * @param emgTriageId   分诊主键
     * @param encounterId   就诊标识
     * @param queuePriority 队列优先级（数值越小越优先）
     */
    void push(Long emgTriageId, Long encounterId, Integer queuePriority);
}
