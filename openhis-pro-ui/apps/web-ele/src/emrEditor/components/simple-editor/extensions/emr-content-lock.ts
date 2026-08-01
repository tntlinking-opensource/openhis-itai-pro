import type { Fragment, Node as PMNode } from '@tiptap/pm/model';

import { Extension } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import {
  AddMarkStep,
  AddNodeMarkStep,
  AttrStep,
  DocAttrStep,
  RemoveMarkStep,
  RemoveNodeMarkStep,
  ReplaceAroundStep,
  ReplaceStep,
} from '@tiptap/pm/transform';

import { EMR_FIXED_TEXT_ALLOW_MUTATE_META } from './emr-fixed-text';

/** 与患者同步、父组件 setContent 等需跳过内容锁时打在 transaction 上 */
export const EMR_CONTENT_LOCK_BYPASS_META = 'emrContentLockBypass';

const pluginKey = new PluginKey('emrContentLock');

/** 固定模板时仍允许医生改写的行内节点（可编辑元数据 + 自由文本 + 勾选） */
function isAllowedFillableIsland(node: null | PMNode | undefined): boolean {
  if (!node) return false;
  if (node.type.name === 'emrFreeText') return true;
  if (node.type.name === 'emrCheckboxField') return true;
  if (node.type.name === 'emrDataElement' && node.attrs.editable !== false) return true;
  if (node.type.name === 'emrMetadataChip' && node.attrs.editable !== false) return true;
  return false;
}

function fragmentHasText(fragment: Fragment): boolean {
  let found = false;
  fragment.forEach((child) => {
    child.descendants((n) => {
      if (n.isText && (n.text?.length ?? 0) > 0) {
        found = true;
        return false;
      }
    });
  });
  return found;
}

/** 版式/结构类节点：不允许在锁定态被增删替换（同步走 bypass） */
function fragmentHasStructuralViolation(fragment: Fragment): boolean {
  let bad = false;
  fragment.forEach((child) => {
    child.descendants((node) => {
      const name = node.type.name;
      if (name === 'emrFixedText') {
        bad = true;
        return false;
      }
      if (
        name === 'image' ||
        name === 'horizontalRule' ||
        name === 'emrHorizontalDashed' ||
        name === 'emrPageBreak' ||
        name === 'emrToc' ||
        name === 'emrTextField' ||
        name === 'emrSelectField' ||
        name === 'table' ||
        name === 'tableRow' ||
        name === 'tableCell' ||
        name === 'tableHeader'
      ) {
        bad = true;
        return false;
      }
      if (
        node.isBlock &&
        (name === 'bulletList' ||
          name === 'orderedList' ||
          name === 'listItem' ||
          name === 'taskList' ||
          name === 'taskItem')
      ) {
        bad = true;
        return false;
      }
    });
  });
  return bad;
}

/** 删除/插入片段中是否出现「非允许岛屿」的节点（用于无 ProseMirror Text 的结构变更） */
function fragmentHasDisallowedNodes(fragment: Fragment): boolean {
  let bad = false;
  fragment.forEach((root) => {
    root.descendants((node) => {
      if (node === root) return;
      if (['blockquote', 'heading', 'paragraph'].includes(node.type.name)) return;
      if (isAllowedFillableIsland(node)) return;
      if (node.type.name === 'hardBreak') {
        bad = true;
        return false;
      }
      if (node.isText) {
        bad = true;
        return false;
      }
      if (node.type.spec.atom || node.isLeaf) {
        bad = true;
        return false;
      }
    });
  });
  return bad;
}

function fragmentTopLevelHasBlock(fragment: Fragment): boolean {
  let found = false;
  fragment.forEach((node) => {
    if (node.isBlock) {
      found = true;
    }
  });
  return found;
}

function replaceStepAllowed(doc: PMNode, step: ReplaceStep): boolean {
  const del = doc.slice(step.from, step.to);
  const ins = step.slice;

  if (fragmentHasText(del.content) || fragmentHasText(ins.content)) {
    return false;
  }
  if (fragmentTopLevelHasBlock(del.content) || fragmentTopLevelHasBlock(ins.content)) {
    return false;
  }
  if (fragmentHasStructuralViolation(del.content) || fragmentHasStructuralViolation(ins.content)) {
    return false;
  }
  if (fragmentHasDisallowedNodes(del.content) || fragmentHasDisallowedNodes(ins.content)) {
    return false;
  }
  return true;
}

function attrStepAllowed(doc: PMNode, step: AttrStep): boolean {
  const $pos = doc.resolve(step.pos);
  const after = $pos.nodeAfter;
  const name = after?.type.name;
  if (name === 'horizontalRule' || name === 'emrHorizontalDashed') {
    return true;
  }
  return isAllowedFillableIsland(after);
}

export const EmrContentLock = Extension.create({
  name: 'emrContentLock',

  addStorage() {
    return {
      enabled: false,
    };
  },

  addProseMirrorPlugins() {
    const editor = this.editor;

    return [
      new Plugin({
        key: pluginKey,
        filterTransaction(tr, state) {
          if (!editor.storage.emrContentLock?.enabled) {
            return true;
          }
          if (
            tr.getMeta(EMR_CONTENT_LOCK_BYPASS_META) ||
            tr.getMeta(EMR_FIXED_TEXT_ALLOW_MUTATE_META)
          ) {
            return true;
          }
          if (!tr.docChanged) {
            return true;
          }

          let doc: PMNode = state.doc;
          for (let i = 0; i < tr.steps.length; i++) {
            const step = tr.steps[i];
            if (step instanceof ReplaceStep) {
              if (!replaceStepAllowed(doc, step)) {
                return false;
              }
            } else if (step instanceof ReplaceAroundStep) {
              return false;
            } else if (step instanceof AddMarkStep || step instanceof RemoveMarkStep) {
              return false;
            } else if (step instanceof AddNodeMarkStep || step instanceof RemoveNodeMarkStep) {
              return false;
            } else if (step instanceof AttrStep) {
              if (!attrStepAllowed(doc, step)) {
                return false;
              }
            } else if (step instanceof DocAttrStep) {
              return false;
            } else {
              return false;
            }

            const applied = step.apply(doc);
            if (applied.failed || !applied.doc) {
              return false;
            }
            doc = applied.doc;
          }
          return true;
        },
      }),
    ];
  },
});

declare module '@tiptap/core' {
  interface Storage {
    /** {@link EmrContentLock} 的 addStorage，未注册扩展时不存在 */
    emrContentLock?: {
      enabled: boolean;
    };
  }
}
