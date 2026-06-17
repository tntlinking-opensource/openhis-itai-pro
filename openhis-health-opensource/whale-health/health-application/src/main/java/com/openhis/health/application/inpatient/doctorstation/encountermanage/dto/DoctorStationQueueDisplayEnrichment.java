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

package com.openhis.health.application.inpatient.doctorstation.encountermanage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医生站队列行展示增补（AS-IP-DOR-001 / IF-IP-DOR-01）：读模型未投影字段由 AppService 批量回填。
 *
 * <p>按 encounterId 与列表行一并传入 Mapper，避免在映射器内触发 N+1 查询。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站队列展示增补字段。 */
@Schema(description = "医生站队列展示增补字段")
public class DoctorStationQueueDisplayEnrichment {

    /**
     * 病房名称：由床位 {@code bus_no} 与病区内病房位置最长前缀匹配解析。
     */
    @Schema(description = "病房名称")
    private String houseName;

    /**
     * 患者拼音检索码：来自患者扩展档 {@code pyStr}。
     */
    @Schema(description = "拼音码")
    private String pinyinCode;

    /**
     * 患者五笔检索码：来自患者扩展档 {@code wbStr}。
     */
    @Schema(description = "五笔码")
    private String wubiCode;
}
