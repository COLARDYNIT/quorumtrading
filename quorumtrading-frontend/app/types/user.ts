export interface User {
  id: string;
  email: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: 'USER' | 'ADMIN';
  providers: string[];
  createdAt: string;
  lastLoginAt: string | null;
  welcomeBudgetCredited: boolean;
  returningUser: boolean;
}
