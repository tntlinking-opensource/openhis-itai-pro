/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.extension.peis;

import lombok.Getter;
import lombok.Setter;

/**
 * 合同类型字典视图。
 */
@Getter
@Setter
public class PeisContractTypeView {

    /** 类型编码。 */
    private String typeCode;

    /** 显示名称。 */
    private String typeName;

    /** 排序号。 */
    private Integer sortOrder;
}
