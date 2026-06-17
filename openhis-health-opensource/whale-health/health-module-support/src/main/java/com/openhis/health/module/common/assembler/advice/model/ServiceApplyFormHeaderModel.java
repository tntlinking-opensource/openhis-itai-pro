/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.common.assembler.advice.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 申请单列表读模型：按 {@code prescriptionNo} 分组后的头摘要。
 *
 * <p>由 {@code ServiceApplyFormOrchestrationSupport#listApplyForms} 产出；
 * 应用层经 {@code ServiceApplyFormAssembler} 映射为 HTTP DTO。</p>
 */
@Getter
@Setter
public class ServiceApplyFormHeaderModel {

    /** 申请单号（{@code AR} 前缀，同号下多行共享）。 */
    private String prescriptionNo;

    /** 申请单类别 code（检查 23 / 检验 22 / 治疗 21）。 */
    private String categoryEnum;

    /** 整单执行科室 {@code adm_location.id}。 */
    private Long orgId;

    /** 执行科室展示名。 */
    private String orgName;

    /** 组内首行开立时间，ISO-8601 local 字符串。 */
    private String authoredAt;

    /** 组内状态摘要（如全草稿、已签发、含已签发）。 */
    private String statusSummary;

    /** 本申请单下的 ServiceRequest 行摘要列表。 */
    private List<ServiceApplyFormLineModel> lines;
}
