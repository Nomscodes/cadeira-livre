import { useLayoutEffect } from 'react'
import { getTheme, temaDaCor } from './salonThemes'

/**
 * Aplica o tema do salao enquanto estiver montado.
 * `cor` e a cor primaria que vem do backend (#RRGGBB); `themeId` e o id de um dos 14 temas
 * (usado pelas telas que ainda nao foram ligadas a API).
 * As variaveis vao para <html> (e nao para um <div>) porque modais e toasts
 * sao renderizados fora da arvore do portal e tambem precisam da cor do salao.
 * Ao sair do portal tudo volta para a identidade Cadeira Livre.
 */
export default function ThemeScope({ cor, themeId, children }) {
  useLayoutEffect(() => {
    if (!cor && !themeId) return undefined // sem tema: mantem a identidade Cadeira Livre
    const theme = cor ? temaDaCor(cor) : getTheme(themeId)
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
  }, [cor, themeId])

  return children
}
