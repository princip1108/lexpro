<template>
  <div class="feature-layout">
    <aside class="case-panel">
      <div class="cp-head"><b>案件列表</b><select v-model="caseType" class="cp-filter"><option value="">全部类型</option><option v-for="type in caseTypes" :key="type">{{ type }}</option></select></div>
      <div class="cp-list">
        <div v-if="loadingCases" class="cp-empty">案件加载中…</div>
        <div v-else-if="!filteredCases.length" class="cp-empty">暂无案件</div>
        <button v-for="item in filteredCases" :key="item.caseId" class="cp-item" :class="{active:item.caseId===caseId}" type="button" @click="selectCase(item.caseId)">
          <div class="cp-name">{{ item.caseName }}</div><div class="cp-meta"><span class="tag">{{ item.caseType || '刑事' }}</span>{{ item.caseNo || '' }}</div><div class="cp-time">{{ (item.acceptDate || item.createdAt || '').slice(0,10) }}</div>
        </button>
      </div>
    </aside>

    <div class="feature-main">
      <div v-if="!selectedCase" class="empty">请从左侧选择案件，开始「{{ title }}」</div>
      <template v-else>
        <div class="case-head feature-head"><div><h2>{{ selectedCase.caseName }}</h2><div class="sub">{{ selectedCase.caseNo || '' }} · 受理 {{ selectedCase.acceptDate || '-' }} · 承办 {{ selectedCase.handlerName || '-' }}</div></div><div class="flex"><span class="badge" :class="statusClass">{{ statusLabel }}</span><button class="btn ghost sm" type="button" @click="$router.push({path:'/todo-cases',query:{caseId}})">查看完整详情</button></div></div>
        <div class="card"><div class="card-title"><span class="bar"></span>案件信息</div><div class="kv-grid"><div><div class="k">案件编号</div><div class="v">{{ selectedCase.caseNo || '-' }}</div></div><div><div class="k">案件类型</div><div class="v">{{ selectedCase.caseType || '-' }}</div></div><div><div class="k">案由</div><div class="v">{{ selectedCase.caseCause || '-' }}</div></div><div><div class="k">承办人</div><div class="v">{{ selectedCase.handlerName || '-' }}</div></div><div><div class="k">当前环节</div><div class="v">{{ selectedCase.currentStage || '-' }}</div></div><div><div class="k">截止时间</div><div class="v">{{ formatDate(selectedCase.deadlineAt) }}</div></div></div></div>

        <div v-if="showDossier" class="card mt16">
          <div class="card-title"><span class="bar"></span>卷宗内容预览</div>
          <div class="card-pad">
            <div class="analysis-bar">
              <select v-model="dossierId" style="max-width:280px" @change="loadHistory"><option :value="null">暂无卷宗</option><option v-for="file in files" :key="file.dossierId" :value="file.dossierId">{{ file.fileName }}</option></select>
              <select v-model="docId" style="max-width:190px" :disabled="!history.length" @change="selectDocument"><option :value="null">选择解析版本</option><option v-for="item in history" :key="item.docId" :value="item.docId" :disabled="item.parseStatus!=='SUCCESS'">V{{ item.versionNo }} · {{ parseStatusLabel(item.parseStatus) }}</option></select>
              <input ref="fileInput" hidden type="file" accept=".txt,.pdf,.doc,.docx,.jpg,.jpeg,.png" @change="upload">
              <button v-if="canManage" class="btn ghost sm" :disabled="uploading||parsing" type="button" @click="fileInput.click()">{{ uploading ? '上传中…' : '上传图片/文书' }}</button>
              <button v-if="canExecute" class="btn primary sm" :disabled="!dossierId||uploading||parsing" type="button" @click="parse">{{ parsing ? '解析中…' : 'MinerU 解析' }}</button>
              <span class="muted">支持 JPG、PNG、PDF、DOCX、TXT；旧版 DOC 请先另存为 DOCX/PDF</span>
            </div>
            <div v-if="notice" class="context-notice" :class="noticeType">{{ notice }}</div>
            <slot name="preview" :document="document"><div class="doc-preview">{{ document?.rawText || '选择卷宗后显示解析文本' }}</div></slot>
          </div>
        </div>
        <slot :case-id="caseId" :document="document" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import { hasPermission } from '../../auth/session'
