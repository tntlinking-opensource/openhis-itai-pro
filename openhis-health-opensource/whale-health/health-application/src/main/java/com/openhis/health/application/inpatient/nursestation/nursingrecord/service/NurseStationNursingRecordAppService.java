/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.nursingrecord.service;

import com.openhis.health.application.inpatient.nursestation.nursingrecord.assembler.NursingRecordRowAssembler;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.dto.NursingRecordRowDto;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.mapper.NursingRecordMapper;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.DeleteNurseStationNursingRecordRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.GetNurseStationNursingRecordPrintDataRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.ListNurseStationNursingRecordsRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.LoadNurseStationNursingRecordInitRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.SaveNurseStationNursingRecordRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.SaveNurseStationNursingRecordsBatchRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.request.SaveNursingRecordItemRequest;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.response.GetNurseStationNursingRecordPrintDataResponse;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.response.ListNurseStationNursingRecordsResponse;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.response.LoadNurseStationNursingRecordInitResponse;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.response.SaveNurseStationNursingRecordResponse;
import com.openhis.health.application.inpatient.nursestation.nursingrecord.response.SaveNurseStationNursingRecordsBatchResponse;
import com.openhis.health.domain.share.utils.BloodPressureParser;
import com.openhis.health.domain.share.utils.BloodPressureParser.BloodPressureParseResult;
import com.openhis.health.module.querysupport.nursestation.NurseStationEncounterVisibilitySupport;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.diagnosis.vitalsign.command.DeleteVitalSignByIdCommand;
import com.openhis.health.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.openhis.health.domain.diagnosis.vitalsign.enums.VitalSignTypeEnum;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignDetailRowQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignHeaderQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.queries.model.VitalSignWithDetailsQueryModel;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignDomainService;
import com.openhis.health.domain.diagnosis.vitalsign.service.VitalSignQueryService;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.VitalSignConstants;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 住院护士站护理记录应用编排（IF-IP-NRC-01～06，AS-IP-NUR-014）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>读侧：按就诊+日期范围批量读主表/明细，pivot 为 UI 行；纸质抬头取自 Encounter。</li>
 *   <li>写侧：单条/批量 upsert 护理记录；删除校验类型与归属后逻辑删。</li>
 * </ul>
 *
 * <p>接入说明：由 AppServiceControllerRegistrar 动态注册为
 * {@code POST /api/app/nurseStationNursingRecord/{methodName}}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NurseStationNursingRecordAppService {

    /** 护理记录 Request/Command/Response 映射。 */
    private final NursingRecordMapper nursingRecordMapper;

    /** 主表+明细 pivot 装配。 */
    private final NursingRecordRowAssembler nursingRecordRowAssembler;

    /** 护士站就诊可见性 Gate。 */
    private final NurseStationEncounterVisibilitySupport nurseStationEncounterVisibilitySupport;

    /** 生命体征读侧。 */
    private final VitalSignQueryService vitalSignQueryService;

    /** 生命体征写侧。 */
    private final VitalSignDomainService vitalSignDomainService;

    /** 就诊读侧（活动床、纸质抬头）。 */
    private final EncounterQueryService encounterQueryService;

    /** 记录人姓名解析。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 术语选项加载。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 业务单号序列。 */
    private final BusinessSequenceService businessSequenceService;

    /** 写路径操作人解析。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 写路径记录人（Practitioner）解析。 */

    /**
     * IF-IP-NRC-01：护理记录 Tab 页初始化。
     *
     * @param request 可空；无业务字段
     * @return 视图模式、常用体征项与周区间说明
     */
    public LoadNurseStationNursingRecordInitResponse loadNurseStationNursingRecordInit(
        LoadNurseStationNursingRecordInitRequest request) {
        // Pre：加载 vital_signs_code 术语子集供前端下拉
        List<OptionDto> allOptions = terminologyOptionLoader.loadOptions(
            TerminologyCodeSystemNameConstants.VITAL_SIGNS_CODE);
        List<OptionDto> nursingOptions = filterNursingRecordVitalSignOptions(allOptions);
        // Asm：静态配置 + 术语选项
        return nursingRecordMapper.buildInitResponse(nursingOptions);
    }

    /**
     * IF-IP-NRC-02：按就诊与日期范围列表护理记录。
     *
     * @param request 就诊、日期范围与排序
     * @return 行列表与纸质抬头
     * @throws BusinessException 就诊不可见（EX-IP-NRC-102）
     */
    public ListNurseStationNursingRecordsResponse listNurseStationNursingRecords(
        ListNurseStationNursingRecordsRequest request) {
        // Gate：就诊须在护士站可见范围（BR-IP-NRC-001）
        InpatientEncounterContextQueryModel encounterSnapshot =
            nurseStationEncounterVisibilitySupport.assertVisible(
                request.getEncounterId(),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_NRC_102,
                "请先选择患者");
        boolean sortAsc = Boolean.TRUE.equals(request.getSortAsc());
        // Qry：批量读主表+明细并 pivot
        List<NursingRecordRowDto> rows = loadNursingRecordRows(
            request.getEncounterId(),
            request.getRecordedAtFrom(),
            request.getRecordedAtTo(),
            sortAsc);
        // Asm：行列表 + 纸质抬头
        ListNurseStationNursingRecordsResponse response = new ListNurseStationNursingRecordsResponse();
        response.setRows(rows);
        response.setPaperHeader(nursingRecordMapper.toPaperHeaderDto(encounterSnapshot));
        return response;
    }

    /**
     * IF-IP-NRC-03：保存/更新单条护理记录。
     *
     * @param request UI 行 + 就诊上下文
     * @return 保存后的行
     * @throws BusinessException EX-IP-NRC-102、201～204
     */
    public SaveNurseStationNursingRecordResponse saveNurseStationNursingRecord(
        SaveNurseStationNursingRecordRequest request) {
        // Cmd+Asm：写路径核心 upsert 后 pivot 回显行
        NursingRecordRowDto savedRow = executeSaveNursingRecord(request, false);
        SaveNurseStationNursingRecordResponse response = new SaveNurseStationNursingRecordResponse();
        response.setRow(savedRow);
        return response;
    }

    /**
     * IF-IP-NRC-04：纸质「保存全部」批量保存。
     *
     * @param request 批量行
     * @return 保存结果
     * @throws BusinessException EX-IP-NRC-205（含 failedIndex）
     */
    public SaveNurseStationNursingRecordsBatchResponse saveNurseStationNursingRecordsBatch(
        SaveNurseStationNursingRecordsBatchRequest request) {
        // Gate：批量写前一次性校验就诊可见（BR-IP-NRC-001；循环内不再重复 Gate，对齐 SDD §3.4）
        nurseStationEncounterVisibilitySupport.assertVisible(
            request.getEncounterId(),
            request.getPatientId(),
            InpatientNurseStationConstants.EX_IP_NRC_102,
            "请先选择患者");
        // Pre：过滤 skeleton 与全空行（BR-IP-NRC-011）
        List<SaveNursingRecordItemRequest> filtered =
            rejectSkeletonAndEmptyNursingRecordRows(request.getRecords());
        List<NursingRecordRowDto> savedRows = new ArrayList<>();
        int index = 0;
        // Loop：逐条执行 IF-03 步骤 2～5（血压解析→Command→校验→写库→Asm）
        for (SaveNursingRecordItemRequest item : filtered) {
            SaveNurseStationNursingRecordRequest singleRequest =
                nursingRecordMapper.toSaveRequest(request.getEncounterId(), request.getPatientId(), item);
            try {
                NursingRecordRowDto savedRow = executeSaveNursingRecord(singleRequest, true);
                savedRows.add(savedRow);
            } catch (BusinessException ex) {
                // Fail：任一行失败携带 1-based 行号（failedIndex）并回滚整批（EX-IP-NRC-205）
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_NRC_205,
                    "第 " + (index + 1) + " 行保存失败：" + ex.getMessage(),
                    ex);
            } catch (Exception ex) {
                log.error("nursing record batch save failed at index={}, encounterId={}",
                    index, request.getEncounterId(), ex);
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_NRC_205,
                    "第 " + (index + 1) + " 行保存失败，请稍后重试");
            }
            index++;
        }
        // Asm：返回成功行数与回显列表
        SaveNurseStationNursingRecordsBatchResponse response = new SaveNurseStationNursingRecordsBatchResponse();
        response.setSavedCount(savedRows.size());
        response.setRows(savedRows);
        return response;
    }

    /**
     * IF-IP-NRC-05：删除一条护理记录。
     *
     * @param request 主表 id 与就诊
     * @throws BusinessException EX-IP-NRC-102、301、302
     */
    public void deleteNurseStationNursingRecord(DeleteNurseStationNursingRecordRequest request) {
        // Gate：可见性（BR-IP-NRC-001）
        nurseStationEncounterVisibilitySupport.assertVisible(
            request.getEncounterId(),
            request.getPatientId(),
            InpatientNurseStationConstants.EX_IP_NRC_102,
            "请先选择患者");
        // Pre：校验记录属当前就诊且类型为护理记录（BR-IP-NRC-008）
        VitalSignWithDetailsQueryModel existing =
            vitalSignQueryService.getWithDetailsById(request.getVitalSignId());
        // 业务分支：主表不存在则视为已删或无效 id
        if (existing == null || existing.getHeader() == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_301,
                "记录不存在，请刷新");
        }
        VitalSignHeaderQueryModel header = existing.getHeader();
        // 业务分支：记录须属当前就诊，防止跨就诊误删
        if (!request.getEncounterId().equals(header.getEncounterId())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_301,
                "记录不存在，请刷新");
        }
        // 业务分支：仅护理记录类型允许本接口删除（BR-IP-NRC-008）
        if (!VitalSignTypeEnum.NURSING_RECORD.getCode().equals(header.getTypeEnum())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_301,
                "记录不存在，请刷新");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_NRC_302,
            "请重新登录后再删除");
        DeleteVitalSignByIdCommand command = new DeleteVitalSignByIdCommand();
        command.setId(request.getVitalSignId());
        command.setOperatorId(operatorId);
        try {
            // Cmd：逻辑删除主表及明细
            vitalSignDomainService.deleteVitalSignById(command);
        } catch (BusinessException ex) {
            // 业务分支：领域 NOT_FOUND 映射为应用 301
            if (ErrorCodeConstants.VITAL_SIGN_NOT_FOUND.equals(ex.getErrorCode())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_NRC_301,
                    "记录不存在，请刷新");
            }
            throw ex;
        } catch (Exception ex) {
            log.error("nursing record delete failed, vitalSignId={}", request.getVitalSignId(), ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_302,
                "删除失败，请稍后重试");
        }
    }

    /**
     * IF-IP-NRC-06：护理记录打印读侧。
     *
     * @param request 就诊与日期范围
     * @return 打印数据集
     * @throws BusinessException 就诊不可见（EX-IP-NRC-102）
     */
    public GetNurseStationNursingRecordPrintDataResponse getNurseStationNursingRecordPrintData(
        GetNurseStationNursingRecordPrintDataRequest request) {
        // Gate：打印不改库，但仍须可见性 Gate
        InpatientEncounterContextQueryModel encounterSnapshot =
            nurseStationEncounterVisibilitySupport.assertVisible(
                request.getEncounterId(),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_NRC_102,
                "请先选择患者");
        boolean sortAsc = true;
        // 业务分支：请求显式指定排序时覆盖纸质默认升序
        if (request.getSortAsc() != null) {
            sortAsc = request.getSortAsc();
        }
        // Qry：与 IF-02 同读路径
        List<NursingRecordRowDto> rows = loadNursingRecordRows(
            request.getEncounterId(),
            request.getRecordedAtFrom(),
            request.getRecordedAtTo(),
            sortAsc);
        // Asm：附加打印模板 code 与纸质抬头
        GetNurseStationNursingRecordPrintDataResponse response = new GetNurseStationNursingRecordPrintDataResponse();
        response.setTemplateCode(InpatientNurseStationConstants.NURSING_RECORD_PRINT_TEMPLATE_CODE);
        response.setRows(rows);
        response.setPaperHeader(nursingRecordMapper.toPaperHeaderDto(encounterSnapshot));
        return response;
    }

    /**
     * IF-IP-NRC-03 写路径核心（Gate→Pre→Cmd→Asm）。
     *
     * @param request            单条保存请求
     * @param skipVisibilityGate 批量 IF-04 为 true（Gate 已在批入口执行）
     * @return 保存后 UI 行
     * @throws BusinessException EX-IP-NRC-102、201～204
     */
    private NursingRecordRowDto executeSaveNursingRecord(
        SaveNurseStationNursingRecordRequest request,
        boolean skipVisibilityGate) {
        // Gate：单条保存须校验可见性；批量循环内跳过（SDD §3.4）
        if (!skipVisibilityGate) {
            nurseStationEncounterVisibilitySupport.assertVisible(
                request.getEncounterId(),
                request.getPatientId(),
                InpatientNurseStationConstants.EX_IP_NRC_102,
                "请先选择患者");
        }
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientNurseStationConstants.EX_IP_NRC_204,
            "请重新登录后再保存");
        Long recorderId = currentPractitionerResolver.requirePractitionerId(
            InpatientNurseStationConstants.EX_IP_NRC_204,
            "请重新登录后再保存");
        // Pre：合并日期时间为 recordedAt（BR-IP-NRC-003 前置）
        LocalDateTime recordedAt = nursingRecordMapper.toRecordedAt(
            request.getRecordDate(), request.getRecordTime());
        // 业务分支：日期或时间缺失则拒绝落库
        if (recordedAt == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_201,
                "请填写记录日期与时间");
        }
        // Pre：拆分血压字符串为 SBP/DBP 或 BP
        BloodPressureParseResult bloodPressure =
            BloodPressureParser.parse(request.getBloodPressure());
        // Pre：至少一项有效内容（BR-IP-NRC-003 / BR-VS-008）
        validateHasBusinessContent(request, bloodPressure);
        String typeEnum = VitalSignTypeEnum.NURSING_RECORD.getCode();
        Long resolvedVitalSignId = request.getVitalSignId();
        String busNo = resolveBusNoForSave(resolvedVitalSignId);
        Long locationId = resolveActiveBedLocationId(request.getEncounterId());
        // Pre：Request → Command
        SaveVitalSignCommand command = nursingRecordMapper.toSaveCommand(
            request,
            recordedAt,
            resolvedVitalSignId,
            busNo,
            locationId,
            recorderId,
            operatorId,
            typeEnum,
            bloodPressure);
        Long savedId;
        try {
            // Cmd：领域层主表+明细覆盖写（BR-VS-005）
            savedId = vitalSignDomainService.saveVitalSign(command);
        } catch (BusinessException ex) {
            throw mapSaveBusinessException(ex);
        } catch (Exception ex) {
            log.error("nursing record save failed, encounterId={}", request.getEncounterId(), ex);
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_204,
                "保存失败，请稍后重试");
        }
        // Asm：回读最新主表+明细 pivot 为 UI 行
        VitalSignWithDetailsQueryModel saved = vitalSignQueryService.getWithDetailsById(savedId);
        Map<Long, String> recorderNames = buildRecorderNameMap(
            Collections.singletonList(saved.getHeader()));
        return nursingRecordRowAssembler.toRowDto(
            saved.getHeader(),
            saved.getDetails(),
            recorderNames);
    }

    /**
     * 读侧：批量加载并 pivot 护理记录行。
     *
     * @param encounterId    就诊 id
     * @param recordedFrom   日期下界
     * @param recordedTo     日期上界
     * @param sortAsc        是否升序
     * @return UI 行列表
     */
    private List<NursingRecordRowDto> loadNursingRecordRows(
        Long encounterId,
        LocalDate recordedFrom,
        LocalDate recordedTo,
        boolean sortAsc) {
        String typeEnum = VitalSignTypeEnum.NURSING_RECORD.getCode();
        LocalDateTime fromDateTime = null;
        LocalDateTime toDateTime = null;
        // 业务分支：按自然日收窄 recorded_at 查询窗口
        if (recordedFrom != null) {
            fromDateTime = recordedFrom.atStartOfDay();
        }
        if (recordedTo != null) {
            toDateTime = recordedTo.atTime(23, 59, 59);
        }
        // Qry：主表列表（type=NURSING_RECORD, range）
        List<VitalSignHeaderQueryModel> headers = vitalSignQueryService.listHeadersByEncounterId(
            nursingRecordMapper.toEncounterQuery(encounterId, typeEnum, fromDateTime, toDateTime));
        // Qry：同 filter 批量明细（禁止 N+1）
        List<VitalSignDetailRowQueryModel> details = vitalSignQueryService.listDetailsByEncounterId(
            nursingRecordMapper.toEncounterQuery(encounterId, typeEnum, fromDateTime, toDateTime));
        Map<Long, String> recorderNames = buildRecorderNameMap(headers);
        // Asm：pivot 合并血压等明细列
        return nursingRecordRowAssembler.pivot(headers, details, sortAsc, recorderNames);
    }

    /**
     * 保存前内容非空校验（BR-IP-NRC-003）。
     *
     * @param request       保存请求
     * @param bloodPressure 血压解析结果
     */
    private void validateHasBusinessContent(
        SaveNurseStationNursingRecordRequest request,
        BloodPressureParseResult bloodPressure) {
        // 业务分支：主表体温非空即满足 BR-VS-008
        if (trimToNull(request.getTemperature()) != null) {
            return;
        }
        // 业务分支：仅病情观察（remark）非空亦可保存
        if (trimToNull(request.getConditionMeasure()) != null) {
            return;
        }
        // 业务分支：血压解析结果计为有效明细
        if (bloodPressure != null && bloodPressure.hasAnyValue()) {
            return;
        }
        // 业务分支：任一明细 UI 字段非空即满足
        if (trimToNull(request.getConsciousness()) != null
            || trimToNull(request.getPulse()) != null
            || trimToNull(request.getRespiratory()) != null
            || trimToNull(request.getHeartRate()) != null
            || trimToNull(request.getPupilLeft()) != null
            || trimToNull(request.getPupilRight()) != null
            || trimToNull(request.getOxygenSaturation()) != null
            || trimToNull(request.getOxygenRate()) != null
            || trimToNull(request.getOxygenMode()) != null
            || trimToNull(request.getOxygenFlow()) != null
            || trimToNull(request.getIntakeName()) != null
            || trimToNull(request.getIntakeAmount()) != null
            || trimToNull(request.getIntakeRoute()) != null
            || trimToNull(request.getOutputName()) != null
            || trimToNull(request.getOutputAmount()) != null
            || trimToNull(request.getOutputColorCharacter()) != null
            || trimToNull(request.getSkinStatus()) != null
            || trimToNull(request.getTubeCare()) != null) {
            return;
        }
        throw new BusinessException(
            InpatientNurseStationConstants.EX_IP_NRC_202,
            "请至少填写一项观察内容");
    }

    /**
     * 新单生成 busNo；更新沿用库内单号（BR-IP-NRC-004）。
     *
     * @param vitalSignId 主表 id（可空）
     * @return 业务单号
     */
    private String resolveBusNoForSave(Long vitalSignId) {
        // 业务分支：更新路径沿用库内 busNo
        if (vitalSignId != null) {
            VitalSignWithDetailsQueryModel existing = vitalSignQueryService.getWithDetailsById(vitalSignId);
            if (existing != null && existing.getHeader() != null) {
                return existing.getHeader().getBusNo();
            }
        }
        return businessSequenceService.getSeqByDay(
            AggregateTypeConstants.VITAL_SIGN, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 解析当前活动床 location id；未分床返回 null。
     *
     * @param encounterId 就诊 id
     * @return location id 或 null
     */
    private Long resolveActiveBedLocationId(Long encounterId) {
        List<EncounterLocation> activeLocations = encounterQueryService
            .listActiveEncounterLocationEntitiesByEncounterIds(Collections.singletonList(encounterId));
        // 遍历活动床 location，取首个作为落库 locationId
        for (EncounterLocation location : activeLocations) {
            if (location != null && location.getLocationId() != null) {
                return location.getLocationId();
            }
        }
        return null;
    }

    /**
     * 领域写异常 → 应用 EX-IP-NRC 码。
     *
     * @param ex 领域异常
     * @return 映射后的 BusinessException
     */
    private BusinessException mapSaveBusinessException(BusinessException ex) {
        // 业务分支：术语 code 未启用 → EX-IP-NRC-203
        if (ErrorCodeConstants.VITAL_SIGN_INVALID_CODE.equals(ex.getErrorCode())) {
            return new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_203,
                "体征项未启用");
        }
        // 业务分支：领域参数类错误细分为内容为空或时点非法
        if (ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT.equals(ex.getErrorCode())) {
            String message = ex.getMessage();
            // 业务分支：含「有效」文案时映射为无有效内容
            if (StringUtils.isNotBlank(message) && message.contains("有效")) {
                return new BusinessException(
                    InpatientNurseStationConstants.EX_IP_NRC_202,
                    "请至少填写一项观察内容");
            }
            return new BusinessException(
                InpatientNurseStationConstants.EX_IP_NRC_201,
                "请填写记录日期与时间");
        }
        return ex;
    }

    /**
     * 批量解析记录人展示名。
     *
     * @param headers 主表列表
     * @return recorderId → 姓名
     */
    private Map<Long, String> buildRecorderNameMap(List<VitalSignHeaderQueryModel> headers) {
        Map<Long, String> result = new HashMap<>();
        if (headers == null) {
            return result;
        }
        Set<Long> recorderIds = new HashSet<>();
        // 收集本批主表涉及的 recorderId，避免 N 次重复查 Practitioner
        for (VitalSignHeaderQueryModel header : headers) {
            if (header != null && header.getRecorderId() != null) {
                recorderIds.add(header.getRecorderId());
            }
        }
        for (Long recorderId : recorderIds) {
            Practitioner practitioner = practitionerQueryService.findById(recorderId);
            if (practitioner != null && trimToNull(practitioner.getName()) != null) {
                result.put(recorderId, practitioner.getName());
            }
        }
        return result;
    }

    /**
     * 过滤护理记录页常用体征项术语选项。
     *
     * @param allOptions 全量 vital_signs_code 选项
     * @return 护理记录相关子集
     */
    private List<OptionDto> filterNursingRecordVitalSignOptions(List<OptionDto> allOptions) {
        List<OptionDto> result = new ArrayList<>();
        if (allOptions == null) {
            return result;
        }
        Set<String> nursingCodes = VitalSignConstants.nursingRecordDetailCodes();
        // 仅保留护理记录页相关 vital_signs_code 子集
        for (OptionDto option : allOptions) {
            if (option == null || option.getValue() == null) {
                continue;
            }
            String code = String.valueOf(option.getValue());
            if (nursingCodes.contains(code)) {
                result.add(option);
            }
        }
        return result;
    }

    private List<SaveNursingRecordItemRequest> rejectSkeletonAndEmptyNursingRecordRows(
        List<SaveNursingRecordItemRequest> records
    ) {
        List<SaveNursingRecordItemRequest> result = new ArrayList<>();
        if (records == null) {
            return result;
        }
        for (SaveNursingRecordItemRequest record : records) {
            if (record == null) {
                continue;
            }
            if (InpatientNurseStationConstants.NURSING_RECORD_ROW_TYPE_SKELETON.equals(
                trimToNull(record.getRowType()))) {
                continue;
            }
            if (!hasAnyNursingRecordBusinessValue(record)) {
                continue;
            }
            result.add(record);
        }
        return result;
    }

    private boolean hasAnyNursingRecordBusinessValue(SaveNursingRecordItemRequest record) {
        if (record == null) {
            return false;
        }
        if (trimToNull(record.getTemperature()) != null) {
            return true;
        }
        if (trimToNull(record.getConditionMeasure()) != null) {
            return true;
        }
        if (trimToNull(record.getConsciousness()) != null
            || trimToNull(record.getPulse()) != null
            || trimToNull(record.getRespiratory()) != null
            || trimToNull(record.getHeartRate()) != null
            || trimToNull(record.getBloodPressure()) != null
            || trimToNull(record.getPupilLeft()) != null
            || trimToNull(record.getPupilRight()) != null
            || trimToNull(record.getOxygenSaturation()) != null
            || trimToNull(record.getOxygenRate()) != null
            || trimToNull(record.getOxygenMode()) != null
            || trimToNull(record.getOxygenFlow()) != null
            || trimToNull(record.getIntakeName()) != null
            || trimToNull(record.getIntakeAmount()) != null
            || trimToNull(record.getIntakeRoute()) != null
            || trimToNull(record.getOutputName()) != null
            || trimToNull(record.getOutputAmount()) != null
            || trimToNull(record.getOutputColorCharacter()) != null
            || trimToNull(record.getSkinStatus()) != null
            || trimToNull(record.getTubeCare()) != null) {
            return true;
        }
        return false;
    }
}
