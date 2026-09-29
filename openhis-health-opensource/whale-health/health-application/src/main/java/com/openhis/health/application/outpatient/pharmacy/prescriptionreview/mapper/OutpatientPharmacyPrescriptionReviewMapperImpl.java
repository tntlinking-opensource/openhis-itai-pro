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
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewItemCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewsCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.RevokePrescriptionReviewCommand;
import com.openhis.health.domain.custom.prescriptionreview.queries.PrescriptionReviewPageQuery;
import com.openhis.health.domain.custom.prescriptionreview.queries.model.PrescriptionReviewQueryModel;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionReviewConstants;
import com.openhis.health.domain.share.enums.PrescriptionReviewStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link OutpatientPharmacyPrescriptionReviewMapper} 实现：纯字段搬运与展示文案装配。
 */
@Component
public class OutpatientPharmacyPrescriptionReviewMapperImpl implements OutpatientPharmacyPrescriptionReviewMapper {

    /**
     * 装配页初始化响应。
     *
     * @param statusOptions           点评状态下拉；调用方保证非空
     * @param reasonableOptions       是否合理下拉；调用方保证非空
     * @param keyDrugTypeOptions      重点药类型下拉；调用方保证非空
     * @param medicinePropertyOptions 药品性质下拉；调用方保证非空
     * @param defaultFrom             默认点评日起
     * @param defaultTo               默认点评日止
     * @return 初始化响应
     */
    @Override
    public LoadPageInitResponse toLoadPageInitResponse(
        List<OptionDto> statusOptions,
        List<OptionDto> reasonableOptions,
        List<OptionDto> keyDrugTypeOptions,
        List<OptionDto> medicinePropertyOptions,
        String defaultFrom,
        String defaultTo) {
        LoadPageInitResponse response = new LoadPageInitResponse();
        response.setStatusOptions(statusOptions);
        response.setReasonableOptions(reasonableOptions);
        response.setKeyDrugTypeOptions(keyDrugTypeOptions);
        response.setMedicinePropertyOptions(medicinePropertyOptions);
        response.setDefaultReviewedDateFrom(defaultFrom);
        response.setDefaultReviewedDateTo(defaultTo);
        return response;
    }

    /**
     * 分页查询请求 → 领域分页筛选（日期转 LocalDateTime 闭区间）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public PrescriptionReviewPageQuery toPageQuery(PageReviewsRequest request) {
        PrescriptionReviewPageQuery query = new PrescriptionReviewPageQuery();
        this.applyFilterFields(
            query,
            request.getStatusEnum(),
            request.getReviewedDateFrom(),
            request.getReviewedDateTo(),
            request.getReasonableFlag(),
            request.getReviewerId(),
            request.getKeyword());
        return query;
    }

    /**
     * 导出请求 → 领域分页筛选（与列表同源）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域查询入参
     */
    @Override
    public PrescriptionReviewPageQuery toPageQuery(ExportReviewsRequest request) {
        PrescriptionReviewPageQuery query = new PrescriptionReviewPageQuery();
        this.applyFilterFields(
            query,
            request.getStatusEnum(),
            request.getReviewedDateFrom(),
            request.getReviewedDateTo(),
            request.getReasonableFlag(),
            request.getReviewerId(),
            request.getKeyword());
        return query;
    }

