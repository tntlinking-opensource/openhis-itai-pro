import { mergeAttributes, Node } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import { getDataElementDef, serializeOptionsFromDef } from '../data-element-registry';
import EmrMetadataChipView from '../node-views/EmrMetadataChipView.vue';

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

/** 行内元数据：绑定 fieldKey，模板/患者信息带入 value；editable=false 时随外部值刷新且不可改 */
export const EmrMetadataChip = Node.create({
  name: 'emrMetadataChip',

  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,

  addAttributes() {
    return {
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
          const body = el.querySelector('.emr-bracket-fallback__body');
          const valueParsed =
            el.dataset.value ??
            (body instanceof HTMLElement
              ? (body.textContent?.trim() ?? '')
              : (el.textContent?.trim() ?? ''));
          return {
            editable: el.dataset.editable !== '0',
            fieldKey: el.dataset.fieldKey ?? '',
            label: el.dataset.label ?? '',
            value: valueParsed,
          };
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes, node }) {
    const v = String(node.attrs.value ?? '');
    const hint = String(node.attrs.label ?? node.attrs.fieldKey ?? '');
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-emr-metadata-chip': '1',
        'data-field-key': node.attrs.fieldKey,
        'data-label': node.attrs.label,
        'data-value': v,
        'data-editable': node.attrs.editable === false ? '0' : '1',
        class: 'emr-metadata-chip emr-metadata-chip--fallback',
        title: node.attrs.fieldKey,
      }),
      ['span', { class: 'emr-bracket-fallback__bracket', 'aria-hidden': 'true' }, '['],
      ['span', { class: 'emr-bracket-fallback__body', 'data-placeholder': hint }, v],
      ['span', { class: 'emr-bracket-fallback__bracket', 'aria-hidden': 'true' }, ']'],
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrMetadataChipView);
  },

  addCommands() {
    return {
      insertEmrMetadataChip:
        (attrs) =>
        ({ commands }) => {
          const def = getDataElementDef(attrs.fieldKey);
          const optionsJson = def ? serializeOptionsFromDef(def) : '[]';
          return commands.insertContent({
            type: 'emrDataElement',
            attrs: {
              editable: attrs.editable !== false,
              elementId: attrs.fieldKey,
              labelSnapshot: attrs.label,
              optionsJson,
              placeholder: attrs.label || '',
              value: attrs.value,
              widgetOverride: '',
            },
          });
        },
    };
  },
});
