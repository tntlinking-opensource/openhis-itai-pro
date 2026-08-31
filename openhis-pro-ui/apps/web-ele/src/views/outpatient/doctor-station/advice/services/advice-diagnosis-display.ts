import type { DiagnosisItem } from '#/components/doctor-station/diagnosis';
import { parseEncounterDiagnosisIds } from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';
import { resolveChronicMedicalTypeTag } from '#/utils/chronic-medical-type';

/** 医嘱列表/历史「诊断」列分段展示（名称 + 慢/特角标） */
export interface AdviceDiagnosisDisplaySegment {
  name: string;
  /** 慢 / 特 / 慢特；非慢特病为空 */
  tag: string;
}

/** 就诊诊断候选项 → id 查找表（encounterDiagnosisId 优先） */
export function buildDiagnosisLookupById(
  items: readonly DiagnosisItem[],
): Map<string, DiagnosisItem> {
  const map = new Map<string, DiagnosisItem>();
  for (const item of items) {
    const id = String(item.encounterDiagnosisId ?? item.id ?? '').trim();
    if (id && !map.has(id)) {
      map.set(id, item);
    }
  }
  return map;
}

/**
 * 按绑定就诊诊断 ids 拆分诊断列；无有效绑定时回落 diagnosisSummary 纯文本。
 */
export function buildAdviceDiagnosisDisplaySegments(params: {
  diagnosisSummary?: null | string;
  encounterDiagnosisIds?: null | string | string[];
  diagnosisById: Map<string, DiagnosisItem>;
}): AdviceDiagnosisDisplaySegment[] {
  const ids = parseEncounterDiagnosisIds(params.encounterDiagnosisIds);
  if (ids.length > 0) {
    const segments: AdviceDiagnosisDisplaySegment[] = [];
    // 按医嘱绑定序展示，缺名则跳过该 id
    for (const id of ids) {
      const hit = params.diagnosisById.get(id);
      const name = String(hit?.name ?? '').trim();
      if (!name) {
        continue;
      }
      segments.push({
        name,
        tag: resolveChronicMedicalTypeTag(hit?.medicalTypeCode),
      });
    }
    if (segments.length > 0) {
      return segments;
    }
  }

  const summary = String(params.diagnosisSummary ?? '').trim() || '—';
  return [{ name: summary, tag: '' }];
}

/** 主诊（或首条）慢病角标，供历史左栏就诊卡 */
export function resolvePrimaryDiagnosisChronicTag(items: readonly DiagnosisItem[]): string {
  if (items.length === 0) {
    return '';
  }
  const main = items.find((item) => Number(item.mainDiagnosisFlag) === 1) ?? items[0];
  return resolveChronicMedicalTypeTag(main?.medicalTypeCode);
}
