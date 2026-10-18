/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-DOR-07：分页检索有效诊断定义请求。分页与检索见 {@link AbstractPageQuery}（B.10.11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "医生站-诊断定义分页检索请求")
public class PageConditionDefinitionRequest extends AbstractHealthPageRequest {

    @Schema(description = "诊断中西医维度筛选：1=西医，2=中医；不传则不过滤来源")
    /** 诊断中西医维度筛选：1=西医，2=中医；不传则不过滤来源。 */
    private String diagnosisKindCode;
}
