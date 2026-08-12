<template>
  <div class="document-context">
    <CaseContextBar v-model="caseId" @change="loadFiles" />
    <div class="document-controls">
      <el-select v-model="dossierId" filterable placeholder="选择卷宗文件" :loading="loading" @change="loadHistory">
        <el-option v-for="file in files" :key="file.dossierId" :value="file.dossierId" :label="file.fileName" />
      </el-select>
      <el-select v-model="docId" placeholder="选择解析版本" :disabled="!history.length" @change="selectDocument">
        <el-option v-for="item in history" :key="item.docId" :value="item.docId" :label="`V${item.versionNo} · ${statusLabel(item.parseStatus)}`" :disabled="item.parseStatus !== 'SUCCESS'" />
      </el-select>
      <el-upload v-if="canManage" :show-file-list="false" :auto-upload="false" :on-change="upload">
        <el-button :icon="Upload">上传 TXT</el-button>
      </el-upload>
      <el-button v-if="canExecute" type="primary" plain :icon="Cpu" :disabled="!dossierId" :loading="parsing" @click="parse">解析文书</el-button>
    </div>
    <el-alert v-if="notice" :title="notice" :type="noticeType" show-icon :closable="false" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Cpu, Upload } from '@element-plus/icons-vue'
import CaseContextBar from './CaseContextBar.vue'
import { hasPermission } from '../../auth/session'
import { getParsedDocument, listDossierFiles, listParseResults, startDocumentParse, uploadDossierFile } from '../../api/dossier'

const emit = defineEmits(['document', 'case'])
const caseId = ref(null)
const dossierId = ref(null)
const docId = ref(null)
const files = ref([])
const history = ref([])
const loading = ref(false)
const parsing = ref(false)
const notice = ref('')
const noticeType = ref('info')
const canManage = hasPermission('DOSSIER_MANAGE')
const canExecute = hasPermission('AI_EXECUTE')

async function loadFiles() {
  dossierId.value = null
  docId.value = null
  history.value = []
  emit('document', null)
  emit('case', caseId.value)
  if (!caseId.value) return
  loading.value = true
  try {
    files.value = await listDossierFiles(caseId.value)
    if (files.value.length) {
      dossierId.value = files.value[0].dossierId
      await loadHistory()
    } else notice.value = '当前案件暂无卷宗文件，可上传一个 UTF-8 TXT 文件进行演示。'
  } catch (error) { noticeError(error) }
  finally { loading.value = false }
}

async function upload(file) {
  if (!caseId.value) return ElMessage.warning('请先选择案件')
  try {
    const created = await uploadDossierFile(caseId.value, file.raw)
    ElMessage.success('文件已上传')
    files.value = await listDossierFiles(caseId.value)
    dossierId.value = created.dossierId
    await loadHistory()
  } catch (error) { noticeError(error) }
}

async function parse() {
  parsing.value = true
  try {
    const result = await startDocumentParse(caseId.value, dossierId.value)
    notice.value = `解析任务已受理，版本 V${result.versionNo}，可稍后刷新解析版本。`
    noticeType.value = 'success'
    await waitForParse(result.docId)
    await loadHistory()
  } catch (error) { noticeError(error) }
  finally { parsing.value = false }
}

async function waitForParse(targetDocId) {
  for (let attempt = 0; attempt < 12; attempt += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 500))
    const detail = await getParsedDocument(caseId.value, targetDocId)
    if (detail.parseStatus === 'SUCCESS') return
    if (detail.parseStatus === 'FAILED') throw new Error(detail.errorCode || '文书解析失败')
  }
}

async function loadHistory() {
  docId.value = null
  emit('document', null)
  if (!dossierId.value) return
  try {
    history.value = await listParseResults(caseId.value, dossierId.value)
    const current = history.value.find((item) => item.current && item.parseStatus === 'SUCCESS') || history.value.find((item) => item.parseStatus === 'SUCCESS')
    if (current) { docId.value = current.docId; await selectDocument() }
    else if (history.value.length) {
      const latest = history.value[0]
      notice.value = latest.parseStatus === 'FAILED' ? `最近一次解析失败：${latest.errorCode || '未知原因'}` : '文书仍在解析中。'
      noticeType.value = latest.parseStatus === 'FAILED' ? 'error' : 'info'
    }
  } catch (error) { noticeError(error) }
}

async function selectDocument() {
  if (!docId.value) return emit('document', null)
  try { emit('document', await getParsedDocument(caseId.value, docId.value)) }
  catch (error) { noticeError(error) }
}

function statusLabel(status) { return ({ SUCCESS: '解析成功', PROCESSING: '解析中', FAILED: '解析失败' })[status] || status }
function noticeError(error) { notice.value = error.message; noticeType.value = 'error'; ElMessage.error(error.message) }
</script>

<style scoped>
.document-context { display: grid; gap: 12px; }
.document-controls { display: grid; grid-template-columns: minmax(220px, 1fr) 190px auto auto; gap: 10px; align-items: center; }
@media (max-width: 900px) { .document-controls { grid-template-columns: 1fr 1fr; } }
</style>
