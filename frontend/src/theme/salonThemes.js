/**
 * 14 temas de cor que o responsavel pode escolher para o portal do salao:
 * as 7 cores do arco-iris em versao ESCURA e em versao PASTEL.
 *
 * Cada tema define os tokens --c-* (ver styles/tokens.css):
 *   primary / primary-hover  fundo de botoes, abas selecionadas, barras
 *   on-primary               texto sobre primary (branco nas escuras, grafite nas pastel)
 *   ink / ink-strong         titulos e links sobre fundo branco (sempre com contraste AA)
 *   soft                     fundo suave (hero, itens selecionados)
 *   accent                   realces (sublinhado do menu, bordas de selecao)
 *   line                     bordas
 *
 * Importante: o tema so e aplicado nas paginas do salao (portal publico).
 * O painel e as telas da plataforma mantem a identidade Cadeira Livre.
 */

const dark = (id, nome, primary, hover, soft, line, accent) => ({
  id: `${id}-escuro`,
  nome: `${nome} escuro`,
  cor: nome,
  grupo: 'escuras',
  swatch: primary,
  tokens: {
    '--c-primary': primary,
    '--c-primary-hover': hover,
    '--c-on-primary': '#FFFFFF',
    '--c-ink': primary,
    '--c-ink-strong': hover,
    '--c-soft': soft,
    '--c-accent': accent,
    '--c-line': line,
  },
})

const pastel = (id, nome, primary, hover, ink, soft, line, accent) => ({
  id: `${id}-pastel`,
  nome: `${nome} pastel`,
  cor: nome,
  grupo: 'pasteis',
  swatch: primary,
  tokens: {
    '--c-primary': primary,
    '--c-primary-hover': hover,
    '--c-on-primary': '#2A1F22',
    '--c-ink': ink,
    '--c-ink-strong': ink,
    '--c-soft': soft,
    '--c-accent': accent,
    '--c-line': line,
  },
})

export const THEMES = [
  //   id         nome        primary    hover      soft       line       accent
  dark('vermelho', 'Vermelho', '#9B1C2E', '#7E1624', '#FBEDEE', '#EBCBD0', '#D9737F'),
  dark('laranja', 'Laranja', '#B4510F', '#8F3F0A', '#FDF0E6', '#EBD3BF', '#E0904F'),
  dark('amarelo', 'Amarelo', '#8A6500', '#6E5000', '#FBF4DF', '#E9DDB5', '#D1A42A'),
  dark('verde', 'Verde', '#1F6B3A', '#17522C', '#E9F5EC', '#CBE3D1', '#5FAE78'),
  dark('azul', 'Azul', '#1B4F9C', '#123C7A', '#E8F0FB', '#C9DAF0', '#5B8FD6'),
  dark('anil', 'Anil', '#3B2F8F', '#2D2370', '#EDEBFA', '#D3CFF0', '#7F73D6'),
  dark('violeta', 'Violeta', '#6A2C91', '#521F73', '#F4EAFA', '#E0CDEE', '#A56BC9'),

  //     id         nome        primary    hover      ink        soft       line       accent
  pastel('vermelho', 'Vermelho', '#F2A3A8', '#EB8E94', '#8C2F3A', '#FDEDEE', '#F0CFD2', '#E27D86'),
  pastel('laranja', 'Laranja', '#F8C29B', '#F2AE7D', '#8A4A14', '#FEF3E8', '#F3DCC6', '#EDA066'),
  pastel('amarelo', 'Amarelo', '#F7E49A', '#EFD36E', '#6B5600', '#FEFAE6', '#EFE6BC', '#E8CF5E'),
  pastel('verde', 'Verde', '#B5E0BD', '#98CFA3', '#1F5A31', '#EEF8F0', '#D2EAD6', '#86C795'),
  pastel('azul', 'Azul', '#A9CBF2', '#8FB8E8', '#1D4C86', '#EDF4FC', '#CFE0F4', '#7FADE0'),
  pastel('anil', 'Anil', '#B9B5EC', '#A29DE0', '#3B3585', '#F0EFFC', '#D8D6F2', '#918CD8'),
  pastel('violeta', 'Violeta', '#D9B4EC', '#C896E2', '#5E2A80', '#F7EEFC', '#E7D3F2', '#BC86DB'),
]

export const DEFAULT_THEME_ID = 'vermelho-escuro'

export const getTheme = (id) => THEMES.find((t) => t.id === id) || THEMES.find((t) => t.id === DEFAULT_THEME_ID)
export const themesByGroup = (grupo) => THEMES.filter((t) => t.grupo === grupo)
