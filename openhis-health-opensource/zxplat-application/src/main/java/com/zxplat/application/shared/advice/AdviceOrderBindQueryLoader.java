/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.advice;


import java.util.List;

/**
 * 医嘱绑定模板只读查询（IF-IP-DOR-12），供 querysupadapter 读路径装配。
 */
public interface AdviceOrderBindQueryLoader {

    /**
     * 按源项键查询医嘱绑定模板行（用法→耗材或诊疗套餐子项）。
     *
     * @param bindType           绑定类型 code
     * @param sourceItemKey      源项键（用法 code 或活动定义复合键）
     * @param organizationUnitId 机构主键；全院模板场景可空
     * @return 绑定明细行；无命中时返回空列表
     */
    List<AdviceOrderBindRowDto> listBindingsBySourceItemKey(
        String bindType,
        String sourceItemKey,
        Long organizationUnitId);
}
