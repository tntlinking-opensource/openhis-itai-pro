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
 * 工作站布局持久化占位实现（GAP-EMG-AS-04：偏好聚合未闭合前显式失败）。
 */
@Component
public class UnsupportedUserWorkbenchLayoutPort implements UserWorkbenchLayoutPort {

    /**
     * 保存布局 JSON（占位：偏好服务尚未接入时抛出业务异常）。
     *
     * @param operatorId       操作者
     * @param layoutSchemaJson 布局 schema（JSON 文本）
     */
    @Override
    public void save(Long operatorId, String layoutSchemaJson) {
        throw new BusinessException(ErrorCodeConstants.EMERGENCY_TRIAGE_INVALID_ARGUMENT, "工作站布局持久化尚未接通平台偏好服务");
    }
}
