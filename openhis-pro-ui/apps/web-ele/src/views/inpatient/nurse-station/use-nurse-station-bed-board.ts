import type { BedBoardPanel } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import type { BedBoardHouseGroup } from './services/map-bed-board';
import type { WardItem } from './types';

import { ref } from 'vue';

import { httpLoadAdmissionParticipantInit } from '#/api/inpatient/nurse-station/inpatient-nurse-station.http';
import { httpListBedBoard } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import {
  normalizeTerminologyOptions,
  type TerminologyOptionItem,
} from '#/utils/terminology/resolve-terminology-option-label';

import { mapBedBoardItemsToHouseGroups } from './services/map-bed-board';
import { resolveBedBoardScopeParams } from './services/resolve-bed-board-scope';

interface UseNurseStationBedBoardOptions {
  selectedHouseIds: () => string[];
  selectedWardIds: () => string[];
  wardOptions: () => WardItem[];
}

/** 入出转床位图：按筛选范围拉取 listBedBoard 并分组展示 */
export function useNurseStationBedBoard(options: UseNurseStationBedBoardOptions) {
  const bedBoardGroups = ref<BedBoardHouseGroup[]>([]);
  const bedBoardLoading = ref(false);
  const bedBoardPanel = ref<BedBoardPanel>('admission');
  const admCondCodeOptions = ref<TerminologyOptionItem[]>([]);

  /** 拉取患者病情字典（adm_cond_code），供床位图展示解析 */
  async function ensureAdmCondCodeOptions() {
    if (admCondCodeOptions.value.length > 0) {
      return;
    }
    const res = await httpLoadAdmissionParticipantInit({});
    admCondCodeOptions.value = normalizeTerminologyOptions(res.admCondCodeOptions);
  }

  /** 拉取当前筛选范围内的床位列表（入科 / 出 Tab 由 panel 区分） */
  async function loadBedBoard(panel: BedBoardPanel = bedBoardPanel.value) {
    bedBoardPanel.value = panel;
    bedBoardLoading.value = true;
    try {
      await ensureAdmCondCodeOptions();
      const scope = resolveBedBoardScopeParams(
        options.selectedHouseIds(),
        options.selectedWardIds(),
      );
      const res = await httpListBedBoard({ ...scope, panel });
      const items = res.items ?? [];
      bedBoardGroups.value = mapBedBoardItemsToHouseGroups(
        items,
        options.wardOptions(),
        admCondCodeOptions.value,
      );
    } finally {
      bedBoardLoading.value = false;
    }
  }

  return {
    bedBoardGroups,
    bedBoardLoading,
    bedBoardPanel,
    loadBedBoard,
  };
}
