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

package com.openhis.health.application.emergency.patientmanagement.port;

/**
 * 腕带物理打印端口；未对接打印中间件前由占位实现返回成功。
 */
public interface WristbandPrintPort {

    /**
     * 提交打印任务。
     *
     * @param command 打印上下文
     * @return 是否打印成功（仅成功时回写业务打印时间）
     */
    boolean print(WristbandPrintCommand command);
}
