<script lang="ts">
import type { ComputedRef, VNode } from 'vue';

import type { EmrFixedTextMarkJSON, EmrFixedTextSegmentJSON } from '../emr-fixed-text-types';

import { computed, defineComponent, h, inject } from 'vue';

import { nodeViewProps, NodeViewWrapper } from '@tiptap/vue-3';

export default defineComponent({
  name: 'EmrFixedTextView',
  props: nodeViewProps,
  setup(props) {
    const surfaceInject = inject<ComputedRef<'document' | 'template'>>('emrEditorSurface');
    const surface = computed(() => surfaceInject?.value ?? 'document');

    const segments = computed((): EmrFixedTextSegmentJSON[] => {
      const raw = props.node.attrs.segments as EmrFixedTextSegmentJSON[] | null | undefined;
      if (Array.isArray(raw) && raw.length > 0) return raw;
      const t = String(props.node.attrs.text ?? '');
      return t ? [{ text: t, marks: [] }] : [];
    });

    function textStyleToCss(attrs: Record<string, unknown> | undefined): Record<string, string> {
      if (!attrs) return {};
      const style: Record<string, string> = {};
      if (attrs.color) style.color = String(attrs.color);
      if (attrs.fontFamily) style.fontFamily = String(attrs.fontFamily);
      if (attrs.fontSize) style.fontSize = String(attrs.fontSize);
      if (attrs.lineHeight) style.lineHeight = String(attrs.lineHeight);
      return style;
    }

    function markToWrapper(mark: EmrFixedTextMarkJSON, child: string | VNode): VNode {
      const attrs = mark.attrs ?? {};

      switch (mark.type) {
        case 'bold': {
          return h('strong', child);
        }
        case 'code': {
          return h('code', child);
        }
        case 'highlight': {
          const c =
            attrs.color === undefined || attrs.color === null ? '#fef08a' : String(attrs.color);
          return h('mark', { style: { backgroundColor: c } }, child);
        }
        case 'italic': {
          return h('em', child);
        }
        case 'link': {
          return h(
            'a',
            {
              href: String(attrs.href ?? '#'),
              rel: 'noopener noreferrer',
              target: '_blank',
            },
            child,
          );
        }
        case 'strike': {
          return h('s', child);
        }
        case 'subscript': {
          return h('sub', child);
        }
        case 'superscript': {
          return h('sup', child);
        }
        case 'textStyle': {
          const st = textStyleToCss(attrs as Record<string, unknown>);
          return h('span', { style: st }, child);
        }
        case 'underline': {
          return h('u', child);
        }
        default: {
          return h(
            'span',
            { class: 'emr-fixed-text__unknown-mark', 'data-mark': mark.type },
            child,
          );
        }
      }
    }

    function segmentToVNode(seg: EmrFixedTextSegmentJSON): VNode {
      let n: string | VNode = seg.text;
      for (const m of seg.marks) {
        n = markToWrapper(m, n);
      }
      return typeof n === 'string' ? h('span', n) : n;
    }

    return () =>
      h(
        NodeViewWrapper,
        {
          as: 'span',
          class: {
            'emr-fixed-text': true,
            'emr-fixed-text--template-bg': surface.value === 'template',
          },
          contenteditable: false,
          spellcheck: false,
          'data-node-view-wrapper': '',
        },
        {
          default: () => segments.value.map((s) => segmentToVNode(s)),
        },
      );
  },
});
</script>
