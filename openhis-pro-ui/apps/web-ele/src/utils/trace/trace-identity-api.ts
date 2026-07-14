import type { BatchResolveTraceIdentityResponse, TraceScanScenePreset } from './trace-types';

import { httpBatchResolveTraceIdentityForDispense } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.http';
import type { BatchResolveTraceIdentityRequest } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';
import { batchResolveTraceIdentityForInboundApi } from '#/api/inventory/warehouse/inbound-receipt';

import { chunkTraceCodes } from './trace-code-parse';

/** 按场景预设选择反查接口并合并多批结果 */
export async function batchResolveTraceIdentityByPreset(
  traceCodes: string[],
  preset: TraceScanScenePreset,
): Promise<BatchResolveTraceIdentityResponse> {
  const chunks = chunkTraceCodes(traceCodes);
  const merged: BatchResolveTraceIdentityResponse = { items: [], failItems: [] };

  for (const batch of chunks) {
    const part =
      preset.resolveProvider === 'inbound'
        ? await batchResolveTraceIdentityForInboundApi({ traceNos: batch })
        : await httpBatchResolveTraceIdentityForDispense({
            traceCodes: batch,
            sceneContext: preset.sceneContext,
          } satisfies BatchResolveTraceIdentityRequest);
    if (part.items?.length) merged.items!.push(...part.items);
    if (part.failItems?.length) merged.failItems!.push(...part.failItems);
  }

  return merged;
}
