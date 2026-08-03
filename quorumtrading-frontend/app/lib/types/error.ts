/**
 * RFC 9457 Problem Details for HTTP APIs
 * https://datatracker.ietf.org/doc/html/rfc9457
 *
 * Also supports RFC 7807 (deprecated but still referenced by backend)
 */
export interface ProblemDetail {
  /** URI reference identifying the problem type */
  type: string;
  /** Short, human-readable summary */
  title: string;
  /** HTTP status code */
  status: number;
  /** Human-readable explanation specific to this occurrence */
  detail?: string;
  /** URI reference identifying the specific occurrence */
  instance?: string;
  /** ISO 8601 timestamp when the error occurred */
  timestamp?: string;
  /** Additional validation errors (for 400 Bad Request) */
  errors?: ValidationError[];
  /** URL to budget management page (present for 402 responses) */
  budgetUrl?: string;
}

export interface ValidationError {
  /** Field name that failed validation */
  field: string;
  /** Validation error message */
  message: string;
  /** The value that was rejected */
  rejectedValue?: unknown;
}

/**
 * Type guard to check if error response is RFC 9457 Problem Detail
 */
export function isProblemDetail(error: unknown): error is ProblemDetail {
  return (
    typeof error === 'object' &&
    error !== null &&
    'type' in error &&
    'title' in error &&
    'status' in error
  );
}

/**
 * Extract error message from various error formats
 */
export function getErrorMessage(error: unknown): string {
  if (isProblemDetail(error)) {
    return error.detail || error.title;
  }

  if (error instanceof Error) {
    return error.message;
  }

  if (typeof error === 'string') {
    return error;
  }

  return 'An unexpected error occurred';
}

/**
 * Extract all validation error messages
 */
export function getValidationErrors(error: unknown): ValidationError[] {
  if (isProblemDetail(error) && error.errors) {
    return error.errors;
  }
  return [];
}

/**
 * Format validation errors as a readable string
 */
export function formatValidationErrors(error: unknown): string {
  const errors = getValidationErrors(error);
  if (errors.length === 0) {
    return getErrorMessage(error);
  }

  return errors
    .map(err => `${err.field}: ${err.message}`)
    .join(', ');
}
