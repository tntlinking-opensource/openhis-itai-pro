/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.service;

import com.openhis.health.module.querysupport.nursestation.NurseStationEncounterVisibilitySupport;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.assembler.TemperatureSheetGridAssembler;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetFillSlotDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetGridAssemblyResult;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetPaginationDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto.TemperatureSheetTemperaturePointDto;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.mapper.TemperatureSheetMapper;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.request.GetNurseStationTemperatureSheetDataRequest;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.request.GetNurseStationTemperatureSheetPrintDataRequest;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.request.LoadNurseStationTemperatureSheetInitRequest;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.request.SaveNurseStationTemperatureSheetAtRecordedTimeRequest;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.response.GetNurseStationTemperatureSheetDataResponse;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.response.LoadNurseStationTemperatureSheetInitResponse;
import com.openhis.health.application.inpatient.nursestation.temperaturesheet.response.SaveNurseStationTemperatureSheetAtRecordedTimeResponse;
import com.openhis.health.module.common.assembler.nursestation.TemperatureSheetDetailItemSupport;
import com.openhis.health.module.common.assembler.nursestation.TemperatureSheetRecordedAtSupport;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.openhis.health.domain.diagnosis.vitalsign.enums.VitalSignTypeEnum;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignTemperaturePointQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignDomainService;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.module.common.assembler.option.TerminologyOptionLoader;
import com.openhis.health.module.common.validator.CurrentPractitionerResolver;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 住院护士站体温单应用编排（IF-IP-TMP-01～04，AS-IP-NUR-007）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>读侧：曲线仅主表体温；网格读主表+明细；派生住院/周次/术后天数。</li>
 *   <li>写侧：按记录时点 upsert 三测单；22:00 槽合并补充记录。</li>
 * </ul>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为
 * {@code POST /api/app/nurseStationTemperatureSheet/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationTemperatureSheetAppService {

    /** 体温单 Request/Command/Response 映射。 */
    private final TemperatureSheetMapper temperatureSheetMapper;

    /** 日×6 槽宽表与派生元数据装配。 */
    private final TemperatureSheetGridAssembler temperatureSheetGridAssembler;

    /** 记录时点与窗口辅助。 */
    private final TemperatureSheetRecordedAtSupport temperatureSheetRecordedAtSupport;

    /** 护士站就诊可见性 Gate。 */
    private final NurseStationEncounterVisibilitySupport nurseStationEncounterVisibilitySupport;

    /** 生命体征读侧。 */
    private final VitalSignQueryService vitalSignQueryService;

    /** 生命体征写侧。 */
    private final VitalSignDomainService vitalSignDomainService;

    /** 就诊读侧（活动床等）。 */
    private final EncounterQueryService encounterQueryService;

    /** 手术/Procedure 读侧（术后天数）。 */
    private final ProcedureQueryService procedureQueryService;

    /** 业务单号序列。 */
    private final BusinessSequenceService businessSequenceService;

    /** 写路径操作人解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 写路径记录人（Practitioner）解析。 */

    /** 术语选项加载。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * IF-IP-TMP-01：体温单 Tab 页初始化（三测槽、窗口与特殊事件术语选项）。
     *
     * @param request 可空；无业务字段
     * @return 页初始化配置
     */
    public LoadNurseStationTemperatureSheetInitResponse loadNurseStationTemperatureSheetInit(
        LoadNurseStationTemperatureSheetInitRequest request) {
        // Pre：加载 special_event_code 术语供前端下拉与图表文案
        List<OptionDto> specialEventOptions = terminologyOptionLoader.loadOptions(
            TerminologyCodeSystemNameConstants.SPECIAL_EVENT_CODE);
        // Asm：静态配置 + 术语选项
        return temperatureSheetMapper.buildInitResponse(specialEventOptions);
    }

    /**
     * IF-IP-TMP-02：加载曲线、网格、分页与可选录入回显。
     *
     * @param request 就诊、窗口与页码
     * @return 体温单读侧数据
     * @throws BusinessException 就诊不可见（EX-IP-TMP-102）
     */
    public GetNurseStationTemperatureSheetDataResponse getNurseStationTemperatureSheetData(
        GetNurseStationTemperatureSheetDataRequest request) {
        // Gate：就诊须在护士站可见范围（BR-IP-TMP-001）
        InpatientEncounterContextQueryModel encounterSnapshot =
            nurseStationEncounterVisibilitySupport.assertVisible(
                request.getEncounterId(),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_TMP_102,
                "请先选择患者");
        // Pre：由 windowRange + pageIndex 计算 dateFrom/dateTo 后进入读路径
        return buildTemperatureSheetDataResponse(
            request.getEncounterId(),
            request.getWindowRange(),
            request.getPageIndex(),
            request.getFocusRecordDate(),
            encounterSnapshot);
    }

    /**
     * IF-IP-TMP-04：打印读侧（与 IF-02 同读路径，按打印范围收窄日期）。
     *
     * @param request 就诊与打印范围
     * @return 打印数据集（结构与 IF-02 一致）
     * @throws BusinessException 就诊不可见（EX-IP-TMP-102）
     */
    public GetNurseStationTemperatureSheetDataResponse getNurseStationTemperatureSheetPrintData(
        GetNurseStationTemperatureSheetPrintDataRequest request) {
        // Gate：打印不改库，但仍须可见性 Gate（BR-IP-TMP-001）
        InpatientEncounterContextQueryModel encounterSnapshot =
            nurseStationEncounterVisibilitySupport.assertVisible(
                request.getEncounterId(),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_TMP_102,
                "请先选择患者");
        int pageIndex = 0;
        if (request.getPageIndex() != null) {
            pageIndex = request.getPageIndex();
        }
        // 业务分支：打印全部时从入科日至今日一次加载
        if (InpatientNurseStationConstants.TEMPERATURE_SHEET_PRINT_SCOPE_ALL.equals(
            trimToNull(request.getPrintScope()))) {
            pageIndex = 0;
            WindowPage windowPage = resolveAllPagesWindow(encounterSnapshot, request.getWindowRange());
            return buildTemperatureSheetDataResponse(
                request.getEncounterId(),
                request.getWindowRange(),
                pageIndex,
                null,
                encounterSnapshot,
                windowPage.dateFrom,
                windowPage.dateTo,
                windowPage.totalPages);
        }
        // 业务分支：打印当前页与 IF-02 共用分页窗口
        return buildTemperatureSheetDataResponse(
            request.getEncounterId(),
            request.getWindowRange(),
            pageIndex,
            null,
            encounterSnapshot);
    }

    /**
     * IF-IP-TMP-03：按记录时点保存/覆盖三测单。
     *
     * @param request 单槽录入与可选补充块
     * @return 主表 id、单号与下一槽
     * @throws BusinessException EX-IP-TMP-101～204
     */
    public SaveNurseStationTemperatureSheetAtRecordedTimeResponse saveNurseStationTemperatureSheetAtRecordedTime(
        SaveNurseStationTemperatureSheetAtRecordedTimeRequest request) {
        // Gate：可见性 + 登录 Practitioner（BR-IP-TMP-001、010）
        nurseStationEncounterVisibilitySupport.assertVisible(
            request.getEncounterId(),
            request.getPatientId(),
            InpatientNurseStationConstants.EX_IP_TMP_102,
            "请先选择患者");
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_TMP_101,
            "请重新登录后再保存");
        Long recorderId = operatorId;
        // Pre：组装合法记录时点（BR-IP-TMP-003）
        LocalDateTime recordedAt = temperatureSheetRecordedAtSupport.toRecordedAt(
            request.getRecordDate(), request.getTimeSlot());
        // Pre：至少一项有效测量（BR-IP-TMP-004 / BR-VS-008）
        validateHasBusinessContent(request);
        String typeEnum = VitalSignTypeEnum.TEMPERATURE_SHEET.getCode();
        // Pre：定位已有主表首条 id（BR-IP-TMP-005）
        Long resolvedVitalSignId = resolveExistingVitalSignId(request, recordedAt, typeEnum);
        String busNo = resolveBusNoForSave(resolvedVitalSignId, request, recordedAt, typeEnum);
        // Pre：活动床 location（BR-IP-TMP-006；未分床可空）
        Long locationId = resolveActiveBedLocationId(request.getEncounterId());
        // Pre：Request → Command（含 22:00 补充明细）
        SaveVitalSignCommand command = temperatureSheetMapper.toSaveCommand(
            request,
            recordedAt,
            resolvedVitalSignId,
            busNo,
            locationId,
            recorderId,
            operatorId,
            typeEnum);
        Long savedId;
        try {
            // Cmd：领域层主表+明细覆盖写（BR-VS-005）
            savedId = vitalSignDomainService.saveVitalSign(command);
        } catch (BusinessException ex) {
            throw mapSaveBusinessException(ex);
        } catch (Exception ex) {
            log.error("temperature sheet save failed, encounterId={}", request.getEncounterId(), ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_TMP_204,
                "保存失败，请稍后重试");
        }
        // Asm：返回下一槽供前端聚焦
        String nextTimeSlot = temperatureSheetRecordedAtSupport.resolveNextTimeSlot(request.getTimeSlot());
        return temperatureSheetMapper.toSaveResponse(savedId, busNo, nextTimeSlot);
    }

    /**
     * 构建 IF-02/04 读侧响应（窗口由 pageIndex 计算）。
     *
     * @param encounterId       就诊 id
     * @param windowRange       窗口 code
     * @param pageIndex         页码
     * @param focusRecordDate   录入回显日
     * @param encounterSnapshot 可见就诊快照
     * @return 读侧响应
     */
    private GetNurseStationTemperatureSheetDataResponse buildTemperatureSheetDataResponse(
        Long encounterId,
        String windowRange,
        Integer pageIndex,
        LocalDate focusRecordDate,
        InpatientEncounterContextQueryModel encounterSnapshot) {
        // Pre：由 windowRange + pageIndex 计算分页窗口 dateFrom/dateTo
        WindowPage windowPage = resolveWindowPage(encounterSnapshot, windowRange, pageIndex);
        int resolvedPageIndex = 0;
        if (pageIndex != null) {
            resolvedPageIndex = pageIndex;
        }
        return buildTemperatureSheetDataResponse(
            encounterId,
            windowRange,
            resolvedPageIndex,
            focusRecordDate,
            encounterSnapshot,
            windowPage.dateFrom,
            windowPage.dateTo,
            windowPage.totalPages);
    }

    /**
     * 构建 IF-02/04 读侧响应（指定 dateFrom/dateTo）。
     *
     * @param encounterId       就诊 id
     * @param windowRange       窗口 code（分页元数据用）
     * @param pageIndex         页码
     * @param focusRecordDate   录入回显日
     * @param encounterSnapshot 就诊快照
     * @param dateFrom          日期下界
     * @param dateTo            日期上界
     * @param totalPages        总页数
     * @return 读侧响应
     */
    private GetNurseStationTemperatureSheetDataResponse buildTemperatureSheetDataResponse(
        Long encounterId,
        String windowRange,
        int pageIndex,
        LocalDate focusRecordDate,
        InpatientEncounterContextQueryModel encounterSnapshot,
        LocalDate dateFrom,
        LocalDate dateTo,
        int totalPages) {
        String typeEnum = VitalSignTypeEnum.TEMPERATURE_SHEET.getCode();
        LocalDateTime recordedFrom = dateFrom.atStartOfDay();
        LocalDateTime recordedTo = dateTo.atTime(23, 59, 59);
        // Qry：曲线仅主表体温（BR-IP-TMP-008）
        List<VitalSignTemperaturePointQueryModel> temperatureModels =
            vitalSignQueryService.listTemperaturePointsByEncounterId(
                temperatureSheetMapper.toTemperatureSeriesQuery(
                    encounterId, typeEnum, recordedFrom, recordedTo));
        // Qry：同时段主表与明细（禁止 N+1）
        List<VitalSignHeaderQueryModel> headers = vitalSignQueryService.listHeadersByEncounterId(
            temperatureSheetMapper.toEncounterQuery(encounterId, typeEnum, recordedFrom, recordedTo));
        List<VitalSignDetailRowQueryModel> details = vitalSignQueryService.listDetailsByEncounterId(
            temperatureSheetMapper.toEncounterQuery(encounterId, typeEnum, recordedFrom, recordedTo));
        LocalDate admissionDate = resolveAdmissionDate(encounterSnapshot);
        List<LocalDate> surgeryDates = loadSurgeryDates(encounterId);
        // Asm：网格与派生 meta（SDD §3.2 步骤 7 assemble）
        TemperatureSheetGridAssemblyResult gridAssembly = temperatureSheetGridAssembler.assemble(
            dateFrom, dateTo, admissionDate, surgeryDates, headers, details);
        List<TemperatureSheetTemperaturePointDto> temperaturePoints =
            temperatureSheetMapper.toTemperaturePointDtos(temperatureModels);
        TemperatureSheetPaginationDto pagination = temperatureSheetMapper.toPaginationDto(
            pageIndex, totalPages, dateFrom, dateTo);
        GetNurseStationTemperatureSheetDataResponse response = new GetNurseStationTemperatureSheetDataResponse();
        response.setTemperaturePoints(temperaturePoints);
        response.setGridRows(gridAssembly.getGridRows());
        response.setPagination(pagination);
        response.setDerivedMeta(gridAssembly.getDerivedMeta());
        // 业务分支：指定回显日时才加载 6 槽+补充项（SDD §3.2 步骤 8）
        if (focusRecordDate != null) {
            VitalSignFillScopeData fillScope = loadFillScopeData(
                encounterId, typeEnum, focusRecordDate, headers, details);
            List<TemperatureSheetFillSlotDto> fillSlots = temperatureSheetGridAssembler.assembleFillSlots(
                focusRecordDate, fillScope.headers, fillScope.details);
            response.setFillSlots(fillSlots);
        }
        return response;
    }

    /**
     * 加载录入回显所需主表/明细（优先复用已查同时段数据，不足时补查 focus 日）。
     *
     * @param encounterId     就诊 id
     * @param typeEnum        类型
     * @param focusRecordDate 回显日
     * @param cachedHeaders   已查主表
     * @param cachedDetails   已查明细
     * @return 回显范围数据
     */
    private VitalSignFillScopeData loadFillScopeData(
        Long encounterId,
        String typeEnum,
        LocalDate focusRecordDate,
        List<VitalSignHeaderQueryModel> cachedHeaders,
        List<VitalSignDetailRowQueryModel> cachedDetails) {
        LocalDateTime dayStart = focusRecordDate.atStartOfDay();
        LocalDateTime dayEnd = focusRecordDate.atTime(23, 59, 59);
        List<VitalSignHeaderQueryModel> headers = new ArrayList<>();
        // 优先从当前窗口已查主表中筛出回显日数据，避免重复读库
        if (cachedHeaders != null) {
            for (VitalSignHeaderQueryModel header : cachedHeaders) {
                if (header != null && header.getRecordedAt() != null
                    && !header.getRecordedAt().isBefore(dayStart)
                    && !header.getRecordedAt().isAfter(dayEnd)) {
                    headers.add(header);
                }
            }
        }
        List<VitalSignDetailRowQueryModel> details = new ArrayList<>();
        if (cachedDetails != null) {
            for (VitalSignDetailRowQueryModel detail : cachedDetails) {
                if (detail != null && detail.getRecordedAt() != null
                    && !detail.getRecordedAt().isBefore(dayStart)
                    && !detail.getRecordedAt().isAfter(dayEnd)) {
                    details.add(detail);
                }
            }
        }
        // 业务分支：当前窗口未覆盖回显日时补查该日数据
        if (headers.isEmpty()) {
            headers = vitalSignQueryService.listHeadersByEncounterId(
                temperatureSheetMapper.toEncounterQuery(encounterId, typeEnum, dayStart, dayEnd));
            details = vitalSignQueryService.listDetailsByEncounterId(
                temperatureSheetMapper.toEncounterQuery(encounterId, typeEnum, dayStart, dayEnd));
        }
        VitalSignFillScopeData scope = new VitalSignFillScopeData();
        scope.headers = headers;
        scope.details = details;
        return scope;
    }

    /**
     * 解析分页窗口（入科日起算，pageIndex 0-based）。
     *
     * @param encounterSnapshot 就诊快照
     * @param windowRange       窗口 code
     * @param pageIndex         页码（可空）
     * @return 窗口页描述
     */
    private WindowPage resolveWindowPage(
        InpatientEncounterContextQueryModel encounterSnapshot,
        String windowRange,
        Integer pageIndex) {
        int page = 0;
        if (pageIndex != null) {
            page = pageIndex;
        }
        int windowDays = temperatureSheetRecordedAtSupport.resolveWindowDays(windowRange);
        LocalDate admissionDate = resolveAdmissionDate(encounterSnapshot);
        LocalDate endAnchor = LocalDate.now();
        long totalDays = ChronoUnit.DAYS.between(admissionDate, endAnchor) + 1;
        if (totalDays < 1) {
            totalDays = 1;
        }
        int totalPages = (int) ((totalDays + windowDays - 1) / windowDays);
        if (totalPages < 1) {
            totalPages = 1;
        }
        LocalDate dateFrom = admissionDate.plusDays((long) page * windowDays);
        LocalDate dateTo = dateFrom.plusDays(windowDays - 1);
        if (dateTo.isAfter(endAnchor)) {
            dateTo = endAnchor;
        }
        WindowPage windowPage = new WindowPage();
        windowPage.dateFrom = dateFrom;
        windowPage.dateTo = dateTo;
        windowPage.totalPages = totalPages;
        return windowPage;
    }

    /**
     * 打印「全部」范围：入科日至今日单页。
     *
     * @param encounterSnapshot 就诊快照
     * @param windowRange       窗口 code（用于合法校验）
     * @return 窗口页描述
     */
    private WindowPage resolveAllPagesWindow(
        InpatientEncounterContextQueryModel encounterSnapshot,
        String windowRange) {
        temperatureSheetRecordedAtSupport.resolveWindowDays(windowRange);
        LocalDate admissionDate = resolveAdmissionDate(encounterSnapshot);
        LocalDate endAnchor = LocalDate.now();
        WindowPage windowPage = new WindowPage();
        windowPage.dateFrom = admissionDate;
        windowPage.dateTo = endAnchor;
        windowPage.totalPages = 1;
        return windowPage;
    }

    /**
     * 入科日（local date）；缺 startTime 时回退今日。
     *
     * @param encounterSnapshot 就诊快照
     * @return 入科日
     */
    private LocalDate resolveAdmissionDate(InpatientEncounterContextQueryModel encounterSnapshot) {
        if (encounterSnapshot != null && encounterSnapshot.getStartTime() != null) {
            return encounterSnapshot.getStartTime().toLocalDate();
        }
        return LocalDate.now();
    }

    /**
     * 加载就诊手术日期列表（Procedure occurrenceTime 派生）。
     *
     * @param encounterId 就诊 id
     * @return 手术 local date 列表
     */
    private List<LocalDate> loadSurgeryDates(Long encounterId) {
        List<LocalDate> surgeryDates = new ArrayList<>();
        List<Procedure> procedures = procedureQueryService.listByEncounterIds(
            Collections.singletonList(encounterId));
        // 遍历 Procedure 取 occurrenceTime 作为手术日锚点
        for (Procedure procedure : procedures) {
            if (procedure == null || procedure.getOccurrenceTime() == null) {
                continue;
            }
            surgeryDates.add(procedure.getOccurrenceTime().toLocalDate());
        }
        return surgeryDates;
    }

    /**
     * 保存前内容非空校验（BR-IP-TMP-004）。
     *
     * @param request 保存请求
     */
    private void validateHasBusinessContent(SaveNurseStationTemperatureSheetAtRecordedTimeRequest request) {
        if (TemperatureSheetDetailItemSupport.hasAnyDetailContent(request.getDetailItems())) {
            return;
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_TMP_202,
            "请至少填写一项明细");
    }

    /**
     * 定位同就诊+同时点+类型已有主表首条 id（BR-IP-TMP-005）。
     *
     * @param request    保存请求
     * @param recordedAt 记录时点
     * @param typeEnum   类型
     * @return 主表 id 或 null
     */
    private Long resolveExistingVitalSignId(
        SaveNurseStationTemperatureSheetAtRecordedTimeRequest request,
        LocalDateTime recordedAt,
        String typeEnum) {
        // 业务分支：请求已带 id 则直接 upsert，不再查库定位
        if (request.getVitalSignId() != null) {
            return request.getVitalSignId();
        }
        boolean exists = vitalSignQueryService.existsAtRecordedTime(
            request.getEncounterId(), recordedAt, null);
        if (!exists) {
            return null;
        }
        List<VitalSignHeaderQueryModel> headers = vitalSignQueryService.listHeadersByEncounterId(
            temperatureSheetMapper.toEncounterQuery(
                request.getEncounterId(), typeEnum, recordedAt, recordedAt));
        if (headers.isEmpty()) {
            return null;
        }
        return headers.get(0).getId();
    }

    /**
     * 新单生成 busNo；更新沿用库内单号（BR-IP-TMP-006 配套）。
     *
     * @param vitalSignId 主表 id
     * @param request     保存请求
     * @param recordedAt  记录时点
     * @param typeEnum    类型
     * @return 业务单号
     */
    private String resolveBusNoForSave(
        Long vitalSignId,
        SaveNurseStationTemperatureSheetAtRecordedTimeRequest request,
        LocalDateTime recordedAt,
        String typeEnum) {
        // 业务分支：更新路径沿用库内 busNo，禁止换新单号
        if (vitalSignId != null) {
            List<VitalSignHeaderQueryModel> headers = vitalSignQueryService.listHeadersByEncounterId(
                temperatureSheetMapper.toEncounterQuery(
                    request.getEncounterId(), typeEnum, recordedAt, recordedAt));
            for (VitalSignHeaderQueryModel header : headers) {
                if (header != null && vitalSignId.equals(header.getId())) {
                    return header.getBusNo();
                }
            }
        }
        return businessSequenceService.getSeqByDay(
            AggregateTypeConstants.VITAL_SIGN, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 解析当前活动床 location id；未分床返回 null（BR-IP-TMP-006）。
     *
     * @param encounterId 就诊 id
     * @return location id 或 null
     */
    private Long resolveActiveBedLocationId(Long encounterId) {
        List<EncounterLocation> activeLocations = encounterQueryService
            .listActiveEncounterLocationEntitiesByEncounterIds(Collections.singletonList(encounterId));
        // 取首个活动床 location；未分床时保持 null（BR-IP-TMP-006）
        for (EncounterLocation location : activeLocations) {
            if (location != null && location.getLocationId() != null) {
                return location.getLocationId();
            }
        }
        return null;
    }

    /**
     * 领域写异常 → 应用 EX-IP-TMP 码。
     *
     * @param ex 领域异常
     * @return 映射后的 BusinessException
     */
    private BusinessException mapSaveBusinessException(BusinessException ex) {
        // 业务分支：术语 code 未启用 → EX-IP-TMP-203
        if (ErrorCodeConstants.VITAL_SIGN_INVALID_CODE.equals(ex.getErrorCode())) {
            return new BusinessException(
                InpatientNurseStationConstants.EX_IP_TMP_203,
                "存在未启用的体征项，请联系管理员");
        }
        // 业务分支：领域参数类错误再细分为日期非法或内容为空
        if (ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String message = ex.getMessage();
            if (StringUtils.isNotBlank(message) && message.contains("有效")) {
                return new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TMP_202,
                    "请至少填写一项生命体征");
            }
            return new BusinessException(
                InpatientNurseStationConstants.EX_IP_TMP_201,
                "请检查日期与测量时点");
        }
        log.warn("temperature sheet domain save rejected: {}", ex.getMessage());
        return new BusinessException(
            InpatientNurseStationConstants.EX_IP_TMP_204,
            "保存失败，请稍后重试");
    }

    /** 分页窗口计算结果。 */
    private static final class WindowPage {
        /** 日期下界。 */
        private LocalDate dateFrom;
        /** 日期上界。 */
        private LocalDate dateTo;
        /** 总页数。 */
        private int totalPages;
    }

    /** 录入回显范围数据。 */
    private static final class VitalSignFillScopeData {
        /** 主表。 */
        private List<VitalSignHeaderQueryModel> headers;
        /** 明细。 */
        private List<VitalSignDetailRowQueryModel> details;
    }
}
