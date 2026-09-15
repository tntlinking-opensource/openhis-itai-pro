/**
 * 病案「首页上传 / 4401」入口开关。
 *
 * 主线默认关闭：院点尚未开通 4401 或未开展首页上传业务时不展示入口。
 * 「一键上报」含 4401 前置，关闭时一并隐藏，主按钮改为「结算上传」(4101A)。
 *
 * 开通后改为 true，或院点在
 * projects/project-H{定点编码}/overrides/views/mrms/homepage/homepage-4401-upload.config.ts
 * override 为 true。
 *
 * 注意：须以 `#/views/mrms/homepage/homepage-4401-upload.config` 导入，相对路径不走院点 override。
 */
export const MRMS_HOMEPAGE_4401_UPLOAD_ENABLED = false;
