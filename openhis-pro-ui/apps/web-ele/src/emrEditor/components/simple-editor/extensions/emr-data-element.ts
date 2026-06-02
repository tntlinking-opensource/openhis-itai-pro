import { mergeAttributes, Node } from '@tiptap/core';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import { getDataElementDef } from '../data-element-registry';
import { isEmrDataElementWidget } from '../data-element-types';
import EmrDataElementView from '../node-views/EmrDataElementView.vue';

function resolveDataElementRenderWidget(node: { attrs: Record<string, unknown> }): string {
  const o = String(node.attrs.widgetOverride ?? '').trim();
  if (o && isEmrDataElementWidget(o)) return o;
  const id = String(node.attrs.elementId ?? '');
  if (id) return getDataElementDef(id)?.widget ?? 'text';
  return 'text';
}

export interface InsertEmrDataElementAttrs {
  editable?: boolean;
  elementId?: string;
  labelSnapshot?: string;
  optionsJson?: string;
  placeholder?: string;
  value?: string;
  widgetOverride?: string;
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    emrDataElement: {
      insertEmrDataElement: (attrs?: InsertEmrDataElementAttrs) => ReturnType;
    };
  }
}

export const EmrDataElement = Node.create({
  name: 'emrDataElement',

  group: 'inline',
  inline: true,
  atom: true,
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      elementId: {
        default: '',
        parseHTML: (el) => {
          if (typeof el === 'string') return '';
          return (el as HTMLElement).dataset.elementId ?? '';
        },
        renderHTML: (attrs) => ({
          'data-element-id': String(attrs.elementId ?? ''),
        }),
      },
      labelSnapshot: {
        default: '',
        parseHTML: (el) => {
          if (typeof el === 'string') return '';
          return (el as HTMLElement).dataset.labelSnapshot ?? '';
        },
        renderHTML: (attrs) => ({
          'data-label-snapshot': String(attrs.labelSnapshot ?? ''),
        }),
      },
      value: {
        default: '',
        parseHTML: (el) => {
          if (typeof el === 'string') return '';
          return (el as HTMLElement).dataset.value ?? '';
        },
        renderHTML: (attrs) => ({
          'data-value': String(attrs.value ?? ''),
        }),
      },
      placeholder: {
        default: '',
        parseHTML: (el) => {
          if (typeof el === 'string') return '';
          return (el as HTMLElement).dataset.placeholder ?? '';
        },
        renderHTML: (attrs) => ({
          'data-placeholder': String(attrs.placeholder ?? ''),
        }),
      },
      widgetOverride: {
        default: '',
        parseHTML: (el) => {
          if (typeof el === 'string') return '';
          return (el as HTMLElement).dataset.widgetOverride ?? '';
        },
        renderHTML: (attrs) => ({
          'data-widget-override': String(attrs.widgetOverride ?? ''),
        }),
      },
      optionsJson: {
        default: '[]',
        parseHTML: (el) => {
          if (typeof el === 'string') return '[]';
          return (el as HTMLElement).dataset.optionsJson ?? '[]';
        },
        renderHTML: (attrs) => ({
          'data-options-json': String(attrs.optionsJson ?? '[]'),
        }),
      },
      editable: {
        default: true,
        parseHTML: (element) => {
          if (typeof element === 'string') return true;
          return (element as HTMLElement).dataset.editable !== '0';
        },
        renderHTML: (attrs) => ({
          'data-editable': attrs.editable === false ? '0' : '1',
        }),
      },
    };
  },

  parseHTML() {
    return [
      {
        tag: 'span[data-emr-data-element]',
        getAttrs: (el) => {
          if (!(el instanceof HTMLElement)) return false;
          return {
            editable: el.dataset.editable !== '0',
            elementId: el.dataset.elementId ?? '',
            labelSnapshot: el.dataset.labelSnapshot ?? '',
            optionsJson: el.dataset.optionsJson ?? '[]',
            placeholder: el.dataset.placeholder ?? '',
            value: (() => {
              const body = el.querySelector('.emr-bracket-fallback__body');
              if (body instanceof HTMLElement) {
                return el.dataset.value ?? body.textContent?.trim() ?? '';
              }
              return el.dataset.value ?? el.textContent?.trim() ?? '';
            })(),
            widgetOverride: el.dataset.widgetOverride ?? '',
          };
        },
      },
    ];
  },

  renderHTML({ HTMLAttributes, node }) {
    const v = String(node.attrs.value ?? '');
    const attrs = mergeAttributes(HTMLAttributes, {
      'data-emr-data-element': '1',
      'data-editable': node.attrs.editable === false ? '0' : '1',
      'data-element-id': node.attrs.elementId,
      'data-label-snapshot': node.attrs.labelSnapshot,
      'data-options-json': String(node.attrs.optionsJson ?? '[]'),
      'data-placeholder': node.attrs.placeholder,
      'data-value': v,
      'data-widget-override': node.attrs.widgetOverride,
      class: 'emr-de emr-de--fallback',
      title: node.attrs.elementId || '数据元',
    });
    if (resolveDataElementRenderWidget(node) === 'text') {
      const hint =
        String(node.attrs.placeholder ?? '') || String(node.attrs.labelSnapshot ?? '') || '填写';
      return [
        'span',
        attrs,
        ['span', { class: 'emr-bracket-fallback__bracket', 'aria-hidden': 'true' }, '['],
        ['span', { class: 'emr-bracket-fallback__body', 'data-placeholder': hint }, v],
        ['span', { class: 'emr-bracket-fallback__bracket', 'aria-hidden': 'true' }, ']'],
      ];
    }
    return ['span', attrs, v];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrDataElementView);
  },

  addCommands() {
    return {
      insertEmrDataElement:
        (attrs) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: {
              editable: attrs?.editable !== false,
              elementId: attrs?.elementId ?? '',
              labelSnapshot: attrs?.labelSnapshot ?? '',
              optionsJson: attrs?.optionsJson ?? '[]',
              placeholder: attrs?.placeholder ?? '',
              value: attrs?.value ?? '',
              widgetOverride: attrs?.widgetOverride ?? '',
            },
          }),
    };
  },
});
