/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.mapper;

import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewActiveDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewExportRowDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewListRowDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.BatchCreateReviewsRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.ExportReviewsRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.PageReviewsRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.RevokeReviewRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.BatchCreateReviewsResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.CreateAutoReviewAfterDispenseResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.ExportReviewsResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.LoadPageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.MapActiveReviewsByPrescriptionNosResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.PageReviewsResponse;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewItemCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewsCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.RevokePrescriptionReviewCommand;
import com.openhis.health.domain.custom.prescriptionreview.queries.PrescriptionReviewPageQuery;
import com.openhis.health.domain.custom.prescriptionreview.queries.model.PrescriptionReviewQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 门诊处方点评 Mapper：Request/Command/Response/DTO 字段搬运；禁止承载业务流程判断。
 */
public interface OutpatientPharmacyPrescriptionReviewMapper {

    /**
     * 装配页初始化响应。
     *
     * @param statusOptions     点评状态下拉；调用方保证非空
     * @param reasonableOptions 是否合理下拉；调用方保证非空
     * @param defaultFrom       默认点评日起
     * @param defaultTo         默认点评日止
     * @return 初始化响应
     */
    LoadPageInitResponse toLoadPageInitResponse(
        List<OptionDto> statusOptions,
        List<OptionDto> reasonableOptions,
        String defaultFrom,
        String defaultTo);

    /**
     * 分页查询请求 → 领域分页筛选（日期转 LocalDateTime 闭区间）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域查询入参
     */
    PrescriptionReviewPageQuery toPageQuery(PageReviewsRequest request);

    /**
     * 导出请求 → 领域分页筛选（与列表同源）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域查询入参
     */
    PrescriptionReviewPageQuery toPageQuery(ExportReviewsRequest request);

    /**
     * 读模型 → 列表行（含点评人姓名）。
     *
     * @param model        读模型；{@code null} 时返回 {@code null}
     * @param reviewerName 点评人姓名；可空
     * @return 列表行 DTO
     */
    PrescriptionReviewListRowDto toListRowDto(PrescriptionReviewQueryModel model, String reviewerName);

    /**
     * 装配分页响应。
     *
     * @param page 领域分页结果；调用方保证非空
     * @param rows 已映射列表行
     * @return 分页响应
     */
    PageReviewsResponse toPageResponse(Page<PrescriptionReviewQueryModel> page, List<PrescriptionReviewListRowDto> rows);

    /**
     * 列表行 → 导出行。
     *
     * @param row 列表行；{@code null} 时返回 {@code null}
     * @return 导出行 DTO
     */
    PrescriptionReviewExportRowDto toExportRowDto(PrescriptionReviewListRowDto row);

    /**
     * 装配导出响应。
     *
     * @param body      xlsx 字节
     * @param rowCount  导出行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    ExportReviewsResponse toExportResponse(byte[] body, int rowCount, boolean truncated);

    /**
     * 批量创建请求 → 领域批量命令。
     *
     * @param request    批量请求；调用方保证非空
     * @param reviewerId 点评人
     * @param operatorId 操作人
     * @param reviewedAt 点评时间
     * @param items      仅含处方号的子项命令（业务号由领域自生成）
     * @return 领域批量命令
     */
    CreatePrescriptionReviewsCommand toCreateReviewsCommand(
        BatchCreateReviewsRequest request,
        Long reviewerId,
        Long operatorId,
        LocalDateTime reviewedAt,
        List<CreatePrescriptionReviewItemCommand> items);

    /**
     * 新建 id 列表 → 批量创建响应。
     *
     * @param ids 新建主键；可空视为空列表
     * @return 批量创建响应
     */
    BatchCreateReviewsResponse toBatchCreateResponse(List<Long> ids);

    /**
     * 撤销请求 → 领域撤销命令。
     *
     * @param request    撤销请求；调用方保证非空
     * @param operatorId 操作人
     * @return 领域撤销命令
     */
    RevokePrescriptionReviewCommand toRevokeCommand(RevokeReviewRequest request, Long operatorId);

    /**
     * 自动点评：子项 + 发药人 → 领域批量命令（合理、无原因/备注）。
     *
     * @param items       子项命令
     * @param dispenserId 发药人（点评人）
     * @param operatorId  操作人
     * @param reviewedAt  点评时间
     * @return 领域批量命令
     */
    CreatePrescriptionReviewsCommand toCreateReviewsCommandForAuto(
        List<CreatePrescriptionReviewItemCommand> items,
        Long dispenserId,
        Long operatorId,
        LocalDateTime reviewedAt);

    /**
     * 自动点评响应装配。
     *
     * <p>失败路径须 {@code softFailed=true} 且<strong>不得</strong>再向上抛异常，以免连带回滚发药。</p>
     *
     * @param createdIds   新建 id；可空
     * @param skippedCount 跳过条数
     * @param softFailed   是否点评软失败（发药仍视为成功）
     * @return 自动点评响应
     */
    CreateAutoReviewAfterDispenseResponse toAutoReviewResponse(
        List<Long> createdIds,
        int skippedCount,
        boolean softFailed);

    /**
     * 读模型 → 有效点评 DTO。
     *
     * @param model        读模型；{@code null} 时返回 {@code null}
     * @param reviewerName 点评人姓名；可空
     * @return 有效点评 DTO
     */
    PrescriptionReviewActiveDto toActiveDto(PrescriptionReviewQueryModel model, String reviewerName);

    /**
     * 装配批量映射响应。
     *
     * @param map 处方号 → 有效点评；可空视为空 Map
     * @return 映射响应
     */
    MapActiveReviewsByPrescriptionNosResponse toMapActiveResponse(Map<String, PrescriptionReviewActiveDto> map);
}
