import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import '@fontsource/dm-serif-display/400.css'
import '@fontsource/dm-sans/400.css'
import '@fontsource/dm-sans/600.css'
import '@fontsource/dm-sans/700.css'
import '@fontsource/ibm-plex-mono/400.css'

import './styles/tokens.css'
import './styles/base.css'
import './styles/components.css'
import './styles/layout.css'
import './styles/pages.css'

import App from './App'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
