/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.support;

import lombok.Getter;
import lombok.Setter;

/**
 * 申请单保存入参：明细目标行（部位/样本引用）。
 *
 * <p>由应用层 {@code ServiceApplyFormAssembler} 从 HTTP DTO 映射；经
 * {@code ServiceApplyFormOrchestrator} 校验白名单后写入
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
