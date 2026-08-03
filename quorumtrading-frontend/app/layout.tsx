import type { Metadata } from "next";
import { IBM_Plex_Mono, IBM_Plex_Sans } from "next/font/google";
import StyledComponentsRegistry from "./lib/registry";
import { Providers } from "./providers";

const ibmPlexMono = IBM_Plex_Mono({
  weight: ['400', '500', '600', '700'],
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-ibm-plex-mono',
});

const ibmPlexSans = IBM_Plex_Sans({
  weight: ['400', '500', '600', '700'],
  subsets: ['latin'],
  display: 'swap',
  variable: '--font-ibm-plex-sans',
});

export const metadata: Metadata = {
  title: "QuorumTrading - Terminal Dashboard",
  description: "Real-time AI trading simulation dashboard with 80s terminal aesthetic",
};

// Inline script to set theme before first paint (prevents FOUC)
// Inline script to set theme before first paint (prevents FOUC)
// Also sets semantic CSS vars used by global-error.tsx (outside ThemeProvider)
const themeInitScript = `
(function(){
  var d={bg:'#0a0e14',fg:'#e0e0e0',info:'#00d9ff',err:'#ff3366',muted:'#7a8f84',surface:'#0f1419'};
  var l={bg:'#f4f4f0',fg:'#1a2e1f',info:'#0077aa',err:'#cc1144',muted:'#6b7c72',surface:'#ffffff'};
  var s=localStorage.getItem('quorum-theme-mode');
  if(!s){s='dark';}
  document.documentElement.setAttribute('data-theme',s);
  var t=s==='light'?l:d;
  var r=document.documentElement.style;
  r.setProperty('--qt-bg',t.bg);
  r.setProperty('--qt-fg',t.fg);
  r.setProperty('--qt-info',t.info);
  r.setProperty('--qt-err',t.err);
  r.setProperty('--qt-muted',t.muted);
  r.setProperty('--qt-surface',t.surface);
})();
`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${ibmPlexMono.variable} ${ibmPlexSans.variable}`} suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body style={{ background: 'var(--qt-bg, #0a0e14)', color: 'var(--qt-fg, #e0e0e0)' }}>
        <StyledComponentsRegistry>
          <Providers>
            {children}
          </Providers>
        </StyledComponentsRegistry>
      </body>
    </html>
  );
}
