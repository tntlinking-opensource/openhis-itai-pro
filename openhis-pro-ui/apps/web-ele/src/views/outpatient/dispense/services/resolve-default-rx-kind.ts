import type { EncounterMedicationDispenseSlipRowDto } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { httpPageEncounterMedicationDispenseSlips } from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.http';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

import type { DispenseRxKindBadgeCounts } from '../components/types';

import { mapSlipRowsToPrescriptionBlocks } from './map-api-to-dispense-ui';

export interface EncounterRxKindSummary {
  badgeCounts: DispenseRxKindBadgeCounts;
  defaultKind: 'herbal' | 'western';
}

/** 拉取就诊下全部 slip，统计中西药处方块数并推断默认 tab */
export async function loadEncounterRxKindSummary(params: {
  dispenseStatusEnumIn: string[];
  encounterId: string;
}): Promise<EncounterRxKindSummary> {
  const res = await httpPageEncounterMedicationDispenseSlips({
    encounterId: params.encounterId,
    dispenseStatusEnumIn: params.dispenseStatusEnumIn,
    pageNo: 1,
    pageSize: 200,
  });
  const { content: slipList } =
    parsePagedRowsFromApiData<EncounterMedicationDispenseSlipRowDto>(res);

  const blocks = mapSlipRowsToPrescriptionBlocks(slipList);
  let western = 0;
  let herbal = 0;
  for (const block of blocks) {
    if (block.rxType === 'herbal') herbal += 1;
    else western += 1;
  }

  const defaultKind: 'herbal' | 'western' = western === 0 && herbal > 0 ? 'herbal' : 'western';

  return {
    defaultKind,
    badgeCounts: { western, herbal },
  };
}
