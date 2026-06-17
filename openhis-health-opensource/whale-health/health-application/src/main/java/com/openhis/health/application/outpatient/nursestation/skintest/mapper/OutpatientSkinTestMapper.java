/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.nursestation.skintest.mapper;

import com.openhis.health.application.outpatient.nursestation.skintest.request.SaveOutpatientSkinTestRecordRequest;
import com.openhis.health.application.outpatient.nursestation.skintest.response.DeleteOutpatientSkinTestRecordResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.ListOutpatientSkinTestRowsResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.LoadOutpatientSkinTestInitResponse;
import com.openhis.health.application.outpatient.nursestation.skintest.response.SaveOutpatientSkinTestRecordResponse;
import com.openhis.health.domain.clinical.allergyintolerance.command.DeleteSkinTestRecordCommand;
import com.openhis.health.domain.clinical.allergyintolerance.command.SaveSkinTestRecordCommand;

import java.util.List;

/**
 * 门诊皮试 Request/Response 与领域 Command 映射。
 */
public interface OutpatientSkinTestMapper {

    /**
     * 构造初始化响应外壳。
     *
     * @param response 已填充选项的响应
     * @return 同一响应实例
     */
    LoadOutpatientSkinTestInitResponse toInitResponse(LoadOutpatientSkinTestInitResponse response);

    /**
     * 构造列表响应。
     *
     * @param rows 列表行
     * @return 列表响应
     */
    ListOutpatientSkinTestRowsResponse toListResponse(List<com.openhis.health.application.outpatient.nursestation.skintest.dto.OutpatientSkinTestRowDto> rows);

    /**
     * 保存请求转领域命令。
     *
     * @param request 保存请求
     * @param skinTestServiceRequestId 皮试检查 SR 主键
     * @return 保存命令
     */
    SaveSkinTestRecordCommand toSaveSkinTestRecordCommand(
        SaveOutpatientSkinTestRecordRequest request,
        Long skinTestServiceRequestId);

    /**
     * 构造保存响应。
     *
     * @param allergyRecordId 过敏记录主键
     * @param skinTestServiceRequestId 皮试检查 SR 主键
     * @return 保存响应
     */
    SaveOutpatientSkinTestRecordResponse toSaveResponse(Long allergyRecordId, Long skinTestServiceRequestId);

    /**
     * 删除命令。
     *
     * @param allergyRecordId 过敏记录主键
     * @param operatorId 操作人
     * @return 删除命令
     */
    DeleteSkinTestRecordCommand toDeleteSkinTestRecordCommand(Long allergyRecordId, Long operatorId);

    /**
     * 删除响应。
     *
     * @param success 是否成功
     * @return 删除响应
     */
    DeleteOutpatientSkinTestRecordResponse toDeleteResponse(boolean success);
}
