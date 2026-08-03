'use client';

import React, { useState } from 'react';
import { useServerInsertedHTML } from 'next/navigation';
import { ServerStyleSheet, StyleSheetManager } from 'styled-components';

/**
 * StyledComponentsRegistry collects styles during SSR and injects them
 * into the <head> to prevent Flash of Unstyled Content (FOUC).
 *
 * @see https://nextjs.org/docs/app/building-your-application/styling/css-in-js#styled-components
 */
export default function StyledComponentsRegistry({
  children,
}: {
  children: React.ReactNode;
}) {
  // Only create stylesheet once with lazy initial state
  const [styledComponentsStyleSheet] = useState(() => new ServerStyleSheet());

  useServerInsertedHTML(() => {
    const styles = styledComponentsStyleSheet.getStyleElement();
    styledComponentsStyleSheet.instance.clearTag();
    return <>{styles}</>;
  });

  if (typeof window !== 'undefined') {
    // Client-side: render children directly
    return <>{children}</>;
  }

  // Server-side: wrap with StyleSheetManager to collect styles
  return (
    <StyleSheetManager sheet={styledComponentsStyleSheet.instance}>
      {children}
    </StyleSheetManager>
  );
}
