/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.pacs.ExtensionIn;

import com.zxplat.domain.infrastructure.integration.pacs.dto.PacsAdviceApplyDetailLine;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 检查外发命令（IF-DOR-15 / IF-IP-DOR-15）。
 *
 * <p>职责：医嘱提交后由 health 组装，PACS/RIS adapter 按明细行外发检查申请。</p>
 */
@Getter
@Setter
public class PacsAdviceApplyExtensionIn {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 医嘱申请单主键。 */
    private Long requestId;

    /** 外发明细行（检查项目等）。 */
    private List<PacsAdviceApplyDetailLine> details;
}
