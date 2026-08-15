/**
 * 库存单据工作站：侧栏切单时主区同步会话。
 *
 * 解决共性问题：
 * - 新建未保存草稿后点开已落库单，再切回草稿时，主区残留已落库明细（污染）；
 * - 旧逻辑用 local headerBusNo 判断「勿清空」，切走过已落库单后 headerBusNo 仍在，误判为刚保存；
 * - 异步 loadDetail 完成后回写，覆盖当前草稿。
 *
 * 同时：离开未保存草稿前 cache 明细，切回时 restore（避免只能清空）。
 * 可选 localStorage 持久化：切菜单/刷新后仍可按 scene+docKey 恢复（场景隔离防串单）。
 *
 * 落盘硬约束：切单时 props 已是「新单」docKey，必须用 activePersistDocKey（离开前的键）写入，
 * 否则草稿会写到新 busNo 下，刷新/再进草稿必然丢码。
 */

import {
  clearInvDocDraftCache,
  normalizeInvDocDraftDocKey,
  readInvDocDraftCache,
  writeInvDocDraftCache,
  type InvDocDraftScene,
} from '#/utils/inventory/inv-doc-draft-cache';

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

/** localStorage 持久化配置（按 scene 隔离） */
export interface InvDocParentSyncPersistConfig {
  scene: InvDocDraftScene | (() => InvDocDraftScene);
  /** 当前单据缓存键：草稿用 _clientDraftId，已落库用 busNo */
  getDocKey: () => null | string | undefined;
  /** 侧栏卡片展示字段（恢复队列用） */
  getCardMeta?: () => Record<string, unknown>;
}

