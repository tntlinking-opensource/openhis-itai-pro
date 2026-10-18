/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.activitydefinition;

import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗子项解析结果（对齐 {@code wkf_activity_definition_children} + 子诊疗目录读模型）。
 *
 * <p>供医保上传、LIS 对接、报表等消费方按需使用；**不**绑定医保字段。</p>
 */
@Getter
@Setter
public class ActivityDefinitionPackageChildResolvedDto {

    /** 子项诊疗定义主键（children_id）。 */
    private Long childrenId;

    /** 子项数量倍率（children_num；空时按 1）。 */
    private Integer childrenNum;

    /** 子项名称快照（children_name）。 */
    private String childrenName;

    /** 子诊疗目录读模型（批量加载；未命中可为 null）。 */
    private ProductDefinitionDisplayDto childProduct;
}
