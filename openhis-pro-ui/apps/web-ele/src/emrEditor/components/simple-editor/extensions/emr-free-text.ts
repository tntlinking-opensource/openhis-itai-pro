import { mergeAttributes, Node } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import { buildFillableNodeInsertJson } from '../emr-fillable-json';
import { fillableNodeExtraClass } from '../node-views/fillable-field-view.shared';
import { keepVueNodeViewIfEq } from '../node-views/keep-vue-node-view-if-eq';
import { fillableCaretAfterInsertCommand } from '../../../fillable-field/runtime/caret-policy';
import EmrFillableFieldView from '../node-views/EmrFillableFieldView.vue';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrFreeText: {
      insertEmrFreeText: (
        attrs?: Partial<{
          label: string;
          placeholder: string;
          required?: boolean;
          requiredMessage?: string;
          value: string;
        }>,
      ) => ReturnType;
    };
  }
}

/**
 * 自由文本：方案 B atom 内联节点 + NodeView 岛屿输入。
 * 存盘 JSON：emrFreeText + attrs.value，无 content。
 */
export const EmrFreeText = Node.create({
  name: 'emrFreeText',

  group: 'inline',
  inline: true,
  atom: true,
  content: '',
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      slotId: { default: '' },
      label: { default: '填写' },
      placeholder: { default: '填写' },
      value: { default: '' },
      required: { default: false },
      requiredMessage: { default: '' },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-emr-free-text]',
        getAttrs: (el) => {
          if (!(el instanceof HTMLElement)) return false;
          return {
            slotId: el.dataset.slotId ?? '',
            label: el.dataset.label ?? el.dataset.placeholder ?? '填写',
            placeholder: el.dataset.placeholder ?? el.dataset.label ?? '填写',
            value: el.dataset.value ?? el.textContent?.trim() ?? '',
            required: el.dataset.required === '1',
            requiredMessage: el.dataset.requiredMessage ?? '',
          };
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes, node }) {
    const placeholder = String(HTMLAttributes.placeholder ?? HTMLAttributes.label ?? '填写');
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-emr-free-text': '1',
        'data-placeholder': placeholder,
        'data-value': String(node.attrs.value ?? ''),
        class: fillableNodeExtraClass(node, 'freeText'),
      }),
      String(node.attrs.value ?? ''),
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrFillableFieldView, { update: keepVueNodeViewIfEq });
  },

  addCommands() {
    return {
      insertEmrFreeText:
        (attrs) =>
        ({ chain }) => {
          const label = attrs?.label ?? attrs?.placeholder ?? '填写';
          const placeholder = attrs?.placeholder ?? label;
          const value = attrs?.value !== undefined ? attrs.value : '';
          return chain()
            .insertContent(
              buildFillableNodeInsertJson({
                kind: 'freeText',
                label,
                placeholder,
                value,
                required: attrs?.required === true,
                requiredMessage: attrs?.requiredMessage ?? '',
              }),
            )
            .command(fillableCaretAfterInsertCommand('toolbar'))
            .run();
        },
    };
  },
});
