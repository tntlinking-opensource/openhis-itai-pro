/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicedevicebinding.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 诊疗耗材绑定分页查询条件：按范围、绑定类型、来源项与状态过滤；分页见 B.10.11。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageServiceDeviceBindingRequest extends AbstractHealthPageRequest {

    /** 绑定范围业务码；空串由应用服务默认全院范围。 */
    private String rangeCode;

    /** 绑定类型码：USAGE / ACTIVITY。 */
    private String bindType;

    /** 来源项主键（字符串形式，与领域 itemId 一致）；空表示不按来源过滤。 */
    private String sourceId;

    /** 发布状态筛选：ACTIVE / INACTIVE；空表示不按状态过滤。 */
    private String statusEnum;

    /**
     * 默认首页、每页 50 条。
     */
    public PageServiceDeviceBindingRequest() {
        setPageNo(1);
        setPageSize(50);
    }
}
