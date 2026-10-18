/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import java.util.Collection;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退费撤销：软删申请退费时 {@code reopenMedicationOrder} 产生的草稿（基于原单 based_on）。
 */
@Getter
@Setter
public class VoidReopenedMedReqDraftCommand {

    /** 原正向药品申请主键集合（based_on 指向方）。 */
    private Collection<Long> originRequestIds;

    /** 操作人标识（写入删除审计）。 */
    private Long operatorId;
}
