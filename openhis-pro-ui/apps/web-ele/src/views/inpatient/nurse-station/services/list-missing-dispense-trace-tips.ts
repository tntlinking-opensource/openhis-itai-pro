import type { ListMissingDispenseTraceTipsResponse } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import { httpListMissingDispenseTraceTips } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 清床前拉取缺追溯码 tip 列表（失败时返回空，不挡清床确认） */
export async function loadMissingDispenseTraceTips(
  encounterId: string,
): Promise<ListMissingDispenseTraceTipsResponse> {
  const id = preserveApiIdForRequest(encounterId) ?? encounterId;
  return httpListMissingDispenseTraceTips({ encounterId: id });
}

/** 将 tip 列表拼成确认框正文；无 tip 时返回基础清床文案 */
export function buildClearBedConfirmMessage(
  tipsResponse: ListMissingDispenseTraceTipsResponse | null | undefined,
): string {
  const base = '确认清床离院？释床后患者将从护士站列表移除。';
  const tips = tipsResponse?.tips ?? [];
  // 仅保留有 tipText 的行，避免空串占位
  const lines: string[] = [];
  for (const tip of tips) {
    const text = String(tip?.tipText ?? '').trim();
    if (text) {
      lines.push(text);
    }
  }
  if (lines.length === 0 && !tipsResponse?.hasMissingTrace) {
    return base;
  }
  if (lines.length === 0) {
    return `存在未分配追溯码的已发药记录，确认仍要清床？\n\n${base}`;
  }
  return `以下已发药记录未分配追溯码，确认仍要清床？\n\n${lines.join('\n')}\n\n${base}`;
}
