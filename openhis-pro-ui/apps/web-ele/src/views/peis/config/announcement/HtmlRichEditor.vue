<script setup lang="ts">
/**
 * 轻量 HTML 富文本（TipTap），用于小程序公告等非病历场景。
 * 与 EMR SimpleEditor 分离，避免病历扩展耦合。
 */
import { onBeforeUnmount, watch } from 'vue';

import Underline from '@tiptap/extension-underline';
import StarterKit from '@tiptap/starter-kit';
import { EditorContent, useEditor } from '@tiptap/vue-3';
import { ElButton } from 'element-plus';

const props = withDefaults(
  defineProps<{
    modelValue?: string;
    placeholder?: string;
  }>(),
  {
    modelValue: '',
    placeholder: '请输入公告内容…',
  },
);

const emit = defineEmits<{
  'update:modelValue': [value: string];
}>();

const editor = useEditor({
  content: props.modelValue || '',
  extensions: [
    StarterKit.configure({
      heading: { levels: [2, 3] },
    }),
    Underline,
  ],
  editorProps: {
    attributes: {
      class: 'html-rich-editor__content',
      'data-placeholder': props.placeholder,
    },
  },
  onUpdate: ({ editor: ed }) => {
    emit('update:modelValue', ed.getHTML());
  },
});

watch(
  () => props.modelValue,
  (html) => {
    const ed = editor.value;
    if (!ed) {
      return;
    }
    const current = ed.getHTML();
    if ((html || '') !== current) {
      ed.commands.setContent(html || '', { emitUpdate: false });
    }
  },
);

onBeforeUnmount(() => {
  editor.value?.destroy();
});

function run(cmd: () => boolean) {
  cmd();
}
</script>

<template>
  <div class="html-rich-editor">
    <div v-if="editor" class="html-rich-editor__toolbar">
      <ElButton
        size="small"
        :type="editor.isActive('bold') ? 'primary' : 'default'"
        @click="run(() => editor!.chain().focus().toggleBold().run())"
      >
        粗体
      </ElButton>
      <ElButton
        size="small"
        :type="editor.isActive('italic') ? 'primary' : 'default'"
        @click="run(() => editor!.chain().focus().toggleItalic().run())"
      >
        斜体
      </ElButton>
      <ElButton
        size="small"
        :type="editor.isActive('underline') ? 'primary' : 'default'"
        @click="run(() => editor!.chain().focus().toggleUnderline().run())"
      >
        下划线
      </ElButton>
      <ElButton
        size="small"
        :type="editor.isActive('bulletList') ? 'primary' : 'default'"
        @click="run(() => editor!.chain().focus().toggleBulletList().run())"
      >
        列表
      </ElButton>
      <ElButton
        size="small"
        :type="editor.isActive('heading', { level: 2 }) ? 'primary' : 'default'"
        @click="run(() => editor!.chain().focus().toggleHeading({ level: 2 }).run())"
      >
        标题
      </ElButton>
    </div>
    <EditorContent :editor="editor" class="html-rich-editor__body" />
  </div>
</template>

<style scoped>
.html-rich-editor {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.html-rich-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}
.html-rich-editor__body {
  min-height: 280px;
  padding: 12px 14px;
}
.html-rich-editor__body :deep(.html-rich-editor__content) {
  outline: none;
  min-height: 240px;
  line-height: 1.7;
  font-size: 14px;
}
.html-rich-editor__body :deep(.html-rich-editor__content p) {
  margin: 0 0 0.6em;
}
</style>
