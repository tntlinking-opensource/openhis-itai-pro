import type { EditorState } from '@tiptap/pm/state';
import type { EditorView } from '@tiptap/pm/view';

import { EMR_FILLABLE_IME_SETTLE_META } from '../kit/meta';
import { EMR_FILLABLE_MARK } from '../core/model';
import { collectFillableMarkRanges, resolveImeCompositionSlotId } from '../core/resolve';
import { normalizeStaleFillableStoredMarks } from './stored-marks-policy';

/** IME 生命周期：组字中 / 上屏结算中禁止改选区、吸槽、拉回空槽 */
export type EmrImePhase = 'idle' | 'composing' | 'settling';

export interface EmrImeControllerStorage {
  phase: EmrImePhase;
  /** @deprecated 用 lockedSlotId；保留兼容装饰读取 */
  imeSlotId: string | null;
  lockedSlotId: string | null;
  /** compositionstart 时的文档快照，供上屏后 recover 对比 */
  compositionBaseState: EditorState | null;
}

export function createEmrImeControllerStorage(): EmrImeControllerStorage {
  return { phase: 'idle', imeSlotId: null, lockedSlotId: null, compositionBaseState: null };
}

export function isImeSideEffectBlocked(storage: EmrImeControllerStorage): boolean {
  return storage.phase === 'composing' || storage.phase === 'settling';
}

function syncLegacySlotId(storage: EmrImeControllerStorage) {
  storage.imeSlotId = storage.lockedSlotId;
}

/** 从编辑器取 IME 状态（分页 / 装饰 / wordlike-ops 统一入口） */
export function getFillableImeStorage(editor: {
  storage: { emrFillableIme?: EmrImeControllerStorage };
}): EmrImeControllerStorage | null {
  return editor.storage.emrFillableIme ?? null;
}

/** 组字中或上屏结算中：禁止改选区、分页重排、拉回空槽 */
export function isFillableImeActive(editor: {
  storage: { emrFillableIme?: EmrImeControllerStorage };
  view?: { composing?: boolean } | null;
}): boolean {
  if (editor.view?.composing) return true;
  const storage = getFillableImeStorage(editor);
  return storage != null && isImeSideEffectBlocked(storage);
}

/**
 * compositionstart：先清域外残留 storedMarks，再按槽内/槽尾续写规则锁定 slotId。
 * 禁止仅凭 storedMarks 在域外乱锁（否则槽前组字会被吸进元数据）。
 */
export function onFillableImeCompositionStart(
  view: EditorView,
  storage: EmrImeControllerStorage,
): void {
  storage.phase = 'composing';
  storage.compositionBaseState = view.state;

  const staleTr = normalizeStaleFillableStoredMarks(view.state);
  if (staleTr) {
    view.dispatch(staleTr);
  }

  const st = view.state;
  storage.lockedSlotId = resolveImeCompositionSlotId(st.doc, st.selection.head, st.storedMarks);
  syncLegacySlotId(storage);

  // 槽内组字：补回 storedMarks，避免上屏后 extend 因 [] 放弃吸字
  const locked = storage.lockedSlotId;
  if (locked) {
    const hit = collectFillableMarkRanges(st.doc).find(
      (h) => h.editable && String(h.mark?.attrs.slotId ?? '') === locked,
    );
    if (
      hit?.mark &&
      !st.storedMarks?.some(
        (m) => m.type.name === EMR_FILLABLE_MARK && String(m.attrs.slotId ?? '') === locked,
      )
    ) {
      const markTr = st.tr.setStoredMarks([hit.mark]);
      markTr.setMeta('addToHistory', false);
      view.dispatch(markTr);
    }
  }
}

/**
 * compositionend：进入 settling，等 composing=false 后派发结算事务。
 * 组字中 doc 变更常被 appendTransaction 跳过，必须在此补 recover。
 */
export function onFillableImeCompositionEnd(
  view: EditorView,
  storage: EmrImeControllerStorage,
): void {
  storage.phase = 'settling';

  const flushSettle = () => {
    if (view.composing) {
      requestAnimationFrame(flushSettle);
      return;
    }
    const tr = view.state.tr.setMeta(EMR_FILLABLE_IME_SETTLE_META, true);
    tr.setMeta('addToHistory', false);
    view.dispatch(tr);
    requestAnimationFrame(() => {
      storage.phase = 'idle';
      storage.lockedSlotId = null;
      storage.compositionBaseState = null;
      syncLegacySlotId(storage);
    });
  };
  requestAnimationFrame(flushSettle);
}
