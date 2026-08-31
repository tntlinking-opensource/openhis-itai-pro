/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.support;

import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.BatchCreateReviewItemDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.BatchCreateReviewsRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.CreateAutoReviewAfterDispenseRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.RevokeReviewRequest;
import com.openhis.health.domain.custom.prescriptionreview.queries.model.PrescriptionReviewQueryModel;
import com.openhis.health.domain.custom.prescriptionreview.service.PrescriptionReviewQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionReviewConstants;
import com.openhis.health.domain.share.enums.PrescriptionReviewStatus;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊处方点评应用辅助（选项装配、入参校验、自动点评跳过过滤）。
 *
 * <p>不承载写库；不跨 Feature 依赖他包 support。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientPharmacyPrescriptionReviewSupport {

    /** 有效点评读侧（过滤已点评处方号）。 */
    private final PrescriptionReviewQueryService prescriptionReviewQueryService;

    /**
     * 装配点评状态下拉（PENDING / REVIEWED）。
     *
     * @return 状态下拉选项
     */
    public List<OptionDto> buildStatusOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto pending = new OptionDto();
        pending.setValue(PrescriptionReviewStatus.PENDING.getCode());
        pending.setLabel(PrescriptionReviewStatus.PENDING.getDisplay());
        options.add(pending);
        OptionDto reviewed = new OptionDto();
        reviewed.setValue(PrescriptionReviewStatus.REVIEWED.getCode());
        reviewed.setLabel(PrescriptionReviewStatus.REVIEWED.getDisplay());
        options.add(reviewed);
        return options;
    }

    /**
     * 装配是否合理下拉（合理 / 不合理；value 为字符串化布尔）。
     *
     * @return 是否合理选项
     */
    public List<OptionDto> buildReasonableOptions() {
        List<OptionDto> options = new ArrayList<>();
        OptionDto reasonable = new OptionDto();
        reasonable.setValue(OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_TRUE_VALUE);
        reasonable.setLabel(OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_TRUE_LABEL);
        options.add(reasonable);
        OptionDto unreasonable = new OptionDto();
        unreasonable.setValue(OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_FALSE_VALUE);
        unreasonable.setLabel(OutpatientPharmacyPrescriptionReviewConstants.REASONABLE_FALSE_LABEL);
        options.add(unreasonable);
        return options;
    }

    /**
     * 默认点评日起止：本月首日～本月末日（yyyy-MM-dd）。
     *
     * @return 默认时间窗
     */
    public ReviewedDateWindow defaultReviewedDateWindow() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        ReviewedDateWindow window = new ReviewedDateWindow();
        window.setFrom(monthStart.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        window.setTo(monthEnd.format(GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        return window;
    }

    /**
     * Gate：点评日起止均传时校验格式与 from≤to；任一侧空则跳过。
     *
     * @param reviewedDateFrom 点评日起
     * @param reviewedDateTo   点评日止
     * @throws BusinessException EX-PRV-102 格式非法或起止颠倒
     */
    public void assertReviewedDateWindow(String reviewedDateFrom, String reviewedDateTo) {
        boolean fromBlank = StringUtils.isBlank(reviewedDateFrom);
        boolean toBlank = StringUtils.isBlank(reviewedDateTo);
        // 两侧皆空：不按点评日收窄，跳过窗校验
        if (fromBlank && toBlank) {
            return;
        }
        // 仅传一侧：时间窗不完整，拒绝查询以免语义歧义
        if (fromBlank || toBlank) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_102,
                "请选择有效的分页或点评时间范围");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(reviewedDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
            to = LocalDate.parse(reviewedDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        } catch (DateTimeParseException ex) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_102,
                "请选择有效的分页或点评时间范围");
        }
        // 结束日早于起始日时拒绝，避免空结果误导
        if (from.isAfter(to)) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_102,
                "请选择有效的分页或点评时间范围");
        }
    }

    /**
     * Gate：批量创建请求 items/reasonable/reason 合法性。
     *
     * @param request 批量创建请求
     * @throws BusinessException EX-PRV-200 / EX-PRV-203
     */
    public void assertBatchCreateRequest(BatchCreateReviewsRequest request) {
        // 空请求无法校验 items/合理结论
        if (request == null) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        List<BatchCreateReviewItemDto> items = request.getItems();
        // 空列表无法落库
        if (items == null || items.isEmpty()) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        // 共用是否合理必填
        if (request.getReasonableFlag() == null) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        Set<String> seen = new HashSet<>();
        for (BatchCreateReviewItemDto item : items) {
            // 子项或处方号缺失
            if (item == null || StringUtils.isBlank(item.getPrescriptionNo())) {
                throw new BusinessException(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                    "请检查点评入参");
            }
            String no = item.getPrescriptionNo().trim();
            // 同批重复处方号拒绝，防止一对一冲突与重复写
            if (seen.contains(no)) {
                throw new BusinessException(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                    "请检查点评入参");
            }
            seen.add(no);
        }
        // 不合理时原因必填（BR-PRV-002）
        if (Boolean.FALSE.equals(request.getReasonableFlag()) && StringUtils.isBlank(request.getReason())) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_203,
                "判定不合理时请填写原因");
        }
    }

    /**
     * Gate：撤销锚点 id 与 prescriptionNo 至少其一非空。
     *
     * @param request 撤销请求
     * @throws BusinessException EX-PRV-200
     */
    public void assertRevokeAnchor(RevokeReviewRequest request) {
        // 空请求无法定位撤销目标
        if (request == null) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        // 主键与处方号皆空则无法定位有效点评
        if (request.getId() == null && StringUtils.isBlank(request.getPrescriptionNo())) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
    }

    /**
     * Gate：自动点评开启时处方号列表与发药人必填。
     *
     * @param request 自动点评请求
     * @throws BusinessException EX-PRV-200
     */
    public void assertAutoReviewRequest(CreateAutoReviewAfterDispenseRequest request) {
        // 空请求无法校验发药人与处方号
        if (request == null) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        // 发药人作为点评人必填
        if (request.getDispenserId() == null) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        List<String> nos = request.getPrescriptionNos();
        // 开启自动点评时须有非空处方号
        if (nos == null || nos.isEmpty()) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
        boolean anyNonBlank = false;
        for (String no : nos) {
            // 任一非空白号即可视为列表有效
            if (StringUtils.isNotEmpty(no)) {
                anyNonBlank = true;
                break;
            }
        }
        // 全空白号等同空列表
        if (!anyNonBlank) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_200,
                "请检查点评入参");
        }
    }

    /**
     * Pre：过滤掉已有有效点评的处方号，返回待自动点评号列表。
     *
     * @param prescriptionNos 入参处方号
     * @return 过滤结果（待创建号 + 跳过条数）
     */
    public FilterWithoutActiveResult filterPrescriptionNosWithoutActiveReview(List<String> prescriptionNos) {
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // 入参非空时清洗空白与同批重复号
        if (prescriptionNos != null) {
            for (String no : prescriptionNos) {
                // 空白号跳过，不计入待创建/跳过
                if (StringUtils.isBlank(no)) {
                    continue;
                }
                String trimmed = no.trim();
                // 同批去重，避免重复写
                if (seen.contains(trimmed)) {
                    continue;
                }
                seen.add(trimmed);
                cleaned.add(trimmed);
            }
        }
        Map<String, PrescriptionReviewQueryModel> activeMap =
            this.prescriptionReviewQueryService.mapActiveByPrescriptionNos(cleaned);
        List<String> toCreate = new ArrayList<>();
        int skipped = 0;
        for (String no : cleaned) {
            // 已有有效点评：跳过，计入 skipped
            if (activeMap.containsKey(no)) {
                skipped = skipped + 1;
            } else {
                toCreate.add(no);
            }
        }
        FilterWithoutActiveResult result = new FilterWithoutActiveResult();
        result.setPrescriptionNosToCreate(toCreate);
        result.setSkippedCount(skipped);
        return result;
    }

    /**
     * 默认点评日窗载体。
     */
    @Getter
    @Setter
    public static class ReviewedDateWindow {

        /** 点评日起 yyyy-MM-dd。 */
        private String from;
        /** 点评日止 yyyy-MM-dd。 */
        private String to;
    }

    /**
     * 自动点评跳过过滤结果。
     */
    @Getter
    @Setter
    public static class FilterWithoutActiveResult {

        /** 尚无有效点评、待创建的处方号。 */
        private List<String> prescriptionNosToCreate;
        /** 因已有有效点评跳过的条数。 */
        private int skippedCount;
    }
}
