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

package com.openhis.health.application.inpatient.nursestation.encountermanage.mapper;

import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.NurseStationQueueDisplayEnrichment;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.NurseStationTaskCountBundle;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.ParticipantDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.PatientDetailDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.PatientQueueItemDto;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.PagePatientQueueRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.request.SavePatientInfoRequest;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.LoadAdmissionParticipantInitResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.LoadWardTreeResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.PagePatientQueueResponse;
import com.openhis.health.application.inpatient.nursestation.encountermanage.response.SavePatientInfoResponse;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.module.common.assembler.inpatient.InpatientInfoQueueDisplayAssembler;
import com.openhis.health.domain.administration.encounter.command.SaveNurseStationPatientCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站患者管理装配：Request/读模型/领域命令与 DTO 字段搬运，不承载业务流程判断。
 */
public interface NurseStationEncounterManageMapper {

    /**
     * 病区与病房列表 → 树响应。
     *
     * @param wards           病区位置列表；调用方保证非 null
     * @param housesByWardId  病区 id → 子病房列表
     * @return 树响应
     */
    LoadWardTreeResponse toLoadWardTreeResponse(List<Location> wards, Map<Long, List<Location>> housesByWardId);

    /**
     * 住院医师与责任护士实体列表 → 分床下拉初始化响应。
     *
     * @param doctors 住院医师候选
     * @param nurses  责任护士候选
     * @return 初始化响应
     */
    LoadAdmissionParticipantInitResponse toLoadAdmissionParticipantInitResponse(
        List<Practitioner> doctors,
        List<Practitioner> nurses);

    /**
     * 列表分页请求 → 住院信息读模型分页入参。
     *
     * @param request          HTTP 请求
     * @param managedWardIds   管理病区 id 列表
     * @param encounterIdsIn   任务筛选预收窄就诊集合；可 null
     * @return 读模型分页查询
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(
        PagePatientQueueRequest request,
        List<Long> managedWardIds,
        Collection<Long> encounterIdsIn);

    /**
     * 读模型行 + 账户与任务计数 → 列表卡片 DTO。
     *
     * @param row               住院信息读模型
     * @param counts            任务计数；可 null 时按 0
     * @param accountBalance    展示余额
     * @param lowBalanceWarning 低余额预警
     * @param enrichment        展示增补；可 null
     * @param displayOverlay    队列展示覆盖；可 null
     * @param priorityEnum      病情/优先级 code（就诊主档批量解析）；可 null
     * @param accountId         就诊默认个人现金账户主键；未开立可 null
     * @return 列表行 DTO；row 为 null 时返回 null
     */
    PatientQueueItemDto toPatientQueueItemDto(
        InpatientEncounterContextQueryModel row,
        NurseStationTaskCountBundle counts,
        BigDecimal accountBalance,
        boolean lowBalanceWarning,
        NurseStationQueueDisplayEnrichment enrichment,
        InpatientInfoQueueDisplayAssembler.InpatientQueueDisplayOverlay displayOverlay,
        String priorityEnum,
        Long accountId);

    /**
     * 已装配列表行 + Spring 分页 → 分页响应。
     *
     * @param rows 当前页 DTO
     * @param page 读模型分页
     * @return 分页响应
     */
    PagePatientQueueResponse toPagePatientQueueResponse(
        List<PatientQueueItemDto> rows, Page<InpatientEncounterContextQueryModel> page);

    /**
     * 列表行 + 详情扩展 → 全景详情 DTO。
     *
     * @param base         列表同源字段（含诊断列表/摘要）
     * @param patientPhone 电话
     * @param idCardNo     证件号（已脱敏）
     * @param participants 全角色参与者
     * @return 详情 DTO
     */
    PatientDetailDto toPatientDetailDto(
        PatientQueueItemDto base,
        String patientPhone,
        String idCardNo,
        List<ParticipantDto> participants);

    /**
     * 维护请求 + 操作人 → 领域保存命令。
     *
     * @param request    维护请求
     * @param operatorId 操作人主键
     * @return 领域命令
     */
    SaveNurseStationPatientCommand toSaveNurseStationPatientCommand(SavePatientInfoRequest request, Long operatorId);

    /**
     * 就诊主键 → 维护成功响应。
     *
     * @param encounterId 就诊主键
     * @return 维护响应
     */
    SavePatientInfoResponse toSavePatientInfoResponse(Long encounterId);
}