export function useInvDocParentSyncSession<TSnap>(persist?: InvDocParentSyncPersistConfig) {
  let draftSnap: null | TSnap = null;
  /** 当前是否仍处在「本地草稿会话」中（未切到已落库卡片） */
  let wasLocalDraftSession = false;
  let loadGen = 0;
  /**
   * 当前主区正在编辑的缓存键（进入会话时钉死）。
   * 切单时 props 已变，落盘必须用本字段，不能现读 getDocKey()。
   */
  let activePersistDocKey = '';

  /** 解析场景名 */
  function resolveScene(): string {
    if (!persist) return '';
    const raw = typeof persist.scene === 'function' ? persist.scene() : persist.scene;
    return String(raw ?? '').trim();
  }

  /** 解析 props 上的「目标」docKey（新选中单），不改 activePersistDocKey */
  function peekDocKey(): string {
    if (!persist) return '';
    return normalizeInvDocDraftDocKey(persist.getDocKey());
  }

  /** 进入某单据会话后钉死落盘键 */
  function bindActivePersistDocKey(docKey: string): void {
    const key = normalizeInvDocDraftDocKey(docKey);
    if (key) activePersistDocKey = key;
  }

  /** 按指定 docKey 写入 localStorage */
  function writePersistToKey(docKey: string, snap: TSnap): void {
    if (!persist) return;
    const scene = resolveScene();
    const key = normalizeInvDocDraftDocKey(docKey);
    if (!scene || !key) return;
    writeInvDocDraftCache({
      scene,
      docKey: key,
      snap,
      cardMeta: persist.getCardMeta?.(),
    });
  }

  /** 将快照写入当前活动键（无活动键时回落 peekDocKey） */
  function writePersist(snap: TSnap): void {
    const key = activePersistDocKey || peekDocKey();
    if (!key) return;
    writePersistToKey(key, snap);
  }

  /** 从 localStorage 读取指定键快照 */
  function readPersistByKey(docKey: string): null | TSnap {
    if (!persist) return null;
    const scene = resolveScene();
    const key = normalizeInvDocDraftDocKey(docKey);
    if (!scene || !key) return null;
    const entry = readInvDocDraftCache<TSnap>(scene, key);
    return entry?.snap ?? null;
  }

  /** 读取当前目标键（peek）的快照 */
  function readPersist(): null | TSnap {
    return readPersistByKey(peekDocKey());
  }

  /** 清除指定或当前活动键的磁盘缓存 */
  function clearPersist(docKey?: string): void {
    if (!persist) return;
    const scene = resolveScene();
    const key = normalizeInvDocDraftDocKey(docKey) || activePersistDocKey || peekDocKey();
    if (!scene || !key) return;
    clearInvDocDraftCache(scene, key);
    if (key === activePersistDocKey) activePersistDocKey = '';
  }

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
   * 新增单据或丢弃草稿：清内存会话并作废在途加载。
   * 不在此清 localStorage：resetKey 与 sync 同拍时会误删刚写入/待恢复的键；
   * 显式丢弃由侧栏 discardSidebarDraftCaches / 删除单据 clearPersist。
   */
  function discardDraftSession(): void {
    draftSnap = null;
    wasLocalDraftSession = false;
    activePersistDocKey = '';
    invalidateLoads();
  }

  /** 保存成功后丢弃内存草稿快照；默认同时清当前活动键 */
  function clearDraftSnap(options?: { clearActivePersist?: boolean }): void {
    draftSnap = null;
    if (options?.clearActivePersist === false) {
      return;
    }
    clearPersist();
  }

  /**
   * 主动落盘当前主区（页卸载 / 切菜单 / 扫码后）。
   * 用 activePersistDocKey，避免 props 已切走写错键。
   */
  function flushPersist(
    handlers: Pick<InvDocParentSyncHandlers<TSnap>, 'captureDraft'> & {
      /** 兼容旧调用方，已忽略 */
      getLocalPersistedBusNo?: InvDocParentSyncHandlers<TSnap>['getLocalPersistedBusNo'];
    },
  ): void {
    if (!persist) return;
    const key = activePersistDocKey || peekDocKey();
    if (!key) return;
    const snap = handlers.captureDraft();
    if (wasLocalDraftSession) {
      draftSnap = snap;
    }
    writePersistToKey(key, snap);
    if (!activePersistDocKey) {
      bindActivePersistDocKey(key);
    }
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
    const targetDocKey = ctx.isDraftOrder ? peekDocKey() : parentBusNo || peekDocKey();

    // 切单瞬间 props 已是新单，但网格仍是旧单：必须先按离开键落盘
    const leavingKey = activePersistDocKey;
    if (leavingKey && leavingKey !== targetDocKey) {
      const leavingSnap = handlers.captureDraft();
      if (wasLocalDraftSession) {
        draftSnap = leavingSnap;
      }
      writePersistToKey(leavingKey, leavingSnap);
    }

    if (ctx.isDraftOrder) {
      invalidateLoads();

      // 仍在同一草稿会话内且主区已写出单号（刚保存、父级尚未回选）：禁止清空，防二次 create
      if (wasLocalDraftSession && localNo !== '') {
        if (localNo) {
          bindActivePersistDocKey(localNo);
        }
        return;
      }

      if (draftSnap !== null) {
        await handlers.restoreDraft(draftSnap);
        wasLocalDraftSession = true;
        bindActivePersistDocKey(targetDocKey || leavingKey);
        return;
      }

      // 内存无快照时尝试 localStorage（切菜单/刷新后再进）
      const diskSnap = readPersistByKey(targetDocKey);
      if (diskSnap !== null) {
        draftSnap = diskSnap;
        await handlers.restoreDraft(diskSnap);
        wasLocalDraftSession = true;
        bindActivePersistDocKey(targetDocKey);
        return;
      }

      await handlers.resetForCreate();
      wasLocalDraftSession = true;
      bindActivePersistDocKey(targetDocKey);
      return;
    }

    wasLocalDraftSession = false;

    if (parentBusNo) {
      const gen = beginLoad();
      await handlers.loadByBusNo(parentBusNo, gen);
      bindActivePersistDocKey(parentBusNo);
      return;
    }

    invalidateLoads();
    await handlers.resetForCreate();
    activePersistDocKey = '';
  }

  return {
    beginLoad,
    bindActivePersistDocKey,
    /** 当前落盘键（供保存后迁键 / 调试） */
    getActivePersistDocKey: () => activePersistDocKey,
    clearDraftSnap,
    clearPersist,
    discardDraftSession,
    flushPersist,
    isLoadStale,
    invalidateLoads,
    readPersistByKey,
    syncFromParentContext,
    writePersistToKey,
  };
}
