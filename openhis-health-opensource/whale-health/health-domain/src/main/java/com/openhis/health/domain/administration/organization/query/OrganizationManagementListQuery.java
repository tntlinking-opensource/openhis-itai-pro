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

package com.openhis.health.domain.administration.organization.query;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * 科室管理页机构列表查询条件。
 *
 * <p>类型/类别入参支持领域 {@code code} 或旧版 sortOrder 数字字符串，与维护页下拉一致。</p>
 */
@Data
public class OrganizationManagementListQuery {

    /** 关键字：名称、医保信息、拼音、五笔等模糊匹配。 */
    private String keyword;

    /** 机构层级类型（{@link com.openhis.health.domain.share.enums.OrganizationType} code 或 sortOrder）。 */
    private String typeEnum;

    /** 机构业务分类（{@link com.openhis.health.domain.share.enums.OrganizationClassification} code 或 sortOrder）。 */
    private String classEnum;

    /** 发布状态（{@link com.openhis.health.domain.share.enums.PublicationStatus#getCode()}）。 */
    private String statusEnum;

    /**
     * 是否携带任一有效筛选条件。
     *
     * @return 有筛选时为 true
     */
    public boolean hasAnyFilter() {
        return StringUtils.isNotBlank(keyword)
            || StringUtils.isNotBlank(typeEnum)
            || StringUtils.isNotBlank(classEnum)
            || StringUtils.isNotBlank(statusEnum);
    }
}
