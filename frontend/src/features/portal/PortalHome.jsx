import { Link } from 'react-router-dom'
import { formatDuration } from '../../lib/dates'
import { money } from '../../lib/format'
import { usePortal } from '../../components/layout/PortalLayout'

export default function PortalHome() {
  const { salao, base } = usePortal()
  const nomeProf = (id) => salao.profissionais.find((p) => p.id === id)?.nome
  const destaques = (salao.servicos.filter((s) => s.destaque).length ? salao.servicos.filter((s) => s.destaque) : salao.servicos).slice(0, 4)

  return (
    <>
      <h1 className="sr-only">{salao.nome}</h1>
      <section
        className={`hero${salao.capa ? ' com-capa' : ''}`} aria-label="Apresentação"
        style={salao.capa ? { backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.28), rgba(0, 0, 0, 0.28)), url("${salao.capa}")` } : undefined}
      >
        <Link to={`${base}/agendar`} className="btn">Agendar horário</Link>
      </section>

      <section className="portal-section" aria-labelledby="titulo-destaques">
        <h2 className="section-title" id="titulo-destaques">Serviços em destaque</h2>
        {destaques.length === 0 ? (
          <p className="hint">Este salão ainda não cadastrou serviços.</p>
        ) : (
          <div className="grid-cards">
            {destaques.map((s) => (
              <article className="card-box" key={s.id}>
                <div className="thumb" aria-hidden="true">foto</div>
                <h3 className="card-title">{s.nome}</h3>
                <div className="meta">
                  {money(s.preco)} &middot; {formatDuration(s.duracao)}
                  {s.profissionalIds.length > 0 && <> &middot; {s.profissionalIds.map(nomeProf).filter(Boolean).join(', ')}</>}
                </div>
                <Link to={`${base}/agendar?servico=${s.id}`} className="btn small block">selecionar</Link>
              </article>
            ))}
          </div>
        )}
      </section>

      {salao.galeria?.length > 0 && (
        <section className="portal-section" aria-labelledby="titulo-galeria">
          <h2 className="section-title" id="titulo-galeria">Nosso trabalho</h2>
          <div className="gallery" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))' }}>
            {salao.galeria.map((src, i) => <div className="g-item" key={i}><img src={src} alt={`Trabalho realizado no salão, foto ${i + 1}`} loading="lazy" /></div>)}
          </div>
        </section>
      )}
    </>
  )
}