    /**
     * 读模型 → 列表行（点评字段；方级指标由 Enricher 后写）。
     *
     * @param model        读模型；{@code null} 时返回 {@code null}
     * @param reviewerName 点评人姓名（列表工作表列不展示，保留参数兼容调用方）
     * @return 列表行 DTO
     */
    @Override
    public PrescriptionReviewListRowDto toListRowDto(PrescriptionReviewQueryModel model, String reviewerName) {
        // 空读模型：调用方按 null 跳过该行
        if (model == null) {
            return null;
        }
        PrescriptionReviewListRowDto row = new PrescriptionReviewListRowDto();
        row.setId(model.getId());
        row.setBusNo(model.getBusNo());
        row.setPrescriptionNo(model.getPrescriptionNo());
        row.setStatusEnum(model.getStatusEnum());
        row.setStatusText(this.resolveStatusText(model.getStatusEnum()));
        row.setReasonableFlag(model.getReasonableFlag());
        row.setReasonableText(this.resolveReasonableText(model.getReasonableFlag()));
        row.setReason(model.getReason());
        row.setRemark(model.getRemark());
        row.setReviewerId(model.getReviewerId());
        row.setReviewerName(reviewerName);
        // 点评时间非空时格式化为展示串；供详情「点评时间」
        if (model.getReviewedAt() != null) {
            row.setReviewedAt(model.getReviewedAt().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        return row;
    }

    /**
     * 装配分页响应。
     *
     * @param page 领域分页结果；调用方保证非空
     * @param rows 已映射列表行
     * @return 分页响应
     */
    @Override
    public PageReviewsResponse toPageResponse(
        Page<PrescriptionReviewQueryModel> page,
        List<PrescriptionReviewListRowDto> rows) {
        return PagedResultBuilder.build(new PageReviewsResponse(), rows, page);
    }

    /**
     * 列表行 → 导出行（工作表同列；是否合理导出 0/1）。
     *
     * @param row 列表行；{@code null} 时返回 {@code null}
     * @return 导出行 DTO
     */
    @Override
    public PrescriptionReviewExportRowDto toExportRowDto(PrescriptionReviewListRowDto row) {
        // 空列表行：导出行亦空，避免 NPE
        if (row == null) {
            return null;
        }
        PrescriptionReviewExportRowDto exportRow = new PrescriptionReviewExportRowDto();
        exportRow.setPrescriptionNo(row.getPrescriptionNo());
        exportRow.setPatientAgeText(row.getPatientAgeText());
        exportRow.setDiagnosisName(row.getDiagnosisName());
        exportRow.setDrugVarietyCount(row.getDrugVarietyCount());
        exportRow.setAntibioticFlag(row.getAntibioticFlag());
        exportRow.setInjectFlag(row.getInjectFlag());
        exportRow.setBasicDrugVarietyCount(row.getBasicDrugVarietyCount());
        exportRow.setGenericNameCount(row.getGenericNameCount());
        exportRow.setPrescriptionAmount(row.getPrescriptionAmount());
        exportRow.setOrderingPractitionerName(row.getOrderingPractitionerName());
        // 是否合理：true→1、false→0、空→空（与工作表 0/1 口径）
        if (Boolean.TRUE.equals(row.getReasonableFlag())) {
            exportRow.setReasonableFlag(Integer.valueOf(1));
        } else if (Boolean.FALSE.equals(row.getReasonableFlag())) {
            exportRow.setReasonableFlag(Integer.valueOf(0));
        } else {
            exportRow.setReasonableFlag(null);
        }
        exportRow.setReason(row.getReason());
        return exportRow;
    }

    /**
     * 装配导出响应。
     *
     * @param body      xlsx 字节
     * @param rowCount  导出行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    @Override
    public ExportReviewsResponse toExportResponse(byte[] body, int rowCount, boolean truncated) {
        ExportReviewsResponse response = new ExportReviewsResponse();
        response.setFileName(OutpatientPharmacyPrescriptionReviewConstants.EXPORT_DEFAULT_FILENAME);
        response.setContentType(OutpatientPharmacyPrescriptionReviewConstants.XLSX_CONTENT_TYPE);
        response.setBody(body);
        response.setExportedRowCount(Integer.valueOf(rowCount));
        response.setTruncated(Boolean.valueOf(truncated));
        return response;
    }

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
    @Override
    public CreatePrescriptionReviewsCommand toCreateReviewsCommand(
        BatchCreateReviewsRequest request,
        Long reviewerId,
        Long operatorId,
        LocalDateTime reviewedAt,
        List<CreatePrescriptionReviewItemCommand> items) {
        CreatePrescriptionReviewsCommand command = new CreatePrescriptionReviewsCommand();
        command.setItems(items);
        command.setReasonableFlag(request.getReasonableFlag());
        command.setReason(request.getReason());
        command.setRemark(request.getRemark());
        command.setReviewerId(reviewerId);
        command.setReviewedAt(reviewedAt);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 新建 id 列表 → 批量创建响应。
     *
     * @param ids 新建主键；可空视为空列表
     * @return 批量创建响应
     */
    @Override
    public BatchCreateReviewsResponse toBatchCreateResponse(List<Long> ids) {
        BatchCreateReviewsResponse response = new BatchCreateReviewsResponse();
        List<Long> safeIds;
        // 空 id 列表按空集合回传，createdCount=0
        if (ids == null) {
            safeIds = new ArrayList<>();
        } else {
            safeIds = ids;
        }
        response.setCreatedIds(safeIds);
        response.setCreatedCount(Integer.valueOf(safeIds.size()));
        return response;
    }

    /**
     * 撤销请求 → 领域撤销命令。
     *
     * @param request    撤销请求；调用方保证非空
     * @param operatorId 操作人
     * @return 领域撤销命令
     */
    @Override
    public RevokePrescriptionReviewCommand toRevokeCommand(RevokeReviewRequest request, Long operatorId) {
        RevokePrescriptionReviewCommand command = new RevokePrescriptionReviewCommand();
        command.setId(request.getId());
        command.setPrescriptionNo(request.getPrescriptionNo());
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 自动点评：子项 + 发药人 → 领域批量命令（合理、无原因/备注）。
     *
     * @param items       子项命令
     * @param dispenserId 发药人（点评人）
     * @param operatorId  操作人
     * @param reviewedAt  点评时间
     * @return 领域批量命令
     */
    @Override
    public CreatePrescriptionReviewsCommand toCreateReviewsCommandForAuto(
        List<CreatePrescriptionReviewItemCommand> items,
        Long dispenserId,
        Long operatorId,
        LocalDateTime reviewedAt) {
        CreatePrescriptionReviewsCommand command = new CreatePrescriptionReviewsCommand();
        command.setItems(items);
        command.setReasonableFlag(Boolean.TRUE);
        command.setReason(null);
        command.setRemark(null);
        command.setReviewerId(dispenserId);
        command.setReviewedAt(reviewedAt);
        command.setOperatorId(operatorId);
        return command;
    }

    /**
     * 自动点评响应装配。
     *
     * <p>失败路径须 {@code softFailed=true} 且不得再向上抛异常，以免连带回滚发药。</p>
     *
     * @param createdIds   新建 id；可空
     * @param skippedCount 跳过条数
     * @param softFailed   是否点评软失败（发药仍视为成功）
     * @return 自动点评响应
     */
    @Override
    public CreateAutoReviewAfterDispenseResponse toAutoReviewResponse(
        List<Long> createdIds,
        int skippedCount,
        boolean softFailed) {
        CreateAutoReviewAfterDispenseResponse response = new CreateAutoReviewAfterDispenseResponse();
        List<Long> safeIds;
        // 空 id 列表按空集合；失败时仍返回可解析结构供发药侧提示
        if (createdIds == null) {
            safeIds = new ArrayList<>();
        } else {
            safeIds = createdIds;
        }
        response.setCreatedIds(safeIds);
        response.setCreatedCount(Integer.valueOf(safeIds.size()));
        response.setSkippedCount(Integer.valueOf(skippedCount));
        // softFailed=true 表示点评未完整写入，发药事实不得因此回滚
        response.setSoftFailed(Boolean.valueOf(softFailed));
        return response;
    }

    /**
     * 读模型 → 有效点评 DTO。
     *
     * @param model        读模型；{@code null} 时返回 {@code null}
     * @param reviewerName 点评人姓名；可空
     * @return 有效点评 DTO
     */
    @Override
    public PrescriptionReviewActiveDto toActiveDto(PrescriptionReviewQueryModel model, String reviewerName) {
        // 无有效点评：调用方不写 Map 键
        if (model == null) {
            return null;
        }
        PrescriptionReviewActiveDto dto = new PrescriptionReviewActiveDto();
        dto.setId(model.getId());
        dto.setPrescriptionNo(model.getPrescriptionNo());
        dto.setBusNo(model.getBusNo());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusText(this.resolveStatusText(model.getStatusEnum()));
        dto.setReasonableFlag(model.getReasonableFlag());
        dto.setReasonableText(this.resolveReasonableText(model.getReasonableFlag()));
        dto.setReason(model.getReason());
        dto.setRemark(model.getRemark());
        dto.setReviewerId(model.getReviewerId());
        dto.setReviewerName(reviewerName);
        // 点评时间非空时格式化；空则 DTO 时间列可空
        if (model.getReviewedAt() != null) {
            dto.setReviewedAt(model.getReviewedAt().format(GeneralSharedConstants.DATE_TIME_FORMATTER));
        }
        return dto;
    }

    /**
     * 装配批量映射响应。
     *
     * @param map 处方号 → 有效点评；可空视为空 Map
     * @return 映射响应
     */
    @Override
    public MapActiveReviewsByPrescriptionNosResponse toMapActiveResponse(
        Map<String, PrescriptionReviewActiveDto> map) {
        MapActiveReviewsByPrescriptionNosResponse response = new MapActiveReviewsByPrescriptionNosResponse();
        // 空 Map：返回空集合，前端按「无有效点评」处理
        if (map == null) {
            response.setReviewsByPrescriptionNo(Collections.emptyMap());
        } else {
            response.setReviewsByPrescriptionNo(map);
        }
        return response;
    }

    /**
     * 将筛选字段写入领域分页 Query（日期闭区间）。
     *
     * @param query            目标 Query
     * @param statusEnum       状态
     * @param reviewedDateFrom 日起
     * @param reviewedDateTo   日止
     * @param reasonableFlag   是否合理
     * @param reviewerId       点评人
     * @param keyword          处方号/业务号关键字（OR）
     */
    private void applyFilterFields(
        PrescriptionReviewPageQuery query,
        String statusEnum,
        String reviewedDateFrom,
        String reviewedDateTo,
        Boolean reasonableFlag,
        Long reviewerId,
        String keyword) {
        // 状态 code：非空则写入；PENDING 空页语义由 QueryService 处理
        if (StringUtils.isNotEmpty(statusEnum)) {
            query.setStatusEnum(statusEnum.trim());
        }
        // 点评日起：日初 00:00:00 闭区间下界
        if (StringUtils.isNotEmpty(reviewedDateFrom)) {
            LocalDate from = LocalDate.parse(reviewedDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            query.setReviewedAtFrom(from.atStartOfDay());
        }
        // 点评日止：日末 LocalTime.MAX 闭区间上界
        if (StringUtils.isNotEmpty(reviewedDateTo)) {
            LocalDate to = LocalDate.parse(reviewedDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            query.setReviewedAtTo(to.atTime(LocalTime.MAX));
        }
        query.setReasonableFlag(reasonableFlag);
        query.setReviewerId(reviewerId);
        // 关键字 trim 后模糊（处方号 OR 业务号）
        if (StringUtils.isNotEmpty(keyword)) {
            query.setKeyword(keyword.trim());
        }
    }

    /**
     * 状态 code → 展示文案。
     *
     * @param statusEnum 状态 code
     * @return 展示文案；未知则回落 code 本身
     */
    private String resolveStatusText(String statusEnum) {
        // PENDING：待点评展示文案
        if (PrescriptionReviewStatus.PENDING.getCode().equals(statusEnum)) {
            return PrescriptionReviewStatus.PENDING.getDisplay();
        }
        // REVIEWED：已点评展示文案
        if (PrescriptionReviewStatus.REVIEWED.getCode().equals(statusEnum)) {
            return PrescriptionReviewStatus.REVIEWED.getDisplay();
        }
        // 未知 code：回落原值，避免空白误导
        return statusEnum;
    }

    /**
     * 是否合理 → 展示文案。
     *
     * @param reasonableFlag 是否合理
     * @return 合理/不合理；空则 null
     */
    private String resolveReasonableText(Boolean reasonableFlag) {
        // 未判定：不展示文案
        if (reasonableFlag == null) {
            return null;
        }
        // true → 合理
        if (Boolean.TRUE.equals(reasonableFlag)) {
            return OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_TRUE_LABEL;
        }
        // false → 不合理
        return OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_FALSE_LABEL;
    }
}
