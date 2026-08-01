<script setup lang="ts">
import { onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';

import { getHomeNoticeApi, saveHomeNoticeApi } from '#/api/peis/announcement';
import { useRequest } from '#/hooks/use-request';
import { ElButton, ElCard, ElMessage } from 'element-plus';

import HtmlRichEditor from './HtmlRichEditor.vue';

defineOptions({ name: 'PeisAnnouncement' });

const html = ref('<p></p>');
const updatedAt = ref('');

const { run: runLoad, loading: loadingLoad } = useRequest(getHomeNoticeApi, {
  manual: true,
  onSuccess: (res) => {
    html.value = res?.contentHtml || '<p></p>';
    updatedAt.value = res?.updatedAt || '';
  },
});

const { run: runSave, loading: loadingSave } = useRequest(saveHomeNoticeApi, {
  manual: true,
  onSuccess: (res) => {
    html.value = res?.contentHtml || html.value;
    updatedAt.value = res?.updatedAt || '';
    ElMessage.success('公告已保存，小程序首页将展示最新内容');
  },
});

async function load() {
  await runLoad();
}

async function save() {
  const content = (html.value || '').trim();
  if (!content || content === '<p></p>') {
    ElMessage.warning('请先填写公告内容');
    return;
  }
  await runSave({ title: '院内公告', contentHtml: html.value });
}

onMounted(() => {
  void load();
});
</script>

<template>
  <Page auto-content-height title="小程序首页公告">
    <ElCard shadow="never">
      <template #header>
        <div class="head">
          <div>
            <div class="title">所见即所得编辑</div>
            <div class="hint">
              写入配置键
              <code>peis.miniapp.home_notice</code>
              ；患者端「首页」公告模块读取同一内容。
              <span v-if="updatedAt">最近保存：{{ updatedAt }}</span>
            </div>
          </div>
          <div class="actions">
            <ElButton :loading="loadingLoad" @click="load">重新加载</ElButton>
            <ElButton type="primary" :loading="loadingSave" @click="save"> 保存发布 </ElButton>
          </div>
        </div>
      </template>
      <HtmlRichEditor v-model="html" placeholder="编辑将展示在小程序首页的公告…" />
      <div class="preview-label">预览</div>
      <div class="preview" v-html="html" />
    </ElCard>
  </Page>
</template>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.hint code {
  padding: 0 4px;
  border-radius: 4px;
  background: var(--el-fill-color);
}
.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.preview-label {
  margin: 20px 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
}
.preview {
  min-height: 80px;
  padding: 12px 14px;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
  line-height: 1.7;
  font-size: 14px;
}
</style>
