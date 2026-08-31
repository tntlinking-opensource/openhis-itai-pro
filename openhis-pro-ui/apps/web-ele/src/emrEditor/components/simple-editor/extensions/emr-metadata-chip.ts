import { mergeAttributes, Node } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { ReplaceStep } from '@tiptap/pm/transform';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import { isWholeFillableNodeDelete } from '../emr-data-element-text';
import { buildFillableNodeInsertJson } from '../emr-fillable-json';
import { fillableNodeExtraClass } from '../node-views/fillable-field-view.shared';
import { resolveInsertEmrMetadataChip } from '../../../fillable-field';
import EmrFillableFieldView from '../node-views/EmrFillableFieldView.vue';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrMetadataChip: {
      insertEmrMetadataChip: (attrs: {
        editable?: boolean;
        fieldKey: string;
        label: string;
        value: string;
      }) => ReturnType;
    };
  }
}

const readonlyGuardKey = new PluginKey('emrMetadataChipReadonlyGuard');

/**
 * 元数据：方案 B atom 内联节点 + NodeView 岛屿输入。
 */
export const EmrMetadataChip = Node.create({
  name: 'emrMetadataChip',

  group: 'inline',
  inline: true,
  atom: true,
  content: '',
  selectable: true,

  addAttributes() {
    return {
      slotId: { default: '' },
      fieldKey: { default: '' },
      label: { default: '' },
      value: { default: '' },
      editable: {
        default: true,
        parseHTML: (element) =>
          typeof element === 'string' || (element as HTMLElement).dataset.editable !== '0',
        renderHTML: (attrs) => ({
          'data-editable': attrs.editable === false ? '0' : '1',
        }),
      },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-emr-metadata-chip]',
        getAttrs: (el) => {
          if (!(el instanceof HTMLElement)) return false;
          return {
            slotId: el.dataset.slotId ?? '',
            fieldKey: el.dataset.fieldKey ?? '',
            label: el.dataset.label ?? '',
            value: el.dataset.value ?? el.textContent?.trim() ?? '',
            editable: el.dataset.editable !== '0',
          };
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes, node }) {
    const placeholder = String(HTMLAttributes.label ?? '填写');
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-emr-metadata-chip': '1',
        'data-field-key': node.attrs.fieldKey,
        'data-placeholder': placeholder,
        'data-value': String(node.attrs.value ?? ''),
        class: `${fillableNodeExtraClass(node, 'metadata')}${
          node.attrs.editable === false ? ' emr-metadata-chip--readonly' : ''
        }`,
      }),
      String(node.attrs.value ?? ''),
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrFillableFieldView);
  },

  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: readonlyGuardKey,
        filterTransaction(tr, state) {
          if (!tr.docChanged) return true;
          let doc = state.doc;
          for (const step of tr.steps) {
            if (step instanceof ReplaceStep) {
              const inserted = step.slice.content.size;
              if (isWholeFillableNodeDelete(doc, step.from, step.to, inserted)) {
                continue;
              }
              const $from = doc.resolve(step.from);
              for (let d = $from.depth; d > 0; d--) {
                const n = $from.node(d);
                if (n.type.name === 'emrMetadataChip' && n.attrs.editable === false) {
                  return false;
                }
              }
              const node = doc.nodeAt(step.from);
              if (
                node?.type.name === 'emrMetadataChip' &&
                node.attrs.editable === false &&
                step.from === step.to - node.nodeSize
              ) {
                return false;
              }
            }
            const applied = step.apply(doc);
            if (applied.failed || !applied.doc) return false;
            doc = applied.doc;
          }
          return true;
        },
      }),
    ];
  },

  addCommands() {
    return {
      insertEmrMetadataChip:
        (attrs) =>
        ({ chain, commands }) => {
          const resolved = resolveInsertEmrMetadataChip(attrs);
          if (resolved.kind === 'dataElement') {
            return commands.insertContent(resolved.content);
          }
          return chain().insertContent(resolved.insertContent).command(resolved.afterInsert).run();
        },
    };
  },
});
