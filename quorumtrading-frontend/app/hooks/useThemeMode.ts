'use client';

import { useContext } from 'react';
import { ThemeModeContext } from '../contexts/ThemeModeContext';

export function useThemeMode() {
  return useContext(ThemeModeContext);
}
