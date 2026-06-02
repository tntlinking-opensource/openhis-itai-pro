/** 出 Tab 后端 panel=discharge 床位图占床患者 encounterId 集合 */
export function collectDischargeOccupantEncounterIds(
  bedBoardGroups: Array<{ beds: Array<{ occupant: { encounterId: string } | null }> }>,
): Set<string> {
  const ids = new Set<string>();
  for (const house of bedBoardGroups) {
    for (const bed of house.beds) {
      const encounterId = String(bed.occupant?.encounterId ?? '').trim();
      if (encounterId) {
        ids.add(encounterId);
      }
    }
  }
  return ids;
}

/** 当前选中 id 是否在出 Tab 床位图范围内 */
export function isEncounterInDischargeBedBoard(
  encounterId: string | null | undefined,
  scopeIds: Set<string>,
): boolean {
  const id = String(encounterId ?? '').trim();
  return id.length > 0 && scopeIds.has(id);
}
