/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.contract.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同（结算协议）列表项查询视图模型。
 * <p>
 * 用于列表、下拉等轻量展示，不含详情页扩展字段。
 */
@Getter
@Setter
public class ContractListItemQueryModel {
    /** 合同主键。 */
    private Long id;
    /** 业务编号或合同编号。 */
    private String busNo;
    /** 合同名称。 */
    private String name;
    /** 合同类别枚举编码。 */
    private String categoryEnum;
    /** 是否启用。 */
    private Boolean activeFlag;
    /** 显示排序号。 */
    private Integer displayOrder;
    /**
     * 就医地/医保通道区划（{@code fin_contract.adm_vs}）。
     * <p>挂号页费用性质匹配与通道概要装配用，避免再按 id 回表实体。</p>
     */
    private String admVs;
    /**
     * 是否医保类费用性质（{@code fin_contract.yb_flag}）。
     */
    private Boolean ybFlag;
}
