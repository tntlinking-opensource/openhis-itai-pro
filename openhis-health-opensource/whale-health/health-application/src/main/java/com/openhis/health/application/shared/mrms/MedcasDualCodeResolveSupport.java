/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.mrms;

import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionYbMappingQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.clinical.proceduredefinition.service.ProcedureDefinitionQueryService;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案诊断/手术国临码与医保码解析（4401/4101A 上传与结算清单预览共用）。
 *
 * <p>西医上传出站<strong>必须</strong>有目录 {@code yb_no}；无对照不得用国临顶替。
 * <strong>中医</strong>：落库即中医目录码；不对国临→医保；预览国临列空、医保列回显落库码/名；
 * 上传出站用该目录码（{@link #requireDiagnosisOutbound}）。
 * 预览西医同屏国临+医保；未对码标红并引导目录对码。</p>
 */
@Component
@RequiredArgsConstructor
public class MedcasDualCodeResolveSupport {

    /** 诊断目录国临→医保。 */
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    /** 手术目录国临→医保。 */
    private final ProcedureDefinitionQueryService procedureDefinitionQueryService;

    /**
     * 批量解析诊断国临码对应的出站码。
     *
     * @param clinicCodes 国临码集合；可空
     * @return 国临码 → 解析结果；空入参返回空 Map
     */
    public Map<String, MedcasDualCodeResolveResult> resolveDiagnosisBatch(Collection<String> clinicCodes) {
        return this.resolveBatch(clinicCodes, true);
    }

    /**
     * 批量解析手术国临码对应的出站码。
     *
     * @param clinicCodes 国临码集合；可空
     * @return 国临码 → 解析结果；空入参返回空 Map
     */
    public Map<String, MedcasDualCodeResolveResult> resolveProcedureBatch(Collection<String> clinicCodes) {
        return this.resolveBatch(clinicCodes, false);
    }

    /**
     * 单条诊断解析（无码时返回 null）。
     *
     * @param clinicCode 国临码
     * @return 解析结果；clinicCode 空则 null
     */
    public MedcasDualCodeResolveResult resolveDiagnosis(String clinicCode) {
        String code = trimToNull(clinicCode);
        if (code == null) {
            return null;
        }
        return this.resolveDiagnosisBatch(List.of(code)).get(code);
    }

    /**
     * 单条手术解析（无码时返回 null）。
     *
     * @param clinicCode 国临码
     * @return 解析结果；clinicCode 空则 null
     */
    public MedcasDualCodeResolveResult resolveProcedure(String clinicCode) {
        String code = trimToNull(clinicCode);
        if (code == null) {
            return null;
        }
        return this.resolveProcedureBatch(List.of(code)).get(code);
    }

    /**
     * 按诊断或手术目录批量解析。
     *
     * @param clinicCodes 国临码集合
     * @param diagnosis   true=诊断目录，false=手术目录
     * @return 国临码 → 解析结果
     */
    private Map<String, MedcasDualCodeResolveResult> resolveBatch(
            Collection<String> clinicCodes, boolean diagnosis) {
        Map<String, MedcasDualCodeResolveResult> result = new LinkedHashMap<>();
        // Gate：空集合无需查目录
        if (clinicCodes == null || clinicCodes.isEmpty()) {
            return result;
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String raw : clinicCodes) {
            String code = trimToNull(raw);
            // 去重并跳过空码
            if (code != null) {
                distinct.add(code);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        // Pre：诊断走目录码+名+国临定义主键；手术暂仅码
        Map<String, String> ybByClinic = new LinkedHashMap<>();
        Map<String, String> ybNameByClinic = new LinkedHashMap<>();
        Map<String, Long> clinicalIdByClinic = new LinkedHashMap<>();
        if (diagnosis) {
            Map<String, ConditionYbMappingQueryModel> mapping =
                    conditionDefinitionQueryService.mapYbMappingByClinicCodes(distinct);
            for (Map.Entry<String, ConditionYbMappingQueryModel> entry : mapping.entrySet()) {
                ConditionYbMappingQueryModel m = entry.getValue();
                if (m == null) {
                    continue;
                }
                if (m.getClinicalDefinitionId() != null) {
                    clinicalIdByClinic.put(entry.getKey(), m.getClinicalDefinitionId());
                }
                String ybNo = trimToNull(m.getYbNo());
                if (ybNo != null) {
                    ybByClinic.put(entry.getKey(), ybNo);
                }
                String ybName = trimToNull(m.getYbName());
                if (ybName != null) {
                    ybNameByClinic.put(entry.getKey(), ybName);
                }
            }
        } else {
            ybByClinic = procedureDefinitionQueryService.mapYbNoByClinicCodes(distinct);
        }
        // Asm：每条国临生成结果；未对码时 outbound 为空（上传须报错，禁止国临顶替）
        for (String clinic : distinct) {
            MedcasDualCodeResolveResult row = new MedcasDualCodeResolveResult();
            row.setClinicCode(clinic);
            row.setClinicalDefinitionId(clinicalIdByClinic.get(clinic));
            String ybNo = null;
            if (ybByClinic != null) {
                ybNo = trimToNull(ybByClinic.get(clinic));
            }
            row.setYbNo(ybNo);
            row.setYbName(trimToNull(ybNameByClinic.get(clinic)));
            // 业务分支：已对码 → 出站医保码；未对码 → outbound 空，由上传侧抛错
            if (ybNo != null) {
                row.setMapped(true);
                row.setOutboundCode(ybNo);
            } else {
                row.setMapped(false);
                row.setOutboundCode(null);
            }
            result.put(clinic, row);
        }
        return result;
    }

    /**
     * 诊断上传出站：西医须已对医保码；中医不对国临→医保，直接用目录编码。
     *
     * @param resolved    批量解析结果（西医用）
     * @param kindByCode  编码 → {@code WESTERN}/{@code TCM}（与医生站同源）
     * @param clinicCode  首页落库诊断编码
     * @param displayHint 报错用展示（码+名称）
     * @return 非空出站码
     */
    public String requireDiagnosisOutbound(
            Map<String, MedcasDualCodeResolveResult> resolved,
            Map<String, String> kindByCode,
            String clinicCode,
            String displayHint) {
        String code = trimToNull(clinicCode);
        if (code == null) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "诊断编码不能为空");
        }
        String kind = null;
        if (kindByCode != null) {
            kind = kindByCode.get(code);
        }
        // 业务分支：中医不对国临→医保，出站用目录自身编码
        if (DoctorStationDiagnosisConstants.DIAGNOSIS_KIND_TAG_TCM.equals(kind)) {
            return code;
        }
        return this.requireYbOutbound(resolved, code, displayHint);
    }

    /**
     * 上传出站：必须已对医保码，否则抛业务异常（禁止国临顶替）。
     *
     * <p>手术与西医诊断共用；中医诊断请走 {@link #requireDiagnosisOutbound}。</p>
     *
     * @param resolved    批量解析结果
     * @param clinicCode  国临码
     * @param displayHint 报错用展示（码+名称）
     * @return 非空医保码
     */
    public String requireYbOutbound(
            Map<String, MedcasDualCodeResolveResult> resolved,
            String clinicCode,
            String displayHint) {
        String code = trimToNull(clinicCode);
        if (code == null) {
            throw new BusinessException(
                    MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION, "诊断/手术编码不能为空");
        }
        MedcasDualCodeResolveResult hit = null;
        if (resolved != null) {
            hit = resolved.get(code);
        }
        // 业务分支：已对码则返回医保出站码
        if (hit != null && hit.isMapped() && trimToNull(hit.getOutboundCode()) != null) {
            return hit.getOutboundCode().trim();
        }
        String hint = trimToNull(displayHint);
        if (hint == null) {
            hint = code;
        }
        throw new BusinessException(
                MrmsErrorCodes.UPLOAD_ASSEMBLE_VALIDATION,
                "未对医保码，请在目录维护国临码与医保编码(yb_no)对照：" + hint);
    }

    /**
     * 从解析 Map 取已对码的医保出站码；未对码返回 null（不回退国临）。
     *
     * @param resolved   批量解析结果
     * @param clinicCode 国临码
     * @return 医保码或 null
     */
    public String outboundYbOrNull(Map<String, MedcasDualCodeResolveResult> resolved, String clinicCode) {
        String code = trimToNull(clinicCode);
        if (code == null || resolved == null) {
            return null;
        }
        MedcasDualCodeResolveResult hit = resolved.get(code);
        if (hit == null || !hit.isMapped()) {
            return null;
        }
        return trimToNull(hit.getOutboundCode());
    }

    /**
     * 收集非空国临码列表（去重保序）。
     *
     * @param codes 原始码序列
     * @return 去重后的国临码列表
     */
    public List<String> distinctClinicCodes(Collection<String> codes) {
        List<String> list = new ArrayList<>();
        if (codes == null || codes.isEmpty()) {
            return list;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : codes) {
            String code = trimToNull(raw);
            if (code != null && seen.add(code)) {
                list.add(code);
            }
        }
        return list;
    }
}
