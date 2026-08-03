import 'styled-components';
import { TerminalTheme } from './theme';

declare module 'styled-components' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  export interface DefaultTheme extends TerminalTheme {}
}
