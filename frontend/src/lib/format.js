const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
export const money = (v) => brl.format(Number(v) || 0).replace(/\u00a0/g, ' ')

/** "45,50" ou "45.5" -> 45.5 (NaN se invalido) */
export const parseMoney = (txt) => {
  const n = parseFloat(String(txt).replace(/\./g, '').replace(',', '.'))
  return Number.isFinite(n) ? n : NaN
}

export const digits = (txt) => String(txt || '').replace(/\D/g, '')

/** Mascara de telefone brasileiro: (62) 99999-0000 */
export const maskPhone = (txt) => {
  const d = digits(txt).slice(0, 11)
  if (d.length <= 2) return d
  if (d.length <= 6) return `(${d.slice(0, 2)}) ${d.slice(2)}`
  if (d.length <= 10) return `(${d.slice(0, 2)}) ${d.slice(2, 6)}-${d.slice(6)}`
  return `(${d.slice(0, 2)}) ${d.slice(2, 7)}-${d.slice(7)}`
}

export const initials = (name = '') =>
  name.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase()

export const slugify = (txt) =>
  String(txt).normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')

export const isEmail = (v) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)

export const plural = (n, one, many) => (n === 1 ? one : many)
