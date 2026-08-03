export const featureFlags = {
  // Recurring investment plans (DCA): surfaced by default. Set
  // NEXT_PUBLIC_INVESTMENT_PLANS_ENABLED=false to hide the nav + dashboard panel
  // (kill-switch). The autonomous DCA scheduler remains separately gated on the backend.
  investmentPlans: process.env.NEXT_PUBLIC_INVESTMENT_PLANS_ENABLED !== 'false',
};
