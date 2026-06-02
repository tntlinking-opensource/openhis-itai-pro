/** 与左侧筛选一致：单选病房优先，其次单选病区 */
export function resolveBedBoardScopeParams(
  selectedHouseIds: string[],
  selectedWardIds: string[],
): { houseId?: string; wardId?: string } {
  if (selectedHouseIds.length === 1) {
    return { houseId: selectedHouseIds[0] };
  }
  if (selectedHouseIds.length === 0 && selectedWardIds.length === 1) {
    return { wardId: selectedWardIds[0] };
  }
  return {};
}
