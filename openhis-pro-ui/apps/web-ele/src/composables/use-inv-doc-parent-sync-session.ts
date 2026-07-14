/**
 * 库存单据工作站：侧栏切单时主区同步会话。
 *
 * 解决共性问题：
 * - 新建未保存草稿后点开已落库单，再切回草稿时，主区残留已落库明细（污染）；
 * - 旧逻辑用 local headerBusNo 判断「勿清空」，切走过已落库单后 headerBusNo 仍在，误判为刚保存；
 * - 异步 loadDetail 完成后回写，覆盖当前草稿。
 *
 * 同时：离开未保存草稿前 cache 明细，切回时 restore（避免只能清空）。
 */

export interface InvDocParentSyncContext {
  isDraftOrder: boolean;
  parentBusNo?: null | string;
}

export interface InvDocParentSyncHandlers<TSnap> {
  /** 主区当前已落库单号（无则视为未保存） */
  getLocalPersistedBusNo: () => null | string | undefined;
  /** 离开未保存草稿前拍摄快照 */
  captureDraft: () => TSnap;
  /** 切回草稿时恢复快照 */
  restoreDraft: (snap: TSnap) => void | Promise<void>;
  /** 无缓存的本地草稿：清空主区 */
  resetForCreate: () => void | Promise<void>;
  /** 按单号拉详情；须在写入前用 isLoadStale(gen) 防串扰 */
  loadByBusNo: (busNo: string, loadGen: number) => Promise<void>;
}

export function useInvDocParentSyncSession<TSnap>() {
  let draftSnap: null | TSnap = null;
  /** 当前是否仍处在「本地草稿会话」中（未切到已落库卡片） */
  let wasLocalDraftSession = false;
  let loadGen = 0;

  /** 开始一次详情加载，返回世代号 */
  function beginLoad(): number {
    loadGen += 1;
    return loadGen;
  }

  /** 详情回调是否已过期（侧栏已切走） */
  function isLoadStale(gen: number): boolean {
    return gen !== loadGen;
  }

  /** 作废在途详情加载（切回草稿 / 丢弃时调用） */
  function invalidateLoads(): void {
    loadGen += 1;
  }

  /**
   * 新增单据或丢弃草稿：清缓存并作废在途加载。
   * 通常挂在 resetKey 变化时调用。
   */
  function discardDraftSession(): void {
    draftSnap = null;
    wasLocalDraftSession = false;
    invalidateLoads();
  }

  /** 保存成功后仅丢弃草稿缓存（保留当前已落库主区） */
  function clearDraftSnap(): void {
    draftSnap = null;
  }

  /**
   * 侧栏选中变化 / resetKey 变化时同步主区。
   * 须与 props.busNo、isDraftOrder、resetKey 的 watch 一并触发。
   */
  async function syncFromParentContext(
    ctx: InvDocParentSyncContext,
    handlers: InvDocParentSyncHandlers<TSnap>,
  ): Promise<void> {
    const parentBusNo = String(ctx.parentBusNo ?? '').trim();
    const localNo = String(handlers.getLocalPersistedBusNo() ?? '').trim();

    if (ctx.isDraftOrder) {
      invalidateLoads();

      // 仍在同一草稿会话内且主区已写出单号（刚保存、父级尚未回选）：禁止清空，防二次 create
      if (wasLocalDraftSession && localNo !== '') {
        return;
      }

      if (draftSnap !== null) {
        await handlers.restoreDraft(draftSnap);
        wasLocalDraftSession = true;
        return;
      }

      await handlers.resetForCreate();
      wasLocalDraftSession = true;
      return;
    }

    // 离开未保存本地草稿：先缓存，再加载已落库单（避免切回时被污染且内容丢失）
    if (wasLocalDraftSession && localNo === '') {
      draftSnap = handlers.captureDraft();
    }
    wasLocalDraftSession = false;

    if (parentBusNo) {
      const gen = beginLoad();
      await handlers.loadByBusNo(parentBusNo, gen);
      return;
    }

    invalidateLoads();
    await handlers.resetForCreate();
  }

  return {
    beginLoad,
    clearDraftSnap,
    discardDraftSession,
    isLoadStale,
    invalidateLoads,
    syncFromParentContext,
  };
}
