import type { EditorState, Transaction } from '@tiptap/pm/state';

import { EMR_FILLABLE_MARK } from '../core/model';
import { extendFillableMarkOverImeComposition, recoverLostFillableMarkSlot } from '../core/resolve';
import { normalizeStaleFillableStoredMarks } from './stored-marks-policy';
import type { EmrImeControllerStorage } from './ime-controller';
import { isImeSideEffectBlocked } from './ime-controller';

export interface FillableImeSettleContext {
  lockedSlotId: string | null;
  storage: EmrImeControllerStorage;
  trs?: readonly Transaction[];
}

/**
 * 上屏后统一结算（顺序固定，禁止插件各自抢跑）：
 * 1. recover — IME replace 剥 mark 时包回元数据
 * 2. extend — 槽尾续写/组字锁定：把槽外上屏字补进 mark
 * 3. normalize — 清域外残留 storedMarks
 */
export function settleFillableImeAfterDocChange(
  oldState: EditorState,
  newState: EditorState,
  ctx: FillableImeSettleContext,
): Transaction | null {
  if (isImeSideEffectBlocked(ctx.storage) && ctx.storage.phase === 'composing') {
    return null;
  }

  const trs = ctx.trs ?? [];

  const recoverTr = recoverLostFillableMarkSlot(trs, oldState, newState);
  let workState = newState;
  if (recoverTr) {
    workState = newState.apply(recoverTr);
  }

  const slotId = ctx.lockedSlotId;
  const sticky = Boolean(oldState.storedMarks?.some((m) => m.type.name === EMR_FILLABLE_MARK));

  if ((slotId != null && slotId !== '') || sticky) {
    const extendTr = extendFillableMarkOverImeComposition(
      oldState,
      {
        doc: workState.doc,
        selection: workState.selection,
        storedMarks: workState.storedMarks,
        tr: recoverTr ?? newState.tr,
      },
      { slotId },
    );
    if (extendTr) {
      if (recoverTr) {
        let tr = recoverTr;
        for (const step of extendTr.steps) {
          tr = tr.step(step);
        }
        if (extendTr.selectionSet) {
          tr = tr.setSelection(extendTr.selection);
        }
        if (extendTr.storedMarksSet) {
          tr = tr.setStoredMarks(extendTr.storedMarks);
        }
        tr.setMeta('addToHistory', false);
        return tr;
      }
      return extendTr;
    }
  }

  if (recoverTr) return recoverTr;

  if (isImeSideEffectBlocked(ctx.storage)) return null;

  return normalizeStaleFillableStoredMarks(workState);
}
