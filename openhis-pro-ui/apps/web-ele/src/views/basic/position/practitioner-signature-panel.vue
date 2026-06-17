<script lang="ts" setup>
import type { UploadProps } from 'element-plus';

import { ref, watch } from 'vue';

import { ElButton, ElMessage, ElUpload } from 'element-plus';

import SectionTitle from '#/components/layouts/section-title.vue';
import { getPractitionerProfileApi, savePractitionerSignatureApi } from '#/api/basic/practitioner';

defineOptions({
  name: 'PractitionerSignaturePanel',
});

const props = defineProps<{
  practitionerId?: string;
  practitionerName?: string;
  busNo?: string;
}>();

const loading = ref(false);
const saving = ref(false);
const signaturePreview = ref('');

const MAX_FILE_BYTES = 200 * 1024;

/** 加载当前医务人员签名图 */
async function loadProfile() {
  const pid = props.practitionerId?.trim();
  if (!pid) {
    signaturePreview.value = '';
    return;
  }
  loading.value = true;
  try {
    const res = await getPractitionerProfileApi(pid);
    const data = res.data;
    signaturePreview.value = data?.signatureImage?.trim() ?? '';
  } catch {
    signaturePreview.value = '';
  } finally {
    loading.value = false;
  }
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(new Error('读取图片失败'));
    reader.readAsDataURL(file);
  });
}

/** 上传前校验格式与大小，预览不落库 */
const beforeUpload: UploadProps['beforeUpload'] = async (rawFile) => {
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.warning('请上传 PNG、JPG 等图片格式');
    return false;
  }
  if (rawFile.size > MAX_FILE_BYTES) {
    ElMessage.warning('签名图片请小于 200KB');
    return false;
  }
  try {
    signaturePreview.value = await fileToDataUrl(rawFile);
  } catch {
    ElMessage.error('读取图片失败');
  }
  return false;
};

/** 保存签名至医务人员主档 */
async function handleSave() {
  const pid = props.practitionerId?.trim();
  if (!pid) {
    ElMessage.warning('未绑定医务人员，无法保存签名');
    return;
  }
  saving.value = true;
  try {
    await savePractitionerSignatureApi({
      practitionerId: pid,
      signatureImage: signaturePreview.value.trim(),
    });
    ElMessage.success('医生签名已保存');
    await loadProfile();
  } finally {
    saving.value = false;
  }
}

/** 清除签名并同步保存 */
async function handleClear() {
  signaturePreview.value = '';
  await handleSave();
}

watch(
  () => props.practitionerId,
  () => {
    loadProfile();
  },
  { immediate: true },
);
</script>

<template>
  <section v-loading="loading" class="practitioner-signature-panel" aria-label="医务人员签名维护">
    <SectionTitle title="医生签名" />

    <div v-if="!practitionerId" class="practitioner-signature-panel__tip">
      该用户尚未建立医务人员主档，请先在用户/主数据环节完成绑定后再维护签名。
    </div>

    <template v-else>
      <div class="practitioner-signature-panel__content">
        <div class="practitioner-signature-panel__preview">
          <img
            v-if="signaturePreview"
            :src="signaturePreview"
            alt="签名预览"
            class="practitioner-signature-panel__image"
          />
          <span v-else class="practitioner-signature-panel__placeholder">暂无签名</span>
        </div>

        <div class="practitioner-signature-panel__actions">
          <ElUpload
            :before-upload="beforeUpload"
            :show-file-list="false"
            accept="image/png,image/jpeg,image/webp"
          >
            <ElButton plain type="primary">上传签名图</ElButton>
          </ElUpload>
          <div class="practitioner-signature-panel__btn-row">
            <ElButton :loading="saving" type="primary" @click="handleSave">保存</ElButton>
            <ElButton :disabled="!signaturePreview" @click="handleClear">清除</ElButton>
          </div>
          <p class="practitioner-signature-panel__hint">
            建议透明底 PNG，宽 200～400px，小于 200KB
          </p>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.practitioner-signature-panel {
  flex-shrink: 0;
  padding: 12px 16px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.practitioner-signature-panel__tip {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.practitioner-signature-panel__content {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-start;
  margin-top: 12px;
}

.practitioner-signature-panel__preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 180px;
  height: 80px;
  background: var(--el-fill-color-blank);
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
}

.practitioner-signature-panel__image {
  max-width: 160px;
  max-height: 64px;
  object-fit: contain;
}

.practitioner-signature-panel__placeholder {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.practitioner-signature-panel__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.practitioner-signature-panel__btn-row {
  display: flex;
  gap: 8px;
}

.practitioner-signature-panel__hint {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
