/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.skintest.mapper;

import com.openhis.health.application.inpatient.nursestation.skintest.dto.NurseStationSkinTestRowDto;
import com.openhis.health.application.inpatient.nursestation.skintest.request.SaveNurseStationSkinTestRecordRequest;
import com.openhis.health.application.inpatient.nursestation.skintest.response.DeleteNurseStationSkinTestRecordResponse;
import com.openhis.health.application.inpatient.nursestation.skintest.response.ListNurseStationSkinTestRowsResponse;
import com.openhis.health.application.inpatient.nursestation.skintest.response.LoadNurseStationSkinTestInitResponse;
import com.openhis.health.application.inpatient.nursestation.skintest.response.SaveNurseStationSkinTestRecordResponse;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;
import com.openhis.whale.core.application.dto.OptionDto;

import java.util.List;

/**
 * 住院皮试 Request/Response 与领域 Command 映射（AS-IP-NUR-011）。
 */
public interface NurseStationSkinTestMapper {

    /**
     * 构造初始化响应外壳。
     *
     * @param response 已填充选项的响应
     * @return 同一响应实例
     */
    LoadNurseStationSkinTestInitResponse toInitResponse(LoadNurseStationSkinTestInitResponse response);

    /**
     * 护士实体列表转下拉选项。
     *
     * @param practitioners 护士实体列表
     * @return 下拉选项
     */
    List<OptionDto> toNurseOptionList(List<Practitioner> practitioners);

    /**
     * 构造列表响应。
     *
     * @param rows 列表行
     * @return 列表响应
     */
    ListNurseStationSkinTestRowsResponse toListResponse(List<NurseStationSkinTestRowDto> rows);

    /**
     * 住院可见性查询入参。
     *
     * @param encounterIds   目标就诊
     * @param managedWardIds 管辖病区
     * @return 住院上下文查询
     */
    InpatientEncounterPageQuery toInpatientEncounterPageQuery(List<Long> encounterIds, List<Long> managedWardIds);

    /**
     * 保存请求转过敏记录写命令。
     *
     * @param request                  保存请求
     * @param skinTestServiceRequestId 皮试检查 SR 主键
     * @return 保存命令
     */
    SaveSkinTestRecordCommand toSaveSkinTestRecordCommand(
        SaveNurseStationSkinTestRecordRequest request,
        Long skinTestServiceRequestId);

    /**
     * 构造保存响应。
     *
     * @param allergyRecordId          过敏记录 id
     * @param skinTestServiceRequestId 皮试检查 SR id
     * @return 保存响应
     */
    SaveNurseStationSkinTestRecordResponse toSaveResponse(
        Long allergyRecordId,
        Long skinTestServiceRequestId);

    /**
     * 删除命令。
     *
     * @param allergyRecordId 过敏记录 id
     * @param operatorId      操作人
     * @return 删除命令
     */
    DeleteSkinTestRecordCommand toDeleteSkinTestRecordCommand(Long allergyRecordId, Long operatorId);

    /**
     * 删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    DeleteNurseStationSkinTestRecordResponse toDeleteResponse(boolean success);
}
