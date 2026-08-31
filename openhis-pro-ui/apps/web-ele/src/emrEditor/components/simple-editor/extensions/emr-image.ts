/**
 * EMR 正文图片：TipTap Image + 拖拽缩放。
 * 仅支持行内嵌入（不做 Word 式文字环绕）；节点名仍为 `image`，兼容旧模板。
 */

import { mergeAttributes, ResizableNodeView } from '@tiptap/core';
import Image from '@tiptap/extension-image';
import { NodeSelection, Plugin, PluginKey } from '@tiptap/pm/state';

const emrImageClickKey = new PluginKey('emrImageClick');

/** 统一行内壳：清掉历史 float/block 样式，避免旧 wrap 把图「漂没」 */
function applyInlineShell(container: HTMLElement, title: unknown) {
  container.classList.remove(
    'emr-img--inline',
    'emr-img--block',
    'emr-img--float-left',
    'emr-img--float-right',
    'emr-img--logo',
  );
  container.style.float = 'none';
  container.style.margin = '';
  container.style.clear = '';
  container.style.justifyContent = '';
  container.style.display = 'inline-flex';
  if (String(title ?? '') === 'hospitalLogo') {
    container.classList.add('emr-img--inline', 'emr-img--logo');
    return;
  }
  container.classList.add('emr-img--inline');
}

export const EmrImage = Image.extend({
  name: 'image',

  selectable: true,
  draggable: true,

  addOptions() {
    return {
      ...this.parent?.(),
      inline: true,
      allowBase64: true,
      HTMLAttributes: {
        class: 'emr-editor-img',
      },
      resize: {
        enabled: true,
        directions: ['top-left', 'top-right', 'bottom-left', 'bottom-right'],
        minWidth: 24,
        minHeight: 24,
        alwaysPreserveAspectRatio: true,
      },
    };
  },

  addAttributes() {
    return {
      ...this.parent?.(),
      // 旧稿可能仍带 wrap；解析后一律当行内，不再写出 data-emr-wrap
      wrap: {
        default: 'inline',
        parseHTML: () => 'inline',
        renderHTML: () => ({}),
      },
    };
  },

  renderHTML({ HTMLAttributes }) {
    return [
      'img',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, {
        class: 'emr-editor-img',
      }),
    ];
  },

  /** 点击图片节点时尽量做成 NodeSelection */
  addProseMirrorPlugins() {
    return [
      new Plugin({
        key: emrImageClickKey,
        props: {
          handleClickOn(view, _pos, node, nodePos, _event, _direct) {
            if (node.type.name !== 'image') return false;
            if (!view.editable) return false;
            view.dispatch(
              view.state.tr.setSelection(NodeSelection.create(view.state.doc, nodePos)),
            );
            return true;
          },
        },
      }),
    ];
  },

  addNodeView() {
    const resize = this.options.resize;
    if (!resize || !resize.enabled || typeof document === 'undefined') {
      return null;
    }
    const { directions, minWidth, minHeight, alwaysPreserveAspectRatio } = resize;

    return ({ node, getPos, HTMLAttributes, editor }) => {
      const el = document.createElement('img');
      el.className = 'emr-editor-img';
      Object.entries(HTMLAttributes).forEach(([key, value]) => {
        if (value == null) return;
        if (key === 'width' || key === 'height' || key === 'class') return;
        el.setAttribute(key, String(value));
      });
      el.src = String(HTMLAttributes.src ?? node.attrs.src ?? '');

      let containerEl: HTMLElement | null = null;

      const nodeView = new ResizableNodeView({
        element: el,
        editor,
        node,
        getPos,
        onResize: (width, height) => {
          el.style.width = `${width}px`;
          el.style.height = `${height}px`;
        },
        onCommit: (width, height) => {
          const pos = getPos();
          if (pos === undefined) return;
          this.editor
            .chain()
            .setNodeSelection(pos)
            .updateAttributes(this.name, { width, height, wrap: 'inline' })
            .run();
        },
        onUpdate: (updatedNode) => {
          if (updatedNode.type !== node.type) return false;
          const src = String(updatedNode.attrs.src ?? '');
          if (src && el.getAttribute('src') !== src) {
            el.src = src;
          }
          const alt = updatedNode.attrs.alt;
          if (alt == null) el.removeAttribute('alt');
          else el.setAttribute('alt', String(alt));
          const title = updatedNode.attrs.title;
          if (title == null) el.removeAttribute('title');
          else el.setAttribute('title', String(title));
          const w = updatedNode.attrs.width;
          const h = updatedNode.attrs.height;
          if (w != null) {
            el.style.width = `${w}px`;
            el.setAttribute('width', String(w));
          }
          if (h != null) {
            el.style.height = `${h}px`;
            el.setAttribute('height', String(h));
          }
          if (containerEl) {
            applyInlineShell(containerEl, updatedNode.attrs.title);
          }
          return true;
        },
        options: {
          directions,
          min: {
            width: minWidth ?? 24,
            height: minHeight ?? 24,
          },
          preserveAspectRatio: alwaysPreserveAspectRatio === true,
          className: {
            container: 'emr-img-resize',
            wrapper: 'emr-img-resize__wrap',
            handle: 'emr-img-resize__handle',
            resizing: 'emr-img-resize--active',
          },
        },
      });

      containerEl = nodeView.dom as HTMLElement;
      applyInlineShell(containerEl, node.attrs.title);

      /** 点图时选中节点；不 stopPropagation，避免掐断 PM / 工具条 */
      const onPick = (event: MouseEvent) => {
        if (event.button !== 0) return;
        const t = event.target;
        if (t instanceof Element && t.closest('[data-resize-handle], .emr-img-resize__handle')) {
          return;
        }
        const pos = getPos();
        if (typeof pos !== 'number' || editor.isDestroyed) return;
        requestAnimationFrame(() => {
          if (editor.isDestroyed) return;
          try {
            editor.view.dispatch(
              editor.state.tr.setSelection(NodeSelection.create(editor.state.doc, pos)),
            );
          } catch {
            /* ignore */
          }
        });
      };
      containerEl.addEventListener('mousedown', onPick);

      if (!el.complete) {
        containerEl.style.opacity = '0';
        const reveal = () => {
          if (containerEl) containerEl.style.opacity = '';
        };
        el.addEventListener('load', reveal, { once: true });
        el.addEventListener('error', reveal, { once: true });
      }

      const prevDestroy = nodeView.destroy?.bind(nodeView);
      nodeView.destroy = () => {
        containerEl?.removeEventListener('mousedown', onPick);
        prevDestroy?.();
      };

      return nodeView;
    };
  },
});
