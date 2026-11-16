/** Quadro arredondado sobre o fundo creme (como no mockup). No celular o CSS remove a moldura. */
export default function Frame({ children }) {
  return (
    <div className="wrap">
      <div className="frame">{children}</div>
    </div>
  )
}
