import type {
  OpspFilingDiagnosisRowDto,
  PatientOpspFilingDiagnosisResponse,
} from '#/api/doctor-station/diagnosis.types';

import {
  httpEnsureOpspFilingDiagnosis,
  httpGetPatientOpspFilingDiagnosis,
} from '#/api/doctor-station/diagnosis.http';
import {
  GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
  isChronicMedicalTypeCode,
  resolveOpspMedicalTypeCode,
} from '#/utils/chronic-medical-type';

/** 历史西医诊断目录快照（与 history-advice-apply 对齐） */
export interface HistoryWesternDiagnosisDefForOpsp {
  conditionCode?: string;
  conditionDefinitionId: string;
  conditionDefinitionName?: string;
  medicalTypeCode?: string;
  ybNo?: string;
}

/** 备案门控后的医疗类别 */
export interface HistoryDefMedicalTypeResolve {
  medicalTypeCode?: string;
  /** 是否按慢病开立（含升级） */
  asChronic: boolean;
  /** unmatched 补录后可能换用新的 chronic_spec 目录 id */
  remappedConditionDefinitionId?: string;
  skipHint?: string;
}

/** 拉取当前就诊慢特病备案；失败按「不可用」降级，不阻断普通诊断带入 */
export async function loadPatientOpspFilingForHistoryApply(
  encounterId: string,
  patientId: string,
): Promise<PatientOpspFilingDiagnosisResponse> {
  try {
    return await httpGetPatientOpspFilingDiagnosis({ encounterId, patientId });
  } catch {
    return { opspFilingImportEnabled: false, filingList: [] };
  }
}

/**
 * 历史诊断 → 备案行：优先目录 id，其次备案码对齐 ybNo/conditionCode（与侧栏导入同源）。
 */
export function findOpspFilingRowForHistoryDef(
  def: HistoryWesternDiagnosisDefForOpsp,
  filingList: readonly OpspFilingDiagnosisRowDto[],
): OpspFilingDiagnosisRowDto | null {
  const defId = def.conditionDefinitionId;
  if (defId) {
    // 已匹配目录：按 conditionDefinitionId 命中
    const byDef = filingList.find(
      (row) => String(row.conditionDefinitionId ?? '').trim() === defId,
    );
    if (byDef) {
      return byDef;
    }
  }
  const codeKeys = [def.ybNo, def.conditionCode].map((v) => String(v ?? '').trim()).filter(Boolean);
  if (codeKeys.length === 0) {
    return null;
  }
  // 未匹配目录时仅有备案码：用病种码对齐
  return (
    filingList.find((row) => {
      const dise = String(row.opspDiseCode ?? '').trim();
      return Boolean(dise) && codeKeys.includes(dise);
    }) ?? null
  );
}

/**
 * 与诊断 Tab「慢病备案」导入同规则：仅省略医生点击。
 * - 历史非慢病：原样带回
 * - 历史慢病：须 CHS 启用 + 备案未过期 + matched/alreadyOnEncounter；unmatched 则自动补录目录
 */
export async function resolveHistoryDefMedicalTypeViaOpspFiling(
  def: HistoryWesternDiagnosisDefForOpsp,
  filing: PatientOpspFilingDiagnosisResponse,
  encounterId: string,
  patientId: string,
): Promise<HistoryDefMedicalTypeResolve> {
  if (!isChronicMedicalTypeCode(def.medicalTypeCode)) {
    return { medicalTypeCode: def.medicalTypeCode, asChronic: false };
  }

  const dxName = def.conditionDefinitionName || def.conditionCode || '该诊断';
  if (!filing.opspFilingImportEnabled) {
    return {
      medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
      asChronic: false,
      skipHint: `「${dxName}」医保慢病备案不可用，已按普通诊断带入`,
    };
  }

  const filingList = filing.filingList ?? [];
  const row = findOpspFilingRowForHistoryDef(def, filingList);
  if (!row) {
    return {
      medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
      asChronic: false,
      skipHint: `「${dxName}」当前无对应慢病备案，已按普通诊断带入`,
    };
  }

  const status = String(row.matchStatus ?? '').trim();
  if (status === 'expired') {
    return {
      medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
      asChronic: false,
      skipHint: row.matchHint || `「${dxName}」慢病备案已过期，已按普通诊断带入`,
    };
  }
  if (status === 'ambiguous') {
    return {
      medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
      asChronic: false,
      skipHint: row.matchHint || `「${dxName}」慢病备案匹配歧义，已按普通诊断带入`,
    };
  }
  if (status === 'matched' || status === 'alreadyOnEncounter') {
    return {
      medicalTypeCode: resolveOpspMedicalTypeCode(row.medicalTypeCode ?? def.medicalTypeCode),
      asChronic: true,
    };
  }
  if (status === 'unmatched') {
    const opspDiseCode = String(row.opspDiseCode ?? '').trim();
    if (!opspDiseCode) {
      return {
        medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
        asChronic: false,
        skipHint: row.matchHint || `「${dxName}」慢病备案不可导入，已按普通诊断带入`,
      };
    }
    try {
      // 等同医生点击未匹配备案：幂等补录 chronic_spec
      const ensured = await httpEnsureOpspFilingDiagnosis({
        encounterId,
        patientId,
        opspDiseCode,
        opspDiseName: row.opspDiseName ?? undefined,
      });
      const ensuredDefId = String(ensured.conditionDefinitionId ?? '').trim();
      if (!ensuredDefId) {
        return {
          medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
          asChronic: false,
          skipHint: `「${dxName}」慢病目录补录失败，已按普通诊断带入`,
        };
      }
      return {
        medicalTypeCode: resolveOpspMedicalTypeCode(
          ensured.medicalTypeCode ?? row.medicalTypeCode ?? def.medicalTypeCode,
        ),
        asChronic: true,
        remappedConditionDefinitionId:
          ensuredDefId !== def.conditionDefinitionId ? ensuredDefId : undefined,
      };
    } catch (e: unknown) {
      let msg = '';
      if (e && typeof e === 'object' && 'message' in e) {
        msg = String((e as { message?: unknown }).message ?? '').trim();
      }
      return {
        medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
        asChronic: false,
        skipHint: msg || row.matchHint || `「${dxName}」慢病备案不可导入，已按普通诊断带入`,
      };
    }
  }

  return {
    medicalTypeCode: GENERAL_OUTPATIENT_MEDICAL_TYPE_CODE,
    asChronic: false,
    skipHint: row.matchHint || `「${dxName}」慢病备案不可导入，已按普通诊断带入`,
  };
}
