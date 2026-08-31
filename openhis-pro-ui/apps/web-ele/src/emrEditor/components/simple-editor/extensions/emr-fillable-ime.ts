import { Extension } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';

import {
  createEmrImeControllerStorage,
  onFillableImeCompositionEnd,
  onFillableImeCompositionStart,
  type EmrImeControllerStorage,
} from '../../../fillable-field/runtime/ime-controller';
import { EMR_FILLABLE_IME_SETTLE_META } from '../../../fillable-field/kit/meta';
import { settleFillableImeAfterDocChange } from '../../../fillable-field/runtime/ime-settle';

export const EMR_FILLABLE_IME_PLUGIN_KEY = new PluginKey('emrFillableIme');

/**
 * 可填域中文 IME 专用扩展（与 wordlike-ops / 分页 / 装饰解耦）。
 *
 * 职责：
 * - 组字生命周期 phase（idle → composing → settling → idle）
 * - compositionstart/end
 * - 上屏后 extend + 清 storedMarks（单点结算）
 *
 * 不做：点击吸槽、箭头、handleTextInput（仍在 EmrWordlikeOps）
 */
export const EmrFillableIme = Extension.create({
  name: 'emrFillableIme',

  addStorage() {
    return createEmrImeControllerStorage();
  },

  addProseMirrorPlugins() {
    const editor = this.editor;

    return [
      new Plugin({
        key: EMR_FILLABLE_IME_PLUGIN_KEY,
        appendTransaction(trs, oldState, newState) {
          const storage = editor.storage.emrFillableIme as EmrImeControllerStorage;
          const isSettle = trs.some((tr) => tr.getMeta(EMR_FILLABLE_IME_SETTLE_META));
          const docChanged = trs.some((tr) => tr.docChanged);

          if (!isSettle && !docChanged) return null;
          if (!isSettle && editor.view?.composing) return null;

          const baseOld =
            isSettle && storage.compositionBaseState ? storage.compositionBaseState : oldState;

          return settleFillableImeAfterDocChange(baseOld, newState, {
            lockedSlotId: storage.lockedSlotId,
            storage,
            trs: docChanged ? trs : [],
          });
        },
        props: {
          handleDOMEvents: {
            compositionstart(view) {
              onFillableImeCompositionStart(
                view,
                editor.storage.emrFillableIme as EmrImeControllerStorage,
              );
              return false;
            },
            compositionend(view) {
              onFillableImeCompositionEnd(
                view,
                editor.storage.emrFillableIme as EmrImeControllerStorage,
              );
              return false;
            },
          },
        },
      }),
    ];
  },
});
