/** Esc 整槽选中后再删：不要把空槽 ZWSP 填回去 */
export const EMR_SKIP_FILLABLE_RESTORE_META = 'emrSkipFillableRestore';

/** compositionend 后强制跑 IME 结算（recover / extend / normalize） */
export const EMR_FILLABLE_IME_SETTLE_META = 'emrFillableImeSettle';

/** 可填域运行时 bypass 标记（同步/回填跳过内部 restore） */
export const FILLABLE_KIT_META = 'emrFillableKitBypass';

/** 插入/点击进槽后聚焦 contenteditable 岛屿 */
export const EMR_FILLABLE_FOCUS_ISLAND_META = 'emrFillableFocusIsland';
