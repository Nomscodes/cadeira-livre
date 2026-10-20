import { formatDuration, resumoHorarios } from '../../lib/dates'
import { initials, money } from '../../lib/format'
import { usePortal } from '../../components/layout/PortalLayout'

export default function PortalSaibaMais() {
  const { salao } = usePortal()
  const servico = (id) => salao.servicos.find((s) => s.id === id)
  const profs = (ids) => ids.map((id) => salao.profissionais.find((p) => p.id === id)?.nome).filter(Boolean)
  const horario = resumoHorarios(salao.horarios)

  return (
    <div className="sub-page wide">
      <h3 style={{ marginTop: 0 }}>Sobre nós</h3>
      <p style={{ fontSize: 13, maxWidth: '72ch' }}>{salao.sobre || 'Este salão ainda não escreveu uma apresentação.'}</p>

      <div className="info-grid">
        <div className="info-item"><span>{salao.atendimento === 'domicilio' ? 'atendimento' : 'localização'}</span><b>{salao.atendimento === 'domicilio' ? 'Esse profissional atende na sua casa!' : salao.endereco || 'não informada'}</b></div>
        <div className="info-item"><span>horário de funcionamento</span><b>{horario || 'não informado'}</b></div>
        {salao.whatsapp && <div className="info-item"><span>contato</span><b>{salao.whatsapp}</b></div>}
        {salao.redes && <div className="info-item"><span>redes sociais</span><b><a href={`https://${salao.redes.replace(/^https?:\/\//, '')}`} target="_blank" rel="noopener noreferrer">{salao.redes}</a></b></div>}
      </div>

      <section className="agenda-section" aria-labelledby="sm-prof">
        <h2 id="sm-prof">Profissionais</h2>
        {salao.profissionais.length === 0 ? <p className="hint">Nenhum profissional cadastrado.</p> : (
          <div className="grid-cards">
            {salao.profissionais.map((p) => (
              <article className="card-box prof-info" key={p.id}>
                <div className="prof-avatar" aria-hidden="true">{initials(p.nome)}</div>
                <h3 className="card-title">{p.nome}</h3>
                <dl className="mini-facts">
                  <div><dt>realiza</dt><dd><div className="chips">{p.servicoIds.length ? p.servicoIds.map(servico).filter(Boolean).map((s) => <span className="chip" key={s.id}>{s.nome}</span>) : 'nenhum serviço'}</div></dd></div>
                  <div><dt>atende</dt><dd>{resumoHorarios(p.horarios) || 'sem horário definido'}</dd></div>
                </dl>
              </article>
            ))}
          </div>
        )}
      </section>

      <section className="agenda-section" aria-labelledby="sm-serv">
        <h2 id="sm-serv">Serviços</h2>
        {salao.servicos.length === 0 ? <p className="hint">Nenhum serviço cadastrado.</p> : (
          <div className="grid-cards">
            {salao.servicos.map((s) => (
              <article className="card-box" key={s.id}>
                <h3 className="card-title">{s.nome}</h3>
                <div className="price">{money(s.preco)}</div>
                <dl className="mini-facts">
                  <div><dt>duração média</dt><dd>{formatDuration(s.duracao)}</dd></div>
                  <div><dt>com</dt><dd>{profs(s.profissionalIds).join(', ') || 'a definir'}</dd></div>
                </dl>
              </article>
            ))}
          </div>
        )}
      </section>

      {salao.combos.length > 0 && (
        <section className="agenda-section" aria-labelledby="sm-combo">
          <h2 id="sm-combo">Combos</h2>
          <div className="grid-cards">
            {salao.combos.map((c) => {
              const soma = c.servicoIds.map(servico).filter(Boolean).reduce((t, s) => t + s.preco, 0)
              const economia = soma - c.preco
              return (
                <article className="card-box" key={c.id}>
                  <h3 className="card-title">{c.nome}</h3>
                  <div className="price">{money(c.preco)}{economia > 0 && <small>economize {money(economia)}</small>}</div>
                  <dl className="mini-facts">
                    <div><dt>inclui</dt><dd>{c.servicoIds.map(servico).filter(Boolean).map((s) => s.nome).join(' + ')}</dd></div>
                    <div><dt>duração média</dt><dd>{formatDuration(c.duracao)}</dd></div>
                    <div><dt>com</dt><dd>{profs(c.profissionalIds).join(', ') || 'a definir'}</dd></div>
                  </dl>
                </article>
              )
            })}
          </div>
        </section>
      )}
    </div>
  )
}
