import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// jsdom nao implementa algumas APIs usadas pela interface
window.scrollTo = () => {}
window.open = () => null

beforeEach(() => {
  window.localStorage.clear()
  document.documentElement.removeAttribute('style')
})
afterEach(() => cleanup())