import { listCases } from '../../api/cases'
import { getParsedDocument, listDossierFiles, listParseResults, startDocumentParse, uploadDossierFile } from '../../api/dossier'

defineProps({ title: { type: String, required: true }, showDossier: { type: Boolean, default: true } })
const emit = defineEmits(['case', 'document'])
const route = useRoute()
const cases = ref([]), caseId = ref(null), caseType = ref(''), loadingCases = ref(false)
const files = ref([]), dossierId = ref(null), history = ref([]), docId = ref(null), document = ref(null)
const uploading = ref(false), parsing = ref(false), notice = ref(''), noticeType = ref('info'), fileInput = ref(null)
const canManage = hasPermission('DOSSIER_MANAGE'), canExecute = hasPermission('AI_EXECUTE')
const selectedCase = computed(() => cases.value.find((item) => item.caseId === caseId.value))
const caseTypes = computed(() => [...new Set(cases.value.map((item) => item.caseType).filter(Boolean))])
const filteredCases = computed(() => caseType.value ? cases.value.filter((item) => item.caseType === caseType.value) : cases.value)
const statusLabel = computed(() => ({PENDING:'待处理',PROCESSING:'办理中',CLOSED:'已办结',ARCHIVED:'已归档'})[selectedCase.value?.caseStatus] || selectedCase.value?.caseStatus || '-')
const statusClass = computed(() => ({PENDING:'b-gold',PROCESSING:'b-blue',CLOSED:'b-green',ARCHIVED:'b-gray'})[selectedCase.value?.caseStatus] || 'b-gray')

