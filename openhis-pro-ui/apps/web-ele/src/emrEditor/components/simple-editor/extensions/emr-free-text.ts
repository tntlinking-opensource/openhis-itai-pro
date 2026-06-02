import { mergeAttributes, Node } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import EmrFreeTextView from '../node-views/EmrFreeTextView.vue';

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrFreeText: {
      insertEmrFreeText: (
        attrs?: Partial<{ label: string; placeholder: string; value: string }>,
      ) => ReturnType;
    };
  }
}

/** 行内自由文本：外观类似元数据芯片，不绑 fieldKey，值仅存于文档内 */
export const EmrFreeText = Node.create({
  name: 'emrFreeText',

  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      label: {
        default: '填写',
        parseHTML: (el) => {
          if (typeof el === 'string') return '填写';
          const h = el as HTMLElement;
          return h.dataset.label ?? h.dataset.placeholder ?? '填写';
        },
        renderHTML: (attrs) => ({
          'data-label': String(attrs.label ?? attrs.placeholder ?? '填写'),
        }),
      },
      placeholder: {
        default: '填写',
        parseHTML: (el) => {
          if (typeof el === 'string') return '填写';
          const h = el as HTMLElement;
          return h.dataset.placeholder ?? h.dataset.label ?? '填写';
        },
        renderHTML: (attrs) => ({
          'data-placeholder': String(attrs.placeholder ?? attrs.label ?? '填写'),
        }),
      },
      value: {
        default: '',
        parseHTML: (el) => {
          if (typeof el === 'string') return '';
          const h = el as HTMLElement;
          const body = h.querySelector('.emr-bracket-fallback__body');
          if (body instanceof HTMLElement) {
            return h.dataset.value ?? body.textContent?.trim() ?? '';
          }
          return h.dataset.value ?? h.textContent?.trim() ?? '';
        },
        renderHTML: (attrs) => ({
          'data-value': String(attrs.value ?? ''),
        }),
      },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-emr-free-text]',
        getAttrs: (el) => {
          if (!(el instanceof HTMLElement)) return false;
          const body = el.querySelector('.emr-bracket-fallback__body');
          const label = el.dataset.label ?? el.dataset.placeholder ?? '填写';
          const valueParsed =
            el.dataset.value ??
            (body instanceof HTMLElement
              ? (body.textContent?.trim() ?? '')
              : (el.textContent?.trim() ?? ''));
          return {
            label,
            placeholder: el.dataset.placeholder ?? el.dataset.label ?? label,
            value: valueParsed,
          };
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes, node }) {
    const v = String(node.attrs.value ?? '');
    const label = String(node.attrs.label ?? node.attrs.placeholder ?? '填写');
    const ph = String(node.attrs.placeholder ?? node.attrs.label ?? label);
    return [
      'span',
      mergeAttributes(HTMLAttributes, {
        'data-emr-free-text': '1',
        'data-label': label,
        'data-placeholder': ph,
        'data-value': v,
        class: 'emr-free-text emr-free-text--fallback',
        title: '自由文本',
      }),
      ['span', { class: 'emr-bracket-fallback__bracket', 'aria-hidden': 'true' }, '['],
      [
        'span',
        {
          class: 'emr-bracket-fallback__body',
          'data-placeholder': label,
        },
        v,
      ],
      ['span', { class: 'emr-bracket-fallback__bracket', 'aria-hidden': 'true' }, ']'],
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrFreeTextView);
  },

  addCommands() {
    return {
      insertEmrFreeText:
        (attrs) =>
        ({ commands }) => {
          const label = attrs?.label ?? attrs?.placeholder ?? '填写';
          const placeholder = attrs?.placeholder ?? attrs?.label ?? label;
          return commands.insertContent({
            type: 'emrFreeText',
            attrs: {
              label,
              placeholder,
              value: attrs?.value ?? '',
            },
          });
        },
    };
  },
});
