import { useLayoutEffect } from 'react'
import { getTheme } from './salonThemes'

/**
 * Aplica o tema do salao enquanto estiver montado.
 * As variaveis vao para <html> (e nao para um <div>) porque modais e toasts
 * sao renderizados fora da arvore do portal e tambem precisam da cor do salao.
 * Ao sair do portal tudo volta para a identidade Cadeira Livre.
 */
export default function ThemeScope({ themeId, children }) {
  useLayoutEffect(() => {
    if (!themeId) return undefined // sem tema: mantem a identidade Cadeira Livre
    const theme = getTheme(themeId)
    const root = document.documentElement
    const meta = document.querySelector('meta[name="theme-color"]')
    const prevMeta = meta?.getAttribute('content')

    Object.entries(theme.tokens).forEach(([k, v]) => root.style.setProperty(k, v))
    // o fundo da pagina tambem acompanha a cor do salao (versao bem clara do tom suave)
    root.style.setProperty('--bg', 'color-mix(in srgb, var(--c-soft) 38%, #ffffff)')
    meta?.setAttribute('content', theme.tokens['--c-primary'])

    return () => {
      Object.keys(theme.tokens).forEach((k) => root.style.removeProperty(k))
      root.style.removeProperty('--bg')
      if (meta && prevMeta) meta.setAttribute('content', prevMeta)
    }
  }, [themeId])

  return children
}
