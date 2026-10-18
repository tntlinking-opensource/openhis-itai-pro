/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.diagnosis.request;

import com.zxplat.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-04：分页检索已发布诊断定义请求（AS-IP-DOR-002）。
 *
 * <p>分页字段继承 {@link AbstractHealthPageRequest}（B.10.11，{@code pageNo} 为 1-based）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "医生站-诊断定义分页检索请求")
public class PageConditionDefinitionRequest extends AbstractHealthPageRequest {

    /**
     * 诊断中西医维度筛选：{@code 1} 西医、{@code 2} 中医；空白表示不按目录来源收敛。
     */
    @Schema(description = "诊断中西医维度筛选：1=西医，2=中医；不传则不过滤来源")
    private String diagnosisKindCode;
}
