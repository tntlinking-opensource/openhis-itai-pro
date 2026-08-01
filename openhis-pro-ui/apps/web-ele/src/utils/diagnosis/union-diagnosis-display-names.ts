/**
 * 处方方头诊断展示：将多段「顿号分隔」诊断文案合并去重保序。
 * 用于发药单/处方查询/笺面等同方多药行场景。
 */

/** 合并多段诊断展示文案；全空时返回空串。 */
export function unionDiagnosisDisplayNames(...texts: Array<null | string | undefined>): string {
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const raw of texts) {
    const text = String(raw ?? '').trim();
    if (!text || text === '—') {
      continue;
    }
    for (const part of text.split('、')) {
      const name = part.trim();
      if (!name || name === '—' || seen.has(name)) {
        continue;
      }
      seen.add(name);
      ordered.push(name);
    }
  }
  return ordered.join('、');
}

/** 同 {@link unionDiagnosisDisplayNames}，无有效名时回落「—」。 */
export function unionDiagnosisDisplayNamesOrDash(
  ...texts: Array<null | string | undefined>
): string {
  const merged = unionDiagnosisDisplayNames(...texts);
  if (!merged) {
    return '—';
  }
  return merged;
}
