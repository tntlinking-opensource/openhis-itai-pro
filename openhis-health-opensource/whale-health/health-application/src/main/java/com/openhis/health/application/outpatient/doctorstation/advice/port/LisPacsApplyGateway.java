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

package com.openhis.health.application.outpatient.doctorstation.advice.port;

/**
 * IF-DOR-15：检验/检查外发网关（严格/忽略模式由租户配置驱动，实现侧捕获异常）。
 */
public interface LisPacsApplyGateway {

    /**
     * 若目标为检验/检查类诊疗则提交外发，否则空操作。
     *
     * @param command 外发上下文
     */
    void submitIfLabOrImaging(LisPacsAdviceApplyCommand command);
}
