import '@testing-library/jest-dom';
import 'jest-styled-components';

declare global {
  namespace jest {
    interface Matchers<R> {
      toBeInTheDocument(): R;
      toHaveStyleRule(property: string, value?: string | RegExp, options?: object): R;
      toBeVisible(): R;
      toBeDisabled(): R;
      toBeEnabled(): R;
      toHaveTextContent(text: string | RegExp): R;
      toHaveAttribute(attr: string, value?: string | RegExp): R;
      toHaveClass(...classNames: string[]): R;
      toHaveStyle(css: string | object): R;
      toContainElement(element: HTMLElement | null): R;
      toBeEmptyDOMElement(): R;
      toHaveFocus(): R;
      toBeChecked(): R;
      toBePartiallyChecked(): R;
      toHaveValue(value?: string | string[] | number): R;
      toHaveDisplayValue(value: string | RegExp | (string | RegExp)[]): R;
      toBeRequired(): R;
      toBeInvalid(): R;
      toBeValid(): R;
      toHaveAccessibleDescription(description?: string | RegExp): R;
      toHaveAccessibleName(name?: string | RegExp): R;
      toHaveErrorMessage(message?: string | RegExp): R;
    }
  }
}
