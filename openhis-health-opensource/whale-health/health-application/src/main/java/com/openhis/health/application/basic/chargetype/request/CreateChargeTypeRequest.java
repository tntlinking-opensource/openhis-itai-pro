/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargetype.request;

import lombok.Data;

/**
 * 新增费用性质（收费类型）请求体：映射为财务域「合约」创建命令。
 */
@Data
public class CreateChargeTypeRequest {

    /** 业务编码（对外展示/检索用，与领域 busNo 对齐）。 */
    private String busNo;

    /** 费用性质名称。 */
    private String name;

    /** 列表/下拉中的显示顺序，非负整数。 */
    private Integer displayOrder;

    /**
     * 费用大类业务码（如 1 自费、2 公费、3 省医保等），与初始化下拉 {@code code} 一致。
     */
    private String categoryEnum;
}
