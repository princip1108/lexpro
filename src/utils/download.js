export function saveBlob({ blob, fileName }, fallbackName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName || fallbackName
  link.click()
  URL.revokeObjectURL(url)
}
