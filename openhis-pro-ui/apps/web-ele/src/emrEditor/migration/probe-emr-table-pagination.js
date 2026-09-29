/**
 * 病历表格分页调试（静态快照）
 *
 * 打开病历编辑页 → F12 Console：
 *   __emrPageDebug.dump()
 *
 * 期望：
 *   - softBreakDom >= 1（有换页时）
 *   - softSpacers[].at === "tableRow" 时，tableRowSoftDom >= 1
 *   - tableCellSoftDom === 0（不要再往格子里塞垫片）
 */
(function emrTablePaginationProbe() {
  const dbg = window.__emrPageDebug;
  if (!dbg || typeof dbg.dump !== 'function') {
    console.warn('[probe] __emrPageDebug 未挂上，请刷新病历页后再试');
    return null;
  }
  const snap = dbg.dump();
  console.log('[probe] dump', snap);
  return snap;
})();
