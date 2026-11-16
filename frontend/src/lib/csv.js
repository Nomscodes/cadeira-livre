/** Escapa uma celula de CSV e neutraliza formulas (=, +, -, @) para nao executarem no Excel. */
const cell = (v) => {
  let s = String(v ?? '')
  if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`
  return `"${s.replace(/"/g, '""')}"`
}

export function toCSV(headers, rows) {
  return [headers, ...rows].map((r) => r.map(cell).join(';')).join('\r\n')
}

export function downloadFile(nome, conteudo, tipo = 'text/csv;charset=utf-8') {
  const blob = new Blob(['\ufeff', conteudo], { type: tipo })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nome
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
