/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.prescriptionreview.service;

import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.BatchCreateReviewItemDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewActiveDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewExportRowDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.dto.PrescriptionReviewListRowDto;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.mapper.OutpatientPharmacyPrescriptionReviewMapper;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.BatchCreateReviewsRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.CreateAutoReviewAfterDispenseRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.ExportReviewsRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.LoadPageInitRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.MapActiveReviewsByPrescriptionNosRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.PageReviewsRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.request.RevokeReviewRequest;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.BatchCreateReviewsResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.CreateAutoReviewAfterDispenseResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.ExportReviewsResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.LoadPageInitResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.MapActiveReviewsByPrescriptionNosResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.PageReviewsResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.response.RevokeReviewResponse;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.support.OutpatientPharmacyPrescriptionReviewDispenseGate;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.support.OutpatientPharmacyPrescriptionReviewListEnricher;
import com.openhis.health.application.outpatient.pharmacy.prescriptionreview.support.OutpatientPharmacyPrescriptionReviewSupport;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewItemCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.CreatePrescriptionReviewsCommand;
import com.openhis.health.domain.custom.prescriptionreview.command.RevokePrescriptionReviewCommand;
import com.openhis.health.domain.custom.prescriptionreview.queries.PrescriptionReviewPageQuery;
import com.openhis.health.domain.custom.prescriptionreview.queries.model.PrescriptionReviewQueryModel;
import com.openhis.health.domain.custom.prescriptionreview.service.PrescriptionReviewDomainService;
import com.openhis.health.domain.custom.prescriptionreview.service.PrescriptionReviewQueryService;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientPharmacyPrescriptionReviewConstants;
import com.openhis.health.domain.share.enums.KeyDrugType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.AnnotatedExcelExporter;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 门诊处方点评应用服务（AS-OP-PHA-007，IF-PRV-01～07）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：登录、分页窗口、点评日窗、批量/撤销/自动点评入参。</li>
 *   <li>Pre：已发药 Gate、已点评冲突过滤、点评人姓名批量；业务号由领域 PRV 日序自生成。</li>
 *   <li>Cmd：领域 createReviews / revokeReview（写 IF 依赖 AppServiceAspect UoW，禁止 @Transactional）。</li>
 *   <li>Asm：Mapper + AnnotatedExcelExporter 装配响应。</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar} →
 * {@code POST /api/app/outpatientPharmacyPrescriptionReview/{methodName}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutpatientPharmacyPrescriptionReviewAppService {

    /** 登录医务人员（点评人/操作人须 adm_practitioner.id，禁止 whale_users.id）。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 分页窗口 Gate。 */
    private final PageWindowGate pageWindowGate;

    /** 点评人姓名批量。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 点评写侧。 */
    private final PrescriptionReviewDomainService prescriptionReviewDomainService;

    /** 点评读侧。 */
    private final PrescriptionReviewQueryService prescriptionReviewQueryService;

    /** 开方日窗 → 处方号预解析（仅本聚合读）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 特殊药品类型 → 药典主键预解析（B.6.4）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 已发药判定（只读药嘱/发放；不生成业务号）。 */
    private final OutpatientPharmacyPrescriptionReviewDispenseGate dispenseGate;

    /** 选项装配与入参校验。 */
    private final OutpatientPharmacyPrescriptionReviewSupport reviewSupport;

    /** 列表/导出方级指标 enrichment（工作表列）。 */
    private final OutpatientPharmacyPrescriptionReviewListEnricher listEnricher;

    /** Request/Command/Response 映射。 */
    private final OutpatientPharmacyPrescriptionReviewMapper reviewMapper;

    /**
     * IF-PRV-01：点评记录页初始化。
     *
     * @param request 空体占位请求
     * @return 状态下拉、是否合理下拉与默认本月点评日
     * @throws BusinessException EX-PRV-101 未登录或初始化失败
     */
    public LoadPageInitResponse loadPageInit(LoadPageInitRequest request) {
        // Gate：未登录或未绑定医务人员禁止拉取点评页元数据
        this.currentPractitionerResolver.requirePractitionerId(
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
            "页面初始化失败，请稍后重试");
        try {
            // Pre：点评状态下拉 PENDING/REVIEWED
            List<OptionDto> statusOptions = this.reviewSupport.buildStatusOptions();
            // Pre：是否合理下拉
            List<OptionDto> reasonableOptions = this.reviewSupport.buildReasonableOptions();
            // Pre：重点药类型 + 药品性质下拉（分框）
            List<OptionDto> keyDrugTypeOptions = this.reviewSupport.buildKeyDrugTypeOptions();
            List<OptionDto> medicinePropertyOptions = this.reviewSupport.buildMedicinePropertyOptions();
            // Pre：默认本月点评日起止
            OutpatientPharmacyPrescriptionReviewSupport.ReviewedDateWindow window =
                this.reviewSupport.defaultReviewedDateWindow();
            // Cmd：本 IF 只读，无领域写
            // Asm：装配初始化响应
            return this.reviewMapper.toLoadPageInitResponse(
                statusOptions,
                reasonableOptions,
                keyDrugTypeOptions,
                medicinePropertyOptions,
                window.getFrom(),
                window.getTo());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊处方点评页初始化失败", ex);
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
                "页面初始化失败，请稍后重试");
        }
    }

    /**
     * IF-PRV-02：分页查询点评记录。
     *
     * @param request 筛选与分页参数
     * @return 分页列表行
     * @throws BusinessException EX-PRV-101/102/103
     */
    public PageReviewsResponse pageReviews(PageReviewsRequest request) {
        // Gate：未登录或未绑定医务人员禁止查询
        this.currentPractitionerResolver.requirePractitionerId(
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
            "页面初始化失败，请稍后重试");
        // Gate：分页窗口非法直接拒绝
        this.pageWindowGate.requirePositivePageWindow(
            request,
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_102,
            "请选择有效的分页或点评时间范围");
        // Gate：点评日起止均传时校验 from≤to
        this.reviewSupport.assertReviewedDateWindow(request.getReviewedDateFrom(), request.getReviewedDateTo());
        // Gate：开方日起止均传时校验 from≤to
        this.reviewSupport.assertOpenedDateWindow(request.getOpenedDateFrom(), request.getOpenedDateTo());
        try {
            // Pre：Request → 领域筛选（日期闭区间）
            PrescriptionReviewPageQuery query = this.reviewMapper.toPageQuery(request);
            // Pre：开方日窗 → 处方号 IN（B.6.4 应用层编排）
            this.applyOpenedDateFilter(query, request.getOpenedDateFrom(), request.getOpenedDateTo());
            // Pre：特殊药品类型 → 处方号 IN 与开方日窗求交
            this.applySpecialDrugTypeFilter(query, request.getKeyDrugType());
            this.applySpecialDrugTypeFilter(query, request.getMedicinePropertyCode());
            PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
            // Pre：有效点评分页（PENDING → 空页由领域处理）
            Page<PrescriptionReviewQueryModel> page =
                this.prescriptionReviewQueryService.pageReviews(query, pageable);
            // Cmd：本 IF 只读，无领域写
            // Pre+Asm：映射点评字段后补齐工作表方级指标
            List<PrescriptionReviewListRowDto> rows = this.mapListRows(page.getContent());
            // Asm：分页响应
            return this.reviewMapper.toPageResponse(page, rows);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊处方点评分页查询失败", ex);
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * IF-PRV-03：导出点评记录（当前页窗口，与列表 pageNo/pageSize 一致）。
     *
     * @param request 与列表一致的筛选 + pageNo/pageSize + exportColumns
     * @return xlsx 字节与导出行数
     * @throws BusinessException EX-PRV-101/102/104
     */
    public ExportReviewsResponse exportReviews(ExportReviewsRequest request) {
        // Gate：未登录或未绑定医务人员禁止导出
        this.currentPractitionerResolver.requirePractitionerId(
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
            "页面初始化失败，请稍后重试");
        // Gate：导出列必填，非法则拒绝进入查询
        if (request == null || request.getExportColumns() == null || request.getExportColumns().isEmpty()) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_102,
                "请选择有效的分页或点评时间范围");
        }
        // Gate：分页窗口与列表同源（一页 N 条即导出 N 条）
        this.pageWindowGate.requirePositivePageWindow(
            request,
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_102,
            "请选择有效的分页或点评时间范围");
        // Gate：点评日窗（与列表同源）
        this.reviewSupport.assertReviewedDateWindow(request.getReviewedDateFrom(), request.getReviewedDateTo());
        // Gate：开方日窗（与列表同源）
        this.reviewSupport.assertOpenedDateWindow(request.getOpenedDateFrom(), request.getOpenedDateTo());
        try {
            // Pre：映射筛选
            PrescriptionReviewPageQuery query = this.reviewMapper.toPageQuery(request);
            // Pre：开方日窗 → 处方号 IN
            this.applyOpenedDateFilter(query, request.getOpenedDateFrom(), request.getOpenedDateTo());
            // Pre：特殊药品类型 → 处方号 IN 与开方日窗求交
            this.applySpecialDrugTypeFilter(query, request.getKeyDrugType());
            this.applySpecialDrugTypeFilter(query, request.getMedicinePropertyCode());
            PageRequest pageable = PageRequest.of(request.springPageIndex(), request.getBoundedPageSize());
            // Pre：当前页点评分页（非全量上限窗）
            Page<PrescriptionReviewQueryModel> page =
                this.prescriptionReviewQueryService.pageReviews(query, pageable);
            // Cmd：本 IF 只读；仍有未导出页时 truncated=true 提示
            long exportedThrough =
                (long) (request.getPageNo() - 1) * request.getPageSize() + page.getNumberOfElements();
            boolean truncated = page.getTotalElements() > exportedThrough;
            List<PrescriptionReviewListRowDto> listRows = this.mapListRows(page.getContent());
            List<PrescriptionReviewExportRowDto> exportRows = new ArrayList<>(listRows.size());
            for (PrescriptionReviewListRowDto listRow : listRows) {
                // Asm：列表行 → 导出行（工作表同列）
                exportRows.add(this.reviewMapper.toExportRowDto(listRow));
            }
            // Asm：xlsx 字节（零行仍含表头）
            byte[] body = AnnotatedExcelExporter.exportToXlsxBytes(
                OutpatientPharmacyPrescriptionReviewConstants.EXPORT_SHEET_NAME,
                exportRows,
                PrescriptionReviewExportRowDto.class,
                request.getExportColumns());
            // Asm：导出响应
            return this.reviewMapper.toExportResponse(body, exportRows.size(), truncated);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("门诊处方点评导出失败", ex);
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_104,
                "导出失败，请稍后重试");
        }
    }

    /**
     * IF-PRV-04：批量创建点评。
     *
     * @param request 待点评处方与共用是否合理/原因/备注
     * @return 新建主键列表
     * @throws BusinessException EX-PRV-101/200/201/202/203
     */
    public BatchCreateReviewsResponse batchCreateReviews(BatchCreateReviewsRequest request) {
        // Gate：登录医务人员兼作点评人（adm_practitioner.id）
        Long practitionerId = this.currentPractitionerResolver.requirePractitionerId(
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
            "页面初始化失败，请稍后重试");
        // Gate：items/reasonable/reason 合法性
        this.reviewSupport.assertBatchCreateRequest(request);
        List<String> prescriptionNos = new ArrayList<>();
        for (BatchCreateReviewItemDto item : request.getItems()) {
            prescriptionNos.add(item.getPrescriptionNo().trim());
        }
        // Pre：任一号已有有效点评则整批拒绝
        Map<String, PrescriptionReviewQueryModel> activeMap =
            this.prescriptionReviewQueryService.mapActiveByPrescriptionNos(prescriptionNos);
        // 已点评不可再选：整批失败，避免部分写入后才撞一对一
        if (activeMap != null && !activeMap.isEmpty()) {
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_202,
                "该处方已点评，请先撤销再评");
        }
        // Pre：已发药 Gate（任一方未发药 → EX-PRV-201）
        this.dispenseGate.assertDispensedOutpatientPrescriptions(prescriptionNos);
        // Pre：仅组处方号子项；业务号由领域 PRV 日序自生成，不取 MR/EN 快照
        List<CreatePrescriptionReviewItemCommand> itemCommands = new ArrayList<>();
        for (BatchCreateReviewItemDto item : request.getItems()) {
            CreatePrescriptionReviewItemCommand cmdItem = new CreatePrescriptionReviewItemCommand();
            cmdItem.setPrescriptionNo(item.getPrescriptionNo().trim());
            itemCommands.add(cmdItem);
        }
        LocalDateTime reviewedAt = LocalDateTime.now();
        // Cmd：领域批量创建（点评人=操作人=当前登录医务人员）
        CreatePrescriptionReviewsCommand command = this.reviewMapper.toCreateReviewsCommand(
            request,
            practitionerId,
            practitionerId,
            reviewedAt,
            itemCommands);
        List<Long> createdIds;
        try {
            createdIds = this.prescriptionReviewDomainService.createReviews(command);
        } catch (BusinessException ex) {
            // 领域一对一冲突映射为应用语义码，避免前端看到领域码
            if (ErrorCodeConstants.PRESCRIPTION_REVIEW_ALREADY_EXISTS.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_202,
                    "该处方已点评，请先撤销再评");
            }
            throw ex;
        }
        // Asm：批量创建响应
        return this.reviewMapper.toBatchCreateResponse(createdIds);
    }

    /**
     * IF-PRV-05：撤销点评。
     *
     * @param request id 优先，否则处方号
     * @return success=true
     * @throws BusinessException EX-PRV-101/200/204
     */
    public RevokeReviewResponse revokeReview(RevokeReviewRequest request) {
        // Gate：登录医务人员
        Long practitionerId = this.currentPractitionerResolver.requirePractitionerId(
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
            "页面初始化失败，请稍后重试");
        // Gate：锚点至少其一
        this.reviewSupport.assertRevokeAnchor(request);
        // Pre：id 优先定位有效行，确认存在
        if (request.getId() != null) {
            try {
                this.prescriptionReviewQueryService.getById(request.getId());
            } catch (BusinessException ex) {
                // 领域 NOT_FOUND → 应用 EX-PRV-204，提示可撤销目标已不在
                if (ErrorCodeConstants.PRESCRIPTION_REVIEW_NOT_FOUND.equals(ex.getErrorCode())) {
                    throw new BusinessException(
                        OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_204,
                        "有效点评不存在或已撤销");
                }
                throw ex;
            }
        } else {
            // Pre：仅处方号时查有效点评
            Optional<PrescriptionReviewQueryModel> active =
                this.prescriptionReviewQueryService.findActiveByPrescriptionNo(request.getPrescriptionNo());
            // 无有效行：禁止进入 Cmd，避免领域二次抛出
            if (active.isEmpty()) {
                throw new BusinessException(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_204,
                    "有效点评不存在或已撤销");
            }
        }
        // Cmd：领域软删 + Revoked 事件
        RevokePrescriptionReviewCommand command = this.reviewMapper.toRevokeCommand(request, practitionerId);
        try {
            this.prescriptionReviewDomainService.revokeReview(command);
        } catch (BusinessException ex) {
            // 并发下目标已被撤：统一映射 EX-PRV-204
            if (ErrorCodeConstants.PRESCRIPTION_REVIEW_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_204,
                    "有效点评不存在或已撤销");
            }
            throw ex;
        }
        // Asm：成功固定 true
        RevokeReviewResponse response = new RevokeReviewResponse();
        response.setSuccess(Boolean.TRUE);
        return response;
    }

    /**
     * IF-PRV-06：发药完成后自动点评（合理、空原因；不重复发药 Gate）。
     *
     * <p><strong>硬约束（BR-PRV-A11）</strong>：本方法在开关开启后的点评路径<strong>不得向上抛业务/运行时异常</strong>，
     * 失败一律 {@code softFailed=true} 正常返回，避免与发药同 UoW 时连带回滚已成功发药。
     * 发药编排应在核发成功后再调；即使同事务调用，亦不得因点评失败冲正发药。</p>
     *
     * <p>进程内调用：若已传 dispenserId，可用其作 operator 并跳过严格登录（兼容发药编排）。</p>
     *
     * @param request 处方号、发药人、开关
     * @return 新建 id、跳过数、softFailed；开关关闭时空成功 softFailed=false
     */
    public CreateAutoReviewAfterDispenseResponse createAutoReviewAfterDispense(
        CreateAutoReviewAfterDispenseRequest request) {
        // Gate：开关关闭或空请求 → 空成功，不碰点评写库
        if (request == null || !Boolean.TRUE.equals(request.getAutoReviewEnabled())) {
            return this.reviewMapper.toAutoReviewResponse(List.of(), 0, false);
        }
        // 以下整段：任意失败只 softFailed，禁止抛出影响发药
        try {
            // Gate：有 dispenserId（医务人员）时信任发药上下文；否则取登录医务人员
            Long practitionerId;
            if (request.getDispenserId() != null) {
                practitionerId = request.getDispenserId();
            } else {
                // 网关直调无发药人：补登录医务人员；失败进 catch → softFailed
                practitionerId = this.currentPractitionerResolver.requirePractitionerId(
                    OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
                    "自动点评未能确认操作人");
            }
            // Gate：开启时 nos/dispenserId 必填（失败进 catch → softFailed）
            this.reviewSupport.assertAutoReviewRequest(request);
            // Pre：过滤已有有效点评
            OutpatientPharmacyPrescriptionReviewSupport.FilterWithoutActiveResult filterResult =
                this.reviewSupport.filterPrescriptionNosWithoutActiveReview(request.getPrescriptionNos());
            List<String> toCreate = filterResult.getPrescriptionNosToCreate();
            int skippedCount = filterResult.getSkippedCount();
            // Pre：过滤后为空 → 仅返回跳过数（点评侧成功，无需 softFailed）
            if (toCreate == null || toCreate.isEmpty()) {
                return this.reviewMapper.toAutoReviewResponse(List.of(), skippedCount, false);
            }
            // Pre：仅组处方号；业务号由领域 PRV 日序自生成；发药调用方已保证 COMPLETED
            List<CreatePrescriptionReviewItemCommand> items = new ArrayList<>();
            for (String prescriptionNo : toCreate) {
                CreatePrescriptionReviewItemCommand item = new CreatePrescriptionReviewItemCommand();
                item.setPrescriptionNo(prescriptionNo);
                items.add(item);
            }
            LocalDateTime reviewedAt = LocalDateTime.now();
            // 有发药人时点评人取 dispenserId；否则与操作人同为登录医务人员
            Long reviewerId;
            if (request.getDispenserId() != null) {
                reviewerId = request.getDispenserId();
            } else {
                reviewerId = practitionerId;
            }
            CreatePrescriptionReviewsCommand command = this.reviewMapper.toCreateReviewsCommandForAuto(
                items,
                reviewerId,
                practitionerId,
                reviewedAt);
            // Cmd：批量创建合理点评
            List<Long> createdIds = this.prescriptionReviewDomainService.createReviews(command);
            // Asm：点评成功
            return this.reviewMapper.toAutoReviewResponse(createdIds, skippedCount, false);
        } catch (RuntimeException ex) {
            // Asm：点评软失败 — 只打日志并返回，绝不向上抛，避免发药回滚
            log.warn(
                "发药后自动点评失败（softFailed，不影响发药成功） code={}",
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_205,
                ex);
            return this.reviewMapper.toAutoReviewResponse(List.of(), 0, true);
        }
    }

    /**
     * IF-PRV-07：按处方号批量映射有效点评。
     *
     * @param request 处方号列表（可空）
     * @return 仅含有效点评的 Map
     * @throws BusinessException EX-PRV-101/103
     */
    public MapActiveReviewsByPrescriptionNosResponse mapActiveReviewsByPrescriptionNos(
        MapActiveReviewsByPrescriptionNosRequest request) {
        // Gate：未登录或未绑定医务人员禁止映射
        this.currentPractitionerResolver.requirePractitionerId(
            OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_101,
            "页面初始化失败，请稍后重试");
        try {
            List<String> prescriptionNos;
            // 空请求：按空列表映射，返回空 Map 而非 NPE
            if (request == null) {
                prescriptionNos = List.of();
            } else {
                prescriptionNos = request.getPrescriptionNos();
            }
            // Pre：批量读有效点评
            Map<String, PrescriptionReviewQueryModel> activeMap =
                this.prescriptionReviewQueryService.mapActiveByPrescriptionNos(prescriptionNos);
            // Cmd：本 IF 只读，无领域写
            LinkedHashSet<Long> reviewerIds = new LinkedHashSet<>();
            for (PrescriptionReviewQueryModel model : activeMap.values()) {
                // 收集点评人 id，供姓名批量
                if (model != null && model.getReviewerId() != null) {
                    reviewerIds.add(model.getReviewerId());
                }
            }
            // Pre：点评人姓名批量
            Map<Long, String> nameById = this.practitionerQueryService.mapNamesByIds(reviewerIds);
            Map<String, PrescriptionReviewActiveDto> dtoMap = new HashMap<>();
            for (Map.Entry<String, PrescriptionReviewQueryModel> entry : activeMap.entrySet()) {
                PrescriptionReviewQueryModel model = entry.getValue();
                String reviewerName = null;
                // 有点评人则回填姓名；缺失时 DTO 姓名可空
                if (model != null && model.getReviewerId() != null) {
                    reviewerName = nameById.get(model.getReviewerId());
                }
                // Asm：逐条装配有效点评 DTO
                dtoMap.put(entry.getKey(), this.reviewMapper.toActiveDto(model, reviewerName));
            }
            // Asm：映射响应
            return this.reviewMapper.toMapActiveResponse(dtoMap);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("按处方号映射有效点评失败", ex);
            throw new BusinessException(
                OutpatientPharmacyPrescriptionReviewConstants.EX_PRV_103,
                "查询失败，请稍后重试");
        }
    }

    /**
     * Pre：开方日窗预解析处方号并写入点评 PageQuery（两侧皆空则跳过）。
     *
     * @param query          点评分页查询
     * @param openedDateFrom 开方日起 yyyy-MM-dd
     * @param openedDateTo   开方日止 yyyy-MM-dd
     */
    private void applyOpenedDateFilter(
        PrescriptionReviewPageQuery query,
        String openedDateFrom,
        String openedDateTo) {
        // 两侧皆空：不按开方日收窄
        if (StringUtils.isBlank(openedDateFrom) && StringUtils.isBlank(openedDateTo)) {
            return;
        }
        LocalDate from = LocalDate.parse(openedDateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDate to = LocalDate.parse(openedDateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER);
        LocalDateTime openedFrom = from.atStartOfDay();
        LocalDateTime openedTo = to.atTime(LocalTime.MAX);
        // Pre：药嘱聚合投影窗内已签发方号
        List<String> prescriptionNos =
            this.medicationRequestQueryService.listDistinctSignedPrescriptionNosByOpenedTimeWindow(
                openedFrom,
                openedTo);
        // Asm：写入 IN 条件；空列表 → 点评分页空页
        query.setPrescriptionNosIn(prescriptionNos);
    }

    /**
     * Pre：重点药类型或药品性质预解析药典主键 → 命中处方号，与已有 {@code prescriptionNosIn} 求交。
     *
     * <p>重点药框认四旗；性质框认麻醉/精一/精二；未知码视为无命中（空 IN → 空页）。空筛选跳过。
     * 两框均选时先后求交，等价 AND。</p>
     *
     * @param query       点评分页查询
     * @param keyDrugType {@link KeyDrugType} code（四旗或麻/精之一）
     */
    private void applySpecialDrugTypeFilter(PrescriptionReviewPageQuery query, String keyDrugType) {
        // 空筛选：不按该框收窄
        if (StringUtils.isBlank(keyDrugType)) {
            return;
        }
        KeyDrugType type = EnumUtil.fromCode(KeyDrugType.class, keyDrugType.trim());
        // 点评页开放七类（四旗 + 麻/精）；其余码 → 空结果
        if (type != KeyDrugType.BASIC
            && type != KeyDrugType.NATIONAL_MONITOR
            && type != KeyDrugType.CENTRAL_PURCHASE
            && type != KeyDrugType.ANTIBIOTIC
            && type != KeyDrugType.NARCOTIC
            && type != KeyDrugType.PSYCH_ONE
            && type != KeyDrugType.PSYCH_TWO) {
            query.setPrescriptionNosIn(List.of());
            return;
        }
        // Pre：药典主键（B.6.4 本聚合）
        List<Long> medicationIds = this.medicationDefinitionQueryService.listIdsByKeyDrugType(type.getCode());
        // 目录无命中 → 点评空页
        if (medicationIds == null || medicationIds.isEmpty()) {
            query.setPrescriptionNosIn(List.of());
            return;
        }
        // Pre：药嘱侧投影含该类药品的已签发方号（不限开立日；与开方日窗再求交）
        List<String> bySpecialDrug =
            this.medicationRequestQueryService.listSignedPrescriptionNosByMedicationIdsInOpenedWindow(
                medicationIds,
                null,
                null);
        this.intersectPrescriptionNosIn(query, bySpecialDrug);
    }

    /**
     * 将候选处方号与 PageQuery 已有 {@code prescriptionNosIn} 求交后写回。
     *
     * @param query               点评分页查询
     * @param candidatePrescriptionNos 新候选方号；null 视为空
     */
    private void intersectPrescriptionNosIn(
        PrescriptionReviewPageQuery query,
        List<String> candidatePrescriptionNos) {
        List<String> candidates = candidatePrescriptionNos;
        if (candidates == null) {
            candidates = List.of();
        }
        List<String> existing = query.getPrescriptionNosIn();
        // 尚无开方日等预筛：直接写入特殊药方号
        if (existing == null) {
            query.setPrescriptionNosIn(candidates);
            return;
        }
        // 已有 IN（含空列表）：与候选求交；空 ∩ 任意 = 空
        if (existing.isEmpty() || candidates.isEmpty()) {
            query.setPrescriptionNosIn(List.of());
            return;
        }
        LinkedHashSet<String> candidateSet = new LinkedHashSet<>(candidates);
        List<String> intersected = new ArrayList<>();
        for (String prescriptionNo : existing) {
            // 空白方号跳过
            if (StringUtils.isBlank(prescriptionNo)) {
                continue;
            }
            if (candidateSet.contains(prescriptionNo)) {
                intersected.add(prescriptionNo);
            }
        }
        query.setPrescriptionNosIn(intersected);
    }

    /**
     * Pre+Asm：映射点评字段（含详情用点评人/时间/业务号）并补齐工作表方级指标。
     *
     * @param models 读模型列表
     * @return 列表行；无数据时空列表
     */
    private List<PrescriptionReviewListRowDto> mapListRows(List<PrescriptionReviewQueryModel> models) {
        // 无读模型：空列表，避免 enrichment 空转
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> reviewerIds = new LinkedHashSet<>();
        for (PrescriptionReviewQueryModel model : models) {
            // 页内点评人去重，供一次 mapNamesByIds（详情展示点评人）
            if (model != null && model.getReviewerId() != null) {
                reviewerIds.add(model.getReviewerId());
            }
        }
        Map<Long, String> nameById = this.practitionerQueryService.mapNamesByIds(reviewerIds);
        List<PrescriptionReviewListRowDto> rows = new ArrayList<>(models.size());
        for (PrescriptionReviewQueryModel model : models) {
            String reviewerName = null;
            // 有点评人则取姓名；缺失时详情姓名列可空
            if (model != null && model.getReviewerId() != null) {
                reviewerName = nameById.get(model.getReviewerId());
            }
            PrescriptionReviewListRowDto row = this.reviewMapper.toListRowDto(model, reviewerName);
            // 映射失败跳过，避免空行进入 enrichment
            if (row != null) {
                rows.add(row);
            }
        }
        // Pre+Asm：方级年龄/诊断/品种/金额等（跨聚合，B.6.4 在应用层）
        this.listEnricher.enrichWorksheetColumns(rows);
        return rows;
    }
}
