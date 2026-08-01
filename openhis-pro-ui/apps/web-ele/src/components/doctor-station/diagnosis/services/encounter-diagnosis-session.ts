import { parseEncounterDiagnosisIds } from './encounter-diagnosis-select';

/** 当前绑定的就诊 id（会话内；刷新即失） */
let boundEncounterId = '';
/** 上次选中的就诊诊断 ids */
let lastSelectedIds: string[] = [];

/** 记住本就诊开立会话内最近一次诊断选择（医嘱 / 申请单共用）。 */
export function rememberEncounterDiagnosisSession(
  encounterId: string | undefined,
  ids: string[] | undefined,
): void {
  const eid = String(encounterId ?? '').trim();
  const parsed = parseEncounterDiagnosisIds(ids);
  if (!eid || parsed.length === 0) return;
  boundEncounterId = eid;
  lastSelectedIds = parsed;
}

/** 读取本就诊会话内上次诊断选择；就诊不一致则空。 */
export function readEncounterDiagnosisSession(encounterId: string | undefined): string[] {
  const eid = String(encounterId ?? '').trim();
  if (!eid || eid !== boundEncounterId) return [];
  return [...lastSelectedIds];
}

/** 换患者/就诊时清空会话记忆。 */
export function clearEncounterDiagnosisSession(): void {
  boundEncounterId = '';
  lastSelectedIds = [];
}
