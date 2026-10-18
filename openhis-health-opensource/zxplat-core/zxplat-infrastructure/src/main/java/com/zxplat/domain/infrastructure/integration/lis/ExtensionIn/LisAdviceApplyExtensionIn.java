/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.lis.ExtensionIn;

import com.zxplat.domain.infrastructure.integration.lis.dto.LisAdviceApplyDetailLine;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 检验外发命令（IF-DOR-15 / IF-IP-DOR-15）。
 *
 * <p>职责：医嘱提交后由 health 组装，LIS adapter 按明细行外发检验申请。</p>
 */
@Getter
@Setter
public class LisAdviceApplyExtensionIn {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 医嘱申请单主键（ServiceRequest 等）。 */
    private Long requestId;

    /** 外发明细行（检验项目/标本等）。 */
    private List<LisAdviceApplyDetailLine> details;
}
