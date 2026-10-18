/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.contract.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同（结算协议）详情查询视图模型。
 * <p>
 * 用于读侧展示单条合同的编号、名称、类别、状态及医保标识等明细字段。
 */
@Getter
@Setter
public class ContractDetailQueryModel {
    /** 合同主键。 */
    private Long id;
    /** 业务编号或合同编号。 */
    private String busNo;
    /** 合同名称。 */
    private String name;
    /** 合同类别枚举编码。 */
    private String categoryEnum;
    /** 合同状态枚举编码。 */
    private String statusEnum;
    /** 是否启用。 */
    private Boolean activeFlag;
    /** 列表或下拉中的显示排序号。 */
    private Integer displayOrder;
    /** 是否医保相关合同/协议。 */
    private Boolean ybFlag;
}
