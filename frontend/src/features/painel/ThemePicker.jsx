import { Check } from 'lucide-react'
import { getTheme, themesByGroup } from '../../theme/salonThemes'
import { initials } from '../../lib/format'

function Group({ titulo, grupo, value, onChange }) {
  return (
    <div className="swatch-group">
      <div className="swatch-title" id={`sw-${grupo}`}>{titulo}</div>
      <div className="swatches" role="radiogroup" aria-labelledby={`sw-${grupo}`}>
        {themesByGroup(grupo).map((t) => (
          <button
            key={t.id} type="button" role="radio" aria-checked={value === t.id} aria-label={t.nome} title={t.nome}
            className={`swatch${grupo === 'pasteis' ? ' pastel' : ''}`} style={{ background: t.swatch }} onClick={() => onChange(t.id)}
          >
            {value === t.id && <Check size={18} strokeWidth={3} aria-hidden="true" />}
          </button>
        ))}
      </div>
    </div>
  )
}

/** Escolha de cor do portal: 7 escuras e 7 pasteis (cores do arco-iris) + previa ao vivo. */
export default function ThemePicker({ value, onChange, nomeSalao, logo, naoSalvo }) {
  const tema = getTheme(value)
  return (
    <div>
      <Group titulo="Escuras" grupo="escuras" value={value} onChange={onChange} />
      <Group titulo="Pastéis" grupo="pasteis" value={value} onChange={onChange} />
      <p className="hint" style={{ margin: '12px 0 8px' }}>Cor escolhida: <b>{tema.nome}</b>. Vale para o portal, o painel e a agenda da equipe. Login e telas da plataforma mantêm a marca Cadeira Livre.</p>
      {naoSalvo && <div className="box info" role="status" style={{ marginTop: 0, marginBottom: 12 }}>Prévia: a tela já está na nova cor, mas ela só é guardada ao clicar em <b>Salvar alterações</b>. Se sair sem salvar, a cor anterior volta.</div>}
      <div className="theme-preview" style={tema.tokens} aria-label="Prévia do portal">
        <div className="tp-head">
          <span className="brand"><span className="logo64">{logo ? <img src={logo} alt="" /> : initials(nomeSalao || 'Salão')}</span></span>
          <span className="brand-name">{nomeSalao || 'Seu salão'}</span>
        </div>
        <div className="tp-body">
          <span style={{ color: 'var(--c-ink-strong)', fontWeight: 600 }}>Corte de cabelo, R$ 45,00</span>
          <span className="btn small" aria-hidden="true">selecionar</span>
        </div>
      </div>
    </div>
  )
}
