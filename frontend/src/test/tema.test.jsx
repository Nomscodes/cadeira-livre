import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import ThemeScope from '../theme/ThemeScope'
import { DEFAULT_THEME_ID, getTheme, temaDaCor } from '../theme/salonThemes'

// o backend guarda so a cor primaria do portal (corPrimaria, #RRGGBB); o front monta o tema a partir dela
const estilo = (k) => document.documentElement.style.getPropertyValue(k)

describe('tema pela cor do backend', () => {
  it('cor de um dos 14 temas usa o tema pronto', () => {
    expect(temaDaCor('#1B4F9C').id).toBe('azul-escuro')
    expect(temaDaCor('#b5e0bd').id).toBe('verde-pastel') // aceita letras minusculas
  })

  it('outra cor monta os tokens a partir dela', () => {
    const escura = temaDaCor('#123456')
    expect(escura.id).toBe('personalizado')
    expect(escura.tokens['--c-primary']).toBe('#123456')
    expect(escura.tokens['--c-on-primary']).toBe('#FFFFFF')

    // cor clara: texto escuro em cima do botao
    expect(temaDaCor('#FFE4F0').tokens['--c-on-primary']).toBe('#2A1F22')
  })

  it('sem cor ou cor invalida fica o tema padrao', () => {
    expect(temaDaCor(null).id).toBe(DEFAULT_THEME_ID)
    expect(temaDaCor('azul').id).toBe(DEFAULT_THEME_ID)
  })

  it('ThemeScope aplica a cor do backend e limpa ao sair', () => {
    const { unmount } = render(<ThemeScope cor="#1B4F9C"><p>portal</p></ThemeScope>)
    expect(estilo('--c-primary')).toBe('#1B4F9C')
    unmount()
    expect(estilo('--c-primary')).toBe('')
  })

  it('ThemeScope ainda aceita o id do tema (telas antigas)', () => {
    render(<ThemeScope themeId="verde-escuro"><p>painel</p></ThemeScope>)
    expect(estilo('--c-primary')).toBe(getTheme('verde-escuro').tokens['--c-primary'])
  })
})
