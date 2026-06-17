/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.advice;

import com.openhis.health.module.querysupport.advice.dto.AdviceOrderBindRowDto;

import java.util.List;

/**
 * 医嘱绑定模板只读查询端口（IF-IP-DOR-12），供 querysupport 读路径装配。
 */
public interface AdviceOrderBindQueryPort {

    List<AdviceOrderBindRowDto> listBindingsBySourceItemKey(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId);
}
