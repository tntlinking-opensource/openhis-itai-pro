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

package com.openhis.health.application.inpatient.doctorstation.encountermanage.mapper;

import com.openhis.health.application.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.dto.DoctorStationPatientQueueItemDto;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.dto.DoctorStationQueueDisplayEnrichment;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.request.PagePatientQueueRequest;
import com.openhis.health.application.inpatient.doctorstation.encountermanage.response.PagePatientQueueResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

/**
 * 住院医生站就诊管理映射契约（AS-IP-DOR-001，IF-IP-DOR-01）。
 *
 * <p>读模型分页入参与列表卡片 DTO 装配；Gate、批量查询与诊断解析由
 * {@link com.openhis.health.application.inpatient.doctorstation.encountermanage.service.InpatientDoctorStationEncounterManageAppService} 承担。</p>
 */
public interface InpatientDoctorStationEncounterManageMapper {

    /**
     * 列表分页请求 → 住院信息读模型分页入参（医生站队列 Q-IPI-006）。
     *
     * @param request HTTP 请求
     * @return 住院就诊上下文分页查询；科室收窄由领域读侧 {@link ICurrentUser} 承担（B.10.11）
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(PagePatientQueueRequest request);

    /**
     * 读模型行 + 账户与展示增补 → 医生站列表卡片 DTO。
     *
     * @param row               住院信息读模型
     * @param accountBalance    展示余额
     * @param lowBalanceWarning 低余额预警
     * @param displayOverlay    队列展示覆盖；可 null
     * @param priorityEnum      病情/优先级 code；可 null
     * @param diagnosisList     诊断展示列表
     * @param diagnosisSummary  诊断一行摘要
     * @param enrichment        展示增补；可 null
     * @return 列表行 DTO；row 为 null 时返回 null
     */
    DoctorStationPatientQueueItemDto toDoctorStationPatientQueueItemDto(
        InpatientEncounterContextQueryModel row,
        BigDecimal accountBalance,
        boolean lowBalanceWarning,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
        String priorityEnum,
        List<String> diagnosisList,
        String diagnosisSummary,
        DoctorStationQueueDisplayEnrichment enrichment);

    /**
     * 已装配列表行 + Spring 分页 → 分页响应。
     *
     * @param rows 当前页 DTO
     * @param page 读模型分页
     * @return 分页响应
     */
    PagePatientQueueResponse toPagePatientQueueResponse(
        List<DoctorStationPatientQueueItemDto> rows,
        Page<InpatientEncounterContextQueryModel> page);
}