onMounted(loadCases)
async function loadCases() { loadingCases.value = true; try { cases.value = (await listCases({page:1,size:100})).items || []; const requested = Number(route.query.caseId); const initial = cases.value.find((item) => item.caseId === requested); if (initial) await selectCase(initial.caseId) } catch (error) { fail(error) } finally { loadingCases.value = false } }
async function selectCase(value) { caseId.value = value; files.value = []; dossierId.value = null; history.value = []; docId.value = null; setDocument(null); emit('case', value); try { files.value = await listDossierFiles(value); if (files.value.length) { dossierId.value = files.value[0].dossierId; await loadHistory() } else { notice.value = '当前案件暂无卷宗，可上传图片或文书。'; noticeType.value = 'info' } } catch (error) { fail(error) } }
async function loadHistory() { history.value = []; docId.value = null; setDocument(null); if (!dossierId.value) return; try { history.value = await listParseResults(caseId.value, dossierId.value); const current = history.value.find((item) => item.current&&item.parseStatus==='SUCCESS') || history.value.find((item) => item.parseStatus==='SUCCESS'); if (current) { docId.value = current.docId; await selectDocument() } } catch (error) { fail(error) } }
async function selectDocument() { if (!docId.value) return setDocument(null); try { setDocument(await getParsedDocument(caseId.value, docId.value)) } catch (error) { fail(error) } }
function setDocument(value) { document.value = value; emit('document', value) }
async function upload(event) { const file = event.target.files?.[0]; event.target.value = ''; if (!file) return; const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase(); if (!['.txt','.pdf','.doc','.docx','.jpg','.jpeg','.png'].includes(extension)) return ElMessage.warning('仅支持 TXT、PDF、Word、JPG 和 PNG 文件'); uploading.value = true; try { const created = await uploadDossierFile(caseId.value, file); files.value = await listDossierFiles(caseId.value); dossierId.value = created.dossierId; await loadHistory(); notice.value = '文件已上传。'; noticeType.value = 'success'; if (canExecute) await parse() } catch (error) { fail(error) } finally { uploading.value = false } }
async function parse() {
  if (!caseId.value || !dossierId.value) return
  const selectedCaseId = caseId.value, selectedDossierId = dossierId.value
  const stillSelected = () => caseId.value === selectedCaseId && dossierId.value === selectedDossierId
  parsing.value = true
  try {
    const started = await startDocumentParse(selectedCaseId, selectedDossierId)
    if (!stillSelected()) return
    notice.value = `解析任务已受理，版本 V${started.versionNo}；含图片的文书可能需要数分钟。`
    noticeType.value = 'info'
    for (let attempt = 0; attempt < 450; attempt += 1) {
      await new Promise((resolve) => setTimeout(resolve, 2000))
      if (!stillSelected()) return
      const detail = await getParsedDocument(selectedCaseId, started.docId)
      if (!stillSelected()) return
      if (detail.parseStatus === 'SUCCESS') {
        const noTextImages = (detail.parsedText?.warnings || []).filter((item) => item.code === 'WORD_IMAGE_NO_TEXT').length
        notice.value = noTextImages ? `文书解析完成；${noTextImages} 处图片未提取到文字，正文已保留，原图仍在上传文档中。` : '文书解析完成。'
        noticeType.value = 'success'
        await loadHistory(); return
      }
      if (detail.parseStatus === 'FAILED') throw new Error(detail.errorCode || '文书解析失败')
    }
    notice.value = '解析仍在后台执行，请稍后重新选择卷宗查看结果。'
  } catch (error) { if (stillSelected()) { fail(error); await loadHistory() } }
  finally { parsing.value = false }
}
function parseStatusLabel(value) { return ({SUCCESS:'解析成功',PROCESSING:'解析中',FAILED:'解析失败'})[value] || value }
function formatDate(value) { return value ? new Date(value).toLocaleString('zh-CN') : '-' }
function fail(error) { notice.value = parseErrors[error.message] || error.message; noticeType.value = 'error'; ElMessage.error(notice.value) }
const parseErrors = {
  WORD_DOCUMENT_INVALID:'Word 文件损坏、加密或不是有效的 DOCX，请用 Word 打开后另存为 DOCX/PDF。',
  WORD_DOCUMENT_TOO_LARGE:'Word 解压后的内容或内嵌图片过多，请拆分后上传。',
  WORD_DOCUMENT_EMPTY:'Word 中未找到可解析的文字。',
  WORD_EXTERNAL_CONTENT_UNSUPPORTED:'Word 包含外部链接图片，请将图片嵌入文档或另存为 PDF。',
  WORD_IMAGE_FORMAT_UNSUPPORTED:'Word 含暂不支持的内嵌图片格式，请另存为 PDF 后解析。',
  WORD_EMBEDDED_CONTENT_UNSUPPORTED:'Word 含嵌入对象，请另存为 PDF 后解析。',
  LEGACY_WORD_CONVERSION_REQUIRED:'旧版 DOC 请先用 Word 另存为 DOCX 或 PDF，再上传解析。',
  MINERU_SERVICE_UNAVAILABLE:'解析服务调用失败，请检查 MinerU 服务和隧道；文档内有图片时也需要 MinerU。',
  MINERU_SERVICE_REJECTED_REQUEST:'解析服务拒绝了请求，请检查文件格式和服务配置。',
}
</script>

<style scoped>
.feature-layout{align-items:start}.case-panel{position:static;align-self:start;margin-top:0;min-height:0}.cp-list{min-height:0}.cp-item.active{background:var(--blue-light)}
.cp-item{display:block;width:100%;text-align:left;background:none}.analysis-bar select{height:34px;padding:5px 10px;border:1px solid var(--border);border-radius:8px;background:#fff;color:var(--text)}.context-notice{margin-bottom:10px;padding:9px 12px;border-radius:7px;font-size:12.5px}.context-notice.info{color:var(--navy-700);background:var(--blue-light)}.context-notice.success{color:var(--green);background:var(--green-bg)}.context-notice.error{color:var(--red);background:var(--red-bg)}
</style>
