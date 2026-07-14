/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.encountermanage.mapper;

import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.CompleteOutpatientEncounterRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.MarkOutpatientLeaveRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.PageOutpatientInfoForDoctorStationRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.request.StartOutpatientReceptionRequest;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.CompleteOutpatientEncounterResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.MarkOutpatientLeaveResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.PageOutpatientInfoForDoctorStationResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.response.StartOutpatientReceptionResponse;
import com.openhis.health.application.outpatient.doctorstation.encountermanage.dto.OutpatientInfoItemDto;
import com.openhis.health.domain.administration.encounter.command.CompleteOutpatientEncounterCommand;
import com.openhis.health.domain.administration.encounter.command.MarkOutpatientLeaveCommand;
import com.openhis.health.domain.administration.encounter.command.StartOutpatientReceptionCommand;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterListFinancialTotals;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * 医生站就诊管理应用层映射器：负责 Request↔领域命令、读模型↔展示 DTO 的纯字段搬运。
 * <p>禁止承载业务流程判断与不变式校验（门控在 AppService）。</p>
 */
public interface DoctorStationEncounterManageMapper {

    /**
     * 将医生站列表请求映射为读模型分页查询入参（IF-DOR-01 Pre）。
     *
     * @param request 应用请求（调用方保证非空）；{@code encounterStatus} 为空时映射为已挂号/在诊/暂离/诊毕多值 IN 条件
     * @return 读模型查询 DTO；字段与 Q-OPI-001 对齐
     */
    OutpatientEncounterPageQuery toOutpatientEncounterPageQuery(PageOutpatientInfoForDoctorStationRequest request);

    /**
     * 将读模型分页结果装配为医生站列表响应（IF-DOR-01 Asm）。
     *
     * @param page 读模型分页（允许空页，不允 null Page 引用）
     * @return 列表响应；records 为空列表时表示无数据
     */
    PageOutpatientInfoForDoctorStationResponse toPageOutpatientInfoForDoctorStationResponse(
        Page<OutpatientEncounterContextQueryModel> page);

    /**
     * 将读模型分页与「就诊维度收费汇总」装配为医生站列表响应（IF-DOR-01 Asm 扩展）。
     * <p>当 {@code chargeTotalsByEncounterId} 非空且命中行上就诊主键时，以汇总对象覆盖
     * {@link OutpatientInfoItemDto} 的 {@code totalAmount}/{@code paidAmount}/{@code refundAmount}；
     * 否则保留读模型投影值（兼容仅读库、未做明细汇总的调用方）。</p>
     *
     * @param page                        读模型分页（允许空页，不允许 {@code null} 引用）
     * @param chargeTotalsByEncounterId  就诊主键 → 收费明细内存汇总；允许为 {@code null} 表示不覆盖金额列
     * @return 列表响应；{@code records} 为空列表表示当前页无数据
     */
    PageOutpatientInfoForDoctorStationResponse toPageOutpatientInfoForDoctorStationResponse(
        Page<OutpatientEncounterContextQueryModel> page,
        Map<Long, OutpatientEncounterListFinancialTotals> chargeTotalsByEncounterId);

    /**
     * 读模型行 → 列表行 DTO。
     *
     * @param row 读模型行；null 时返回 null
     * @return 列表 DTO 或 null
     */
    OutpatientInfoItemDto toOutpatientInfoItemDto(OutpatientEncounterContextQueryModel row);

    /**
     * 接诊请求 → 领域命令（IF-DOR-02 Cmd 入参）。
     *
     * @param request 请求（非空）
     * @param doctorDisplayName 接诊医师展示名（应用层已解析主数据）
     * @return 接诊命令（单 item）
     */
    StartOutpatientReceptionCommand toStartOutpatientReceptionCommand(
        StartOutpatientReceptionRequest request,
        String doctorDisplayName
    );

    /**
     * 暂离请求 → 领域命令（IF-DOR-03）。
     *
     * @param request 请求（非空）
     * @return 暂离命令
     */
    MarkOutpatientLeaveCommand toMarkOutpatientLeaveCommand(MarkOutpatientLeaveRequest request);

    /**
     * 完诊请求 → 领域命令（IF-DOR-04）。
     *
     * @param request 请求（非空）
     * @return 完诊命令
     */
    CompleteOutpatientEncounterCommand toCompleteOutpatientEncounterCommand(CompleteOutpatientEncounterRequest request);

    /**
     * 接诊结果装配（IF-DOR-02 Asm）。
     *
     * @param encounterId 就诊主键
     * @param success       是否成功
     * @return 响应体
     */
    StartOutpatientReceptionResponse toStartOutpatientReceptionResponse(Long encounterId, boolean success);

    /**
     * 暂离结果装配（IF-DOR-03 Asm）。
     *
     * @param encounterId 就诊主键
     * @param success     是否成功
     * @return 响应体
     */
    MarkOutpatientLeaveResponse toMarkOutpatientLeaveResponse(Long encounterId, boolean success);

    /**
     * 完诊结果装配（IF-DOR-04 Asm）。
     *
     * @param encounterId 就诊主键
     * @param success     是否成功
     * @return 响应体
     */
    CompleteOutpatientEncounterResponse toCompleteOutpatientEncounterResponse(Long encounterId, boolean success);
}
