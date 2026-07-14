/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.organization.query;

import lombok.Data;
import com.openhis.whale.core.application.util.StringUtils;

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
        return StringUtils.isNotEmpty(keyword)
            || StringUtils.isNotEmpty(typeEnum)
            || StringUtils.isNotEmpty(classEnum)
            || StringUtils.isNotEmpty(statusEnum);
    }
}
