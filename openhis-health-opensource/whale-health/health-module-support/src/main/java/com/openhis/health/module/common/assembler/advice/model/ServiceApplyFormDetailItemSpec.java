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

/**
 * 申请单保存入参：明细目标行（部位/样本引用）。
 *
 * <p>由应用层 {@code ServiceApplyFormAssembler} 从 HTTP DTO 映射；经
 * {@code ServiceApplyFormOrchestrationSupport} 校验白名单后写入
 * {@code CreateServiceRequestDraftCommand.DetailItemCommand}。</p>
 */
@Getter
@Setter
public class ServiceApplyFormDetailItemSpec {

    /** 目标表名；仅允许 {@code dia_body_structure} 或 {@code adm_specimen_definition}。 */
    private String targetTable;

    /** 目标主数据主键（部位 id 或样本定义 id）。 */
    private Long targetId;

    /** 与该目标同行的补充说明；纯备注须写主表 {@code remark}，禁止仅 Detail 承载。 */
    private String remark;
}
