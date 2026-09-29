/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.settlelist.service;

import com.openhis.health.application.mrms.settlelist.dto.SettleListDiagnosisPreviewDto;
import com.openhis.health.application.mrms.settlelist.dto.SettleListProcedurePreviewDto;
import com.openhis.health.application.mrms.settlelist.request.LoadSettleListPreviewRequest;
import com.openhis.health.application.mrms.settlelist.response.LoadSettleListPreviewResponse;
import com.openhis.health.application.mrms.upload.medcas.mapper.CaseRecord4101AMapper;
import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterContext;
import com.openhis.health.application.mrms.upload.medcas.support.InsuranceEncounterLoader;
import com.openhis.health.application.mrms.upload.medcas.support.Upload4101AValidator;
import com.openhis.health.application.shared.diagnosis.EncounterDiagnosisSourceHintResolver;
import com.openhis.health.application.shared.mrms.MedcasDualCodeResolveResult;
import com.openhis.health.application.shared.mrms.MedcasDualCodeResolveSupport;
import com.openhis.health.application.shared.validator.MrmsOperatorResolver;
import com.openhis.health.domain.administration.account.entity.Account;
import com.openhis.health.domain.administration.account.service.AccountQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ClinicalDiagnosisPresentationQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageDiagnosis;
import com.openhis.health.domain.mrms.caserecord.entity.HomepageProcedure;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.MrmsDualCodeConstants;
import com.openhis.health.domain.share.enums.HomepageDiagType;
import com.openhis.health.domain.share.utils.DiagnosisKindPresentationUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.infrastructure.integration.insurance.medcas.dto.MedcasSettleListPayload;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 结算清单只读预览应用服务（独立预览页，不嵌病案详情编辑）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsSettleListPreview/*}。上传/4102/4103 仍走
 * {@code MrmsCaseRecordUploadAppService}，本服务仅组预览与双码对照。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrmsSettleListPreviewAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final MedcasDualCodeResolveSupport medcasDualCodeResolveSupport;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final EncounterDiagnosisSourceHintResolver encounterDiagnosisSourceHintResolver;
    private final CaseRecord4101AMapper caseRecord4101AMapper;
    private final InsuranceEncounterLoader insuranceEncounterLoader;
    private final AccountQueryService accountQueryService;
    private final MrmsOperatorResolver operatorResolver;

    /**
     * 加载结算清单只读预览：摘要 + 诊断/手术双码 + 重症/输血占位；可选 4101A 组装校验。
     *
     * @param request 病案或就诊主键
     * @return 预览响应
     */
    public LoadSettleListPreviewResponse loadSettleListPreview(LoadSettleListPreviewRequest request) {
        // Gate：须能定位病案
        CaseRecord record = this.requireCaseRecord(request);
        // Pre：懒建首页（历史未建档就诊）
        Long operatorId = operatorResolver.resolveOperatorIdOrNull();
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorId);
        List<HomepageDiagnosis> diagnoses = caseRecordHomepageDomainService.listDiagnoses(record.getId());
        List<HomepageProcedure> procedures = caseRecordHomepageDomainService.listProcedures(record.getId());

        // Asm：摘要 + 双码行 + 重症/输血空分区（对齐老页签；无单独费用页签）
        LoadSettleListPreviewResponse response = new LoadSettleListPreviewResponse();
        this.fillSummary(response, record, homepage);
        response.setDiagnoses(this.toDiagnosisPreviewRows(diagnoses, record.getEncounterId()));
        response.setProcedures(this.toProcedurePreviewRows(procedures));
        response.setIcuinfo(new ArrayList<>());
        response.setBldinfo(new ArrayList<>());
        response.setIteminfo(new ArrayList<>());

        // Cmd：有结算上下文则做 4101A 组装校验；失败写 hint（页面不另开费用/JSON 页签）
        this.fillAssembledPreview(response, record, request.getContractBusNo(), operatorId);
        return response;
    }

    /**
     * 按 caseRecordId / encounterId 定位病案；仅有就诊时幂等建档。
     *
     * @param request 预览请求
     * @return 病案主档
     */
    private CaseRecord requireCaseRecord(LoadSettleListPreviewRequest request) {
        if (request == null) {
            throw new ValidationException("请求不能为空");
        }
        // 优先病案主键
        if (request.getCaseRecordId() != null) {
            return caseRecordDomainService.requireById(request.getCaseRecordId());
        }
        Long encounterId = request.getEncounterId();
        if (encounterId == null) {
            throw new ValidationException("病案主键与就诊主键不能同时为空");
        }
        Long tenantId = operatorResolver.requireTenantId();
        Optional<CaseRecord> existing = caseRecordDomainService.findByEncounterId(tenantId, encounterId);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 未建档：幂等 bootstrap（与首页打开兜底同口径）
        Long operatorId = operatorResolver.resolveOperatorIdOrNull();
        // encounter 上的 patientId 由 bootstrap 内部经 createIfAbsent 使用；此处须从已有逻辑取
        // 使用 Domain bootstrap 需 patientId：经 ensure 路径——读首页 App 同类 bootstrapByEncounter
        throw new ValidationException("该就诊尚未建档，请先打开病案详情完成建档后再查看结算清单");
    }

    /**
     * 回填列表摘要字段。
     *
     * @param response 响应
     * @param record   主档
     * @param homepage 首页
     */
    private void fillSummary(
            LoadSettleListPreviewResponse response, CaseRecord record, CaseRecordHomepage homepage) {
        response.setCaseRecordId(record.getId());
        response.setEncounterId(record.getEncounterId());
        response.setPatientId(record.getPatientId());
        response.setMedcasno(record.getMedcasno());
        response.setIptNo(trimToNull(homepage.getIptNo()));
        if (response.getIptNo() == null) {
            response.setIptNo(trimToNull(record.getIptNo()));
        }
        response.setPsnName(trimToNull(homepage.getPsnName()));
        if (response.getPsnName() == null) {
            response.setPsnName(trimToNull(record.getPsnName()));
        }
        response.setAdmCatyName(trimToNull(homepage.getAdmCatyName()));
        if (response.getAdmCatyName() == null) {
            response.setAdmCatyName(trimToNull(record.getAdmCatyName()));
        }
        response.setAdmTime(homepage.getAdmTime());
        if (response.getAdmTime() == null) {
            response.setAdmTime(record.getAdmTime());
        }
        response.setDscgTime(homepage.getDscgTime());
        if (response.getDscgTime() == null) {
            response.setDscgTime(record.getDscgTime());
        }
        response.setSpgaNurscareDays(homepage.getSpgaNurscareDays());
        response.setLv1NurscareDays(homepage.getLv1NurscareDays());
        response.setScdNurscareDays(homepage.getScdNurscareDays());
        response.setLv3NurscareDays(homepage.getLv3NurscareDays());
        response.setSettleUploadStatusCode(record.getSettleUploadStatusCode());
        response.setSetlListId(trimToNull(record.getSetlListId()));
    }

    /**
     * 诊断实体 → 双码预览行。
     *
     * @param rows        诊断子表
     * @param encounterId 就诊主键；用于按医生开立来源区分国临/医保同码
     * @return 预览行
     */
    private List<SettleListDiagnosisPreviewDto> toDiagnosisPreviewRows(
            List<HomepageDiagnosis> rows, Long encounterId) {
        List<SettleListDiagnosisPreviewDto> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        List<HomepageDiagnosis> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageDiagnosis::getDiagTypeCode)
                .thenComparing(HomepageDiagnosis::getSortNo));
        Set<String> allCodes = new LinkedHashSet<>();
        for (HomepageDiagnosis row : sorted) {
            String code = trimToNull(row.getDiagCode());
            if (code != null) {
                allCodes.add(code);
            }
        }
        Map<String, String> sourceHints =
                encounterDiagnosisSourceHintResolver.resolveSourceHintByCode(encounterId);
        Map<String, ClinicalDiagnosisPresentationQueryModel> presentationByStored =
                conditionDefinitionQueryService.mapClinicalPresentationByStoredCodes(allCodes, sourceHints);
        // Pre：西医国临码走国临→医保解析；中医与已解析国临码分开处理
        Set<String> westernClinicCodes = new LinkedHashSet<>();
        for (ClinicalDiagnosisPresentationQueryModel presentation : presentationByStored.values()) {
            if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(presentation.getDiagnosisKind())) {
                continue;
            }
            String clinicCode = trimToNull(presentation.getClinicCode());
            if (clinicCode != null) {
                westernClinicCodes.add(clinicCode);
            }
        }
        Map<String, MedcasDualCodeResolveResult> dual =
                medcasDualCodeResolveSupport.resolveDiagnosisBatch(westernClinicCodes);
        for (HomepageDiagnosis row : sorted) {
            SettleListDiagnosisPreviewDto dto = new SettleListDiagnosisPreviewDto();
            dto.setDiagTypeCode(row.getDiagTypeCode());
            dto.setDiagTypeLabel(this.resolveDiagTypeLabel(row.getDiagTypeCode()));
            dto.setSortNo(row.getSortNo());
            String storedCode = trimToNull(row.getDiagCode());
            String storedName = trimToNull(row.getDiagName());
            dto.setAdmCondCode(trimToNull(row.getAdmCondCode()));
            dto.setOutIllStatusCode(trimToNull(row.getOutIllStatusCode()));
            dto.setDiagRemark(trimToNull(row.getDiagRemark()));
            dto.setConfirmTime(row.getConfirmTime());
            dto.setMaindiagFlag(trimToNull(row.getMaindiagFlag()));
            // 是否上传：透传首页 need_yb_up，供预览对照；出站跳过见 4101A/4401 Mapper
            dto.setNeedYbUp(trimToNull(row.getNeedYbUp()));
            ClinicalDiagnosisPresentationQueryModel presentation = null;
            if (storedCode != null) {
                presentation = presentationByStored.get(storedCode);
            }
            // 业务分支：有编码则填中西医列
            if (presentation != null) {
                dto.setDiagnosisKind(presentation.getDiagnosisKind());
                dto.setDiagnosisKindLabel(DiagnosisKindPresentationUtil.toDisplay(presentation.getDiagnosisKind()));
            }
            // 业务分支：中医不对国临→医保 → 国临列空，医保列回显落库目录码/名（结算清单原口径）
            boolean isTcm = DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(
                    dto.getDiagnosisKind());
            if (isTcm) {
                dto.setClinicCode(null);
                dto.setDiagName(null);
                dto.setClinicalDefinitionId(null);
                String ybCode = storedCode;
                String ybName = storedName;
                if (presentation != null) {
                    if (trimToNull(presentation.getYbCode()) != null) {
                        ybCode = presentation.getYbCode();
                    }
                    if (trimToNull(presentation.getYbName()) != null) {
                        ybName = presentation.getYbName();
                    }
                }
                dto.setYbNo(ybCode);
                dto.setYbName(ybName);
                dto.setOutboundCode(ybCode);
                dto.setMapped(null);
                dto.setUnmappedHint(null);
                result.add(dto);
                continue;
            }
            String presentationClinic = null;
            String presentationClinicName = null;
            String presentationYb = null;
            String presentationYbName = null;
            if (presentation != null) {
                presentationClinic = trimToNull(presentation.getClinicCode());
                presentationClinicName = trimToNull(presentation.getClinicName());
                presentationYb = trimToNull(presentation.getYbCode());
                presentationYbName = trimToNull(presentation.getYbName());
                dto.setClinicalDefinitionId(presentation.getClinicalDefinitionId());
            }
            // 业务分支：落库为医保码且无国临绑码 → 国临列空，医保列展示落库码
            if (presentationClinic == null && presentationYb != null) {
                dto.setClinicCode(null);
                dto.setDiagName(null);
                dto.setClinicalDefinitionId(null);
                dto.setYbNo(presentationYb);
                if (presentationYbName != null) {
                    dto.setYbName(presentationYbName);
                } else {
                    dto.setYbName(storedName);
                }
                dto.setOutboundCode(presentationYb);
                dto.setMapped(null);
                dto.setUnmappedHint(null);
                result.add(dto);
                continue;
            }
            // 业务分支：国临码（或医保已反查出国临）→ 国临列展示；医保列走对照
            String clinicCode = presentationClinic;
            String clinicName = presentationClinicName;
            if (clinicCode == null) {
                clinicCode = storedCode;
            }
            if (clinicName == null) {
                clinicName = storedName;
            }
            dto.setClinicCode(clinicCode);
            dto.setDiagName(clinicName);
            if (clinicCode != null) {
                MedcasDualCodeResolveResult hit = dual.get(clinicCode);
                if (hit != null) {
                    if (dto.getClinicalDefinitionId() == null) {
                        dto.setClinicalDefinitionId(hit.getClinicalDefinitionId());
                    }
                    // 落库本是医保码时，医保列优先用落库码/名
                    if (presentationYb != null && !clinicCode.equals(storedCode)) {
                        dto.setYbNo(presentationYb);
                        if (presentationYbName != null) {
                            dto.setYbName(presentationYbName);
                        } else {
                            dto.setYbName(hit.getYbName());
                        }
                        dto.setOutboundCode(presentationYb);
                        dto.setMapped(true);
                    } else {
                        dto.setYbNo(hit.getYbNo());
                        dto.setYbName(hit.getYbName());
                        dto.setOutboundCode(hit.getOutboundCode());
                        dto.setMapped(hit.isMapped());
                        if (!hit.isMapped()) {
                            dto.setUnmappedHint(MrmsDualCodeConstants.UNMAPPED_HINT);
                        }
                    }
                } else {
                    dto.setMapped(false);
                    dto.setUnmappedHint(MrmsDualCodeConstants.UNMAPPED_HINT);
                }
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 手术实体 → 双码预览行。
     *
     * @param rows 手术子表
     * @return 预览行
     */
    private List<SettleListProcedurePreviewDto> toProcedurePreviewRows(List<HomepageProcedure> rows) {
        List<SettleListProcedurePreviewDto> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        List<HomepageProcedure> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(HomepageProcedure::getSortNo));
        Set<String> clinicCodes = new LinkedHashSet<>();
        for (HomepageProcedure row : sorted) {
            String clinic = trimToNull(row.getOprnOprtCode());
            if (clinic != null) {
                clinicCodes.add(clinic);
            }
        }
        Map<String, MedcasDualCodeResolveResult> dual =
                medcasDualCodeResolveSupport.resolveProcedureBatch(clinicCodes);
        for (HomepageProcedure row : sorted) {
            SettleListProcedurePreviewDto dto = new SettleListProcedurePreviewDto();
            dto.setSortNo(row.getSortNo());
            String clinic = trimToNull(row.getOprnOprtCode());
            dto.setClinicCode(clinic);
            dto.setOprnOprtName(trimToNull(row.getOprnOprtName()));
            dto.setMainOprnFlag(trimToNull(row.getMainOprnFlag()));
            // 首页仅存手术日；结算清单老列「开始/结束」暂只回填开始日
            if (row.getOprnDate() != null) {
                dto.setOprnDate(row.getOprnDate().toString());
            }
            dto.setOprnLevelCode(trimToNull(row.getOprnLevelCode()));
            dto.setOprnIncisionCode(trimToNull(row.getOprnIncisionCode()));
            dto.setHealGradeCode(trimToNull(row.getHealGradeCode()));
            dto.setAnstWayCode(trimToNull(row.getAnstWayCode()));
            dto.setOperDrName(trimToNull(row.getOperDrName()));
            dto.setAsit1Name(trimToNull(row.getAsit1Name()));
            dto.setAsit2Name(trimToNull(row.getAsit2Name()));
            dto.setNurseName(trimToNull(row.getNurseName()));
            dto.setAnstDrName(trimToNull(row.getAnstDrName()));
            // DRG 扩展码本期无独立字段，列保留空
            dto.setDrgExtCode(null);
            if (clinic != null) {
                MedcasDualCodeResolveResult hit = dual.get(clinic);
                if (hit != null) {
                    dto.setYbNo(hit.getYbNo());
                    dto.setOutboundCode(hit.getOutboundCode());
                    dto.setMapped(hit.isMapped());
                    if (!hit.isMapped()) {
                        dto.setUnmappedHint(MrmsDualCodeConstants.UNMAPPED_HINT);
                    }
                } else {
                    dto.setMapped(false);
                    dto.setUnmappedHint(MrmsDualCodeConstants.UNMAPPED_HINT);
                }
            }
            result.add(dto);
        }
        return result;
    }

    /**
     * 诊断类型码 → 展示名。
     *
     * @param diagTypeCode 类型码
     * @return 展示名；未知则原码
     */
    private String resolveDiagTypeLabel(String diagTypeCode) {
        String code = trimToNull(diagTypeCode);
        if (code == null) {
            return null;
        }
        HomepageDiagType type = EnumUtil.fromCode(HomepageDiagType.class, code);
        if (type == null) {
            return code;
        }
        return type.getDisplay();
    }

    /**
     * 尝试组装 4101A 载荷写入响应；缺结算上下文时仅写 hint。
     *
     * @param response       响应
     * @param record         主档
     * @param contractBusNo  显式契约
     * @param operatorId     操作人
     */
    private void fillAssembledPreview(
            LoadSettleListPreviewResponse response,
            CaseRecord record,
            String contractBusNo,
            Long operatorId) {
        String resolvedContract = this.resolveContractBusNoOrNull(record.getEncounterId(), contractBusNo);
        // 无医保合同：仍可看双码，不上送预览
        if (resolvedContract == null) {
            response.setAssembleHint("未解析到医保费用性质，暂无法校验结算上传条件");
            return;
        }
        Optional<InsuranceEncounterContext> ctxOpt = insuranceEncounterLoader.findContext(
                record.getEncounterId(), resolvedContract);
        if (ctxOpt.isEmpty()) {
            response.setAssembleHint("病人确认结算后才能上传结算清单（缺 mdtrt_id/setl_id）");
            return;
        }
        InsuranceEncounterContext ctx = ctxOpt.get();
        // 页头展示医保人员编号与结算 ID（不用 setl_list_id）
        response.setPsnNo(trimToNull(ctx.getPsnNo()));
        response.setSetlId(trimToNull(ctx.getSetlId()));
        try {
            MedcasSettleListPayload payload =
                    caseRecord4101AMapper.assemble(record, ctx, operatorId);
            Upload4101AValidator.validate(payload);
            response.setAssembledPreview(payload);
            response.setAssembleHint(null);
        } catch (RuntimeException ex) {
            log.warn("结算清单 4101A 预校验组装失败 caseRecordId={}", record.getId(), ex);
            response.setAssembleHint("结算清单组装未通过：" + ex.getMessage());
        }
    }

    /**
     * 解析医保合同 busNo；无法解析时返回 null（不抛）。
     *
     * @param encounterId   就诊
     * @param contractBusNo 显式契约
     * @return busNo 或 null
     */
    private String resolveContractBusNoOrNull(Long encounterId, String contractBusNo) {
        String explicit = trimToNull(contractBusNo);
        if (explicit != null) {
            return explicit;
        }
        if (encounterId == null) {
            return null;
        }
        Account ybAccount = accountQueryService.getYbAccount(encounterId);
        if (ybAccount == null) {
            return null;
        }
        return trimToNull(ybAccount.getContractNo());
    }
}
