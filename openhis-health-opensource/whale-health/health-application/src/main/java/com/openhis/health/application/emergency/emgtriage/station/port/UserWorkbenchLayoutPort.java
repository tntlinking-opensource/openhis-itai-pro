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
 * 工作站布局持久化端口（对接平台用户偏好或专用偏好服务）。
 */
public interface UserWorkbenchLayoutPort {

    /**
     * 保存布局 JSON。
     *
     * @param operatorId      操作者
     * @param layoutSchemaJson 布局 schema（JSON 文本）
     */
    void save(Long operatorId, String layoutSchemaJson);
}
