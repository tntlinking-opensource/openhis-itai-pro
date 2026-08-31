import { mergeAttributes, Node } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import { ReplaceStep } from '@tiptap/pm/transform';
import { VueNodeViewRenderer } from '@tiptap/vue-3';

import { isTextLikeEmrDataElementWidget } from '../data-element-types';
import { isWholeFillableNodeDelete, resolveDataElementWidget } from '../emr-data-element-text';
import { buildFillableNodeInsertJson } from '../emr-fillable-json';
import { fillableNodeExtraClass } from '../node-views/fillable-field-view.shared';
import { fillableCaretAfterInsertCommand } from '../../../fillable-field/runtime/caret-policy';
import EmrDataElementView from '../node-views/EmrDataElementView.vue';

function resolveDataElementRenderWidget(node: { attrs: Record<string, unknown> }): string {
  return resolveDataElementWidget(node.attrs);
}

export interface InsertEmrDataElementAttrs {
  editable?: boolean;
  elementId?: string;
  labelSnapshot?: string;
  optionsJson?: string;
  placeholder?: string;
  required?: boolean;
  requiredMessage?: string;
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

const readonlyGuardKey = new PluginKey('emrDataElementReadonlyGuard');

function extraClassForDataElement(node: { attrs: Record<string, unknown> }): string {
  const widget = resolveDataElementRenderWidget(node);
  const bound = String(node.attrs.elementId ?? '') ? ' emr-de--bound' : '';
  const readonly = node.attrs.editable === false ? ' emr-de--readonly' : '';
  const multiline = widget === 'multiline' ? ' emr-de--multiline' : '';
  return `emr-de emr-de--pm emr-bracket-field emr-de--${widget}${bound}${readonly}${multiline}`;
}

export const EmrDataElement = Node.create({
  name: 'emrDataElement',

  group: 'inline',
  inline: true,
  atom: true,
  content: '',
  selectable: true,
  draggable: false,

  addAttributes() {
    return {
      slotId: { default: '' },
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
      required: {
        default: false,
        parseHTML: (el) => {
          if (typeof el === 'string') return false;
          return (el as HTMLElement).dataset.required === '1';
        },
        renderHTML: (attrs) => ({
          'data-required': attrs.required === true ? '1' : '0',
        }),
      },
      requiredMessage: {
        default: '',
        parseHTML: (el) => {
          if (typeof el === 'string') return '';
          return (el as HTMLElement).dataset.requiredMessage ?? '';
        },
        renderHTML: (attrs) => ({
          'data-required-message': String(attrs.requiredMessage ?? ''),
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
          const widget = el.dataset.widgetOverride ?? '';
          const isWidget =
            widget === 'select' || widget === 'checkbox' || widget === 'radio' || widget === 'date';
          if (!isWidget && widget !== '' && !isTextLikeEmrDataElementWidget(widget)) {
            return false;
          }
          return {
            editable: el.dataset.editable !== '0',
            elementId: el.dataset.elementId ?? '',
            labelSnapshot: el.dataset.labelSnapshot ?? '',
            optionsJson: el.dataset.optionsJson ?? '[]',
            placeholder: el.dataset.placeholder ?? '',
            required: el.dataset.required === '1',
            requiredMessage: el.dataset.requiredMessage ?? '',
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
    const widget = resolveDataElementRenderWidget(node);
    const attrs = mergeAttributes(HTMLAttributes, {
      'data-emr-data-element': '1',
      'data-editable': node.attrs.editable === false ? '0' : '1',
      'data-element-id': node.attrs.elementId,
      'data-label-snapshot': node.attrs.labelSnapshot,
      'data-options-json': String(node.attrs.optionsJson ?? '[]'),
      'data-placeholder': node.attrs.placeholder,
      'data-required': node.attrs.required === true ? '1' : '0',
      'data-required-message': String(node.attrs.requiredMessage ?? ''),
      'data-widget-override': node.attrs.widgetOverride,
      class: extraClassForDataElement(node),
      title: node.attrs.elementId || '数据元',
    });
    if (!isTextLikeEmrDataElementWidget(widget)) {
      return ['span', attrs, String(node.attrs.value ?? '')];
    }
    const placeholder = String(node.attrs.placeholder ?? node.attrs.labelSnapshot ?? '填写');
    return [
      'span',
      mergeAttributes(attrs, {
        'data-placeholder': placeholder,
        'data-value': String(node.attrs.value ?? ''),
        class: `${fillableNodeExtraClass(node, 'dataElement')} emr-de emr-de--${widget}`,
      }),
      String(node.attrs.value ?? ''),
    ];
  },

  addNodeView() {
    return VueNodeViewRenderer(EmrDataElementView);
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
                if (n.type.name === 'emrDataElement' && n.attrs.editable === false) {
                  return false;
                }
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
      insertEmrDataElement:
        (attrs) =>
        ({ chain, commands }) => {
          const value = attrs?.value ?? '';
          const nextAttrs = {
            editable: attrs?.editable !== false,
            elementId: attrs?.elementId ?? '',
            labelSnapshot: attrs?.labelSnapshot ?? '',
            optionsJson: attrs?.optionsJson ?? '[]',
            placeholder: attrs?.placeholder ?? '',
            required: attrs?.required === true,
            requiredMessage: attrs?.requiredMessage ?? '',
            value,
            widgetOverride: attrs?.widgetOverride ?? '',
          };
          const widget = resolveDataElementWidget(nextAttrs);
          if (isTextLikeEmrDataElementWidget(widget)) {
            return chain()
              .insertContent(
                buildFillableNodeInsertJson({
                  kind: 'dataElement',
                  editable: nextAttrs.editable,
                  elementId: nextAttrs.elementId,
                  label: nextAttrs.labelSnapshot || nextAttrs.placeholder,
                  labelSnapshot: nextAttrs.labelSnapshot,
                  optionsJson: nextAttrs.optionsJson,
                  placeholder: nextAttrs.placeholder || nextAttrs.labelSnapshot,
                  required: nextAttrs.required,
                  requiredMessage: nextAttrs.requiredMessage,
                  value,
                  widgetOverride: nextAttrs.widgetOverride,
                }),
              )
              .command(fillableCaretAfterInsertCommand('drop'))
              .run();
          }
          return commands.insertContent({
            type: this.name,
            attrs: nextAttrs,
          });
        },
    };
  },
});
