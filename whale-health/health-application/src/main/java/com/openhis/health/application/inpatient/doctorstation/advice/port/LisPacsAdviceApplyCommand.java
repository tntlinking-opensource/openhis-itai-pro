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

package com.openhis.health.application.inpatient.doctorstation.advice.port;

import lombok.Getter;
import lombok.Setter;

/**
 * IF-IP-DOR-15：检验/检查外发命令（目标态网关入参壳）。
 *
 * <p>当前仅携带就诊/患者锚点供占位网关与后续对接协议扩展；诊疗申请 id、活动类型等字段
 * 将在 LIS/PACS 集成落地时按租户协议补齐。</p>
 */
@Getter
@Setter
public class LisPacsAdviceApplyCommand {

    /** 就诊主键：外发申请归属的住院就诊。 */
    private Long encounterId;

    /** 患者主键：与就诊卡片、门控校验一致。 */
    private Long patientId;
}
