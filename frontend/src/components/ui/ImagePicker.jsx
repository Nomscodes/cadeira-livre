import { useId, useState } from 'react'
import { ImagePlus, X } from 'lucide-react'
import { readImage } from '../../lib/image'
import { useToast } from '../../context/ToastContext'

/** Seleciona uma imagem (logo) ou varias (galeria). Reduz o tamanho antes de devolver. */
export default function ImagePicker({ value, onChange, multiple, label, max = 900, limit = 6, wide, alt = 'Imagem atual', removeLabel = 'Remover imagem', quality }) {
  const id = useId()
  const toast = useToast()
  const [busy, setBusy] = useState(false)

  const pick = async (e) => {
    const files = [...e.target.files]
    e.target.value = ''
    if (!files.length) return
    setBusy(true)
    try {
      const imgs = await Promise.all(files.map((f) => readImage(f, { max, quality })))
      if (multiple) onChange([...(value || []), ...imgs].slice(0, limit))
      else onChange(imgs[0])
    } catch (err) {
      toast.error(err.message)
    } finally {
      setBusy(false)
    }
  }

  const input = <input id={id} className="sr-only" type="file" accept="image/*" multiple={multiple} onChange={pick} />

  if (multiple) {
    const list = value || []
    return (
      <div>
        {list.length > 0 && (
          <div className="gallery">
            {list.map((src, i) => (
              <div key={i} className="g-item">
                <img src={src} alt={`Foto ${i + 1} do portfólio`} />
                <button type="button" onClick={() => onChange(list.filter((_, j) => j !== i))} aria-label={`Remover foto ${i + 1}`}><X size={16} aria-hidden="true" /></button>
              </div>
            ))}
          </div>
        )}
        {list.length < limit && (
          <label htmlFor={id} className="upload-box">
            {input}
            <ImagePlus size={24} aria-hidden="true" />
            {busy ? 'Enviando...' : label}
            <span className="hint">até {limit} fotos</span>
          </label>
        )}
      </div>
    )
  }

  return (
    <div>
      <label htmlFor={id} className={`upload-box${value ? ' has-image' : ''}${wide ? ' wide' : ''}`}>
        {input}
        {value ? <img src={value} alt={alt} /> : <><ImagePlus size={24} aria-hidden="true" />{busy ? 'Enviando...' : label}</>}
      </label>
      {value && (
        <div className="row" style={{ marginTop: 8 }}>
          <label htmlFor={id} className="link hint" style={{ cursor: 'pointer' }}>Trocar imagem</label>
          <button type="button" className="link hint" onClick={() => onChange(null)}>{removeLabel}</button>
        </div>
      )}
    </div>
  )
}
