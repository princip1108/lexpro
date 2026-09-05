export function cardFieldPending(row) {
  return !row.confirmStatus || row.confirmStatus === 'UNCONFIRMED'
}

export function cardFieldValue(row) {
  const text = String(row._value ?? '').trim()
  if (!text) throw new Error(`请填写“${row.fieldName}”的候选值`)
  if (typeof row.value === 'number') {
    const number = Number(text)
    if (!Number.isFinite(number)) throw new Error(`“${row.fieldName}”必须是有效数字`)
    return number
  }
  if (typeof row.value === 'boolean') {
    if (text === 'true' || text === '是') return true
    if (text === 'false' || text === '否') return false
    throw new Error(`“${row.fieldName}”必须是是或否`)
  }
  if (row.value && typeof row.value === 'object') {
    try { return JSON.parse(text) } catch { throw new Error(`“${row.fieldName}”格式无效`) }
  }
  return text
}
