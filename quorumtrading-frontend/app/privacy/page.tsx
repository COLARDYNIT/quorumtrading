'use client';

import Link from 'next/link';
import styled from 'styled-components';
import { LandingLayout } from '../components/layouts/LandingLayout';
import { Panel } from '@/app/components/shared/v2';
import { MarkdownContainer } from '@/app/components/shared';

export default function PrivacyPage() {
  return (
    <LandingLayout>
      <Shell>
        <PageHeader>
          <Crumb>
            <Link href="/">Home</Link>
            <CrumbSep>/</CrumbSep>
            <CrumbMid>Legal</CrumbMid>
            <CrumbSep>/</CrumbSep>
            <CrumbCurrent>Privacy</CrumbCurrent>
          </Crumb>
          <H1>Privacy Policy</H1>
          <Desc>
            What we collect, what we send to LLM providers, and how long we keep it.
          </Desc>
          <Meta>
            <span><MetaLabel>Last updated</MetaLabel> <MetaValue>10 Feb 2026</MetaValue></span>
            <MetaSep>·</MetaSep>
            <span><MetaLabel>Contact</MetaLabel> <a href="mailto:privacy@colardyn-it.be">privacy@colardyn-it.be</a></span>
          </Meta>
        </PageHeader>

        <Panel noBodyPadding title="">
          <Article>
            <MarkdownContainer>
              <p><em>Last Updated: February 10, 2026</em></p>

              <h2>1. Introduction</h2>
              <p>
                QuorumTrading (&quot;we&quot;, &quot;our&quot;, or &quot;us&quot;) respects your privacy and is committed to protecting
                your personal data. This Privacy Policy explains how we collect, use, disclose, and safeguard your
                information when you use our Service.
              </p>

              <h2>2. Information We Collect</h2>
              <p>We collect the following types of information:</p>
              <p><strong>Account Information:</strong> When you sign in using Google or Apple authentication, we receive:</p>
              <ul>
                <li>Your name</li>
                <li>Email address</li>
                <li>Profile picture (if provided by the authentication provider)</li>
                <li>Unique user identifier from the authentication provider</li>
              </ul>
              <p><strong>Usage Data:</strong> We automatically collect information about your use of the Service:</p>
              <ul>
                <li>Trading strategies you create and simulate</li>
                <li>Simulation results and backtesting data</li>
                <li>Platform interactions and feature usage</li>
                <li>Technical data such as browser type, IP address, and device information</li>
              </ul>

              <h2>3. How We Use Your Information</h2>
              <p>We use the collected information to:</p>
              <ul>
                <li>Provide, maintain, and improve the Service</li>
                <li>Authenticate your identity and manage your account</li>
                <li>Process and execute your trading simulations</li>
                <li>Display your public strategies and simulation results (if you choose to share them)</li>
                <li>Communicate with you about the Service, updates, and security alerts</li>
                <li>Analyze usage patterns to improve our platform</li>
                <li>Ensure the security and integrity of the Service</li>
              </ul>

              <h2>4. Data Sharing and Disclosure</h2>
              <p><strong>We do not sell your personal data to third parties.</strong></p>
              <p>We may share your information only in the following limited circumstances:</p>
              <ul>
                <li><strong>Service Providers:</strong> We may share data with trusted third-party service providers who assist in operating our Service (e.g., cloud hosting, authentication services). These providers are contractually obligated to protect your data.</li>
                <li><strong>Public Strategies:</strong> If you choose to make your trading strategies or simulation results public, this information will be visible to other users of the Service.</li>
                <li><strong>Legal Requirements:</strong> We may disclose your information if required by law or in response to valid legal requests.</li>
                <li><strong>Business Transfers:</strong> In the event of a merger, acquisition, or sale of assets, your information may be transferred to the acquiring entity.</li>
              </ul>

              <h2>5. Cookies and Tracking</h2>
              <p><strong>We do not use tracking cookies or third-party advertising cookies.</strong></p>
              <p>We use only essential cookies necessary for the Service to function:</p>
              <ul>
                <li>Authentication cookies to maintain your login session</li>
                <li>Security cookies to protect against unauthorized access</li>
                <li>Preference cookies to remember your settings</li>
              </ul>
              <p>
                <strong>Payment Processing:</strong> When you make a payment, our payment processor (Stripe) may set
                cookies on your device to facilitate the checkout process, prevent fraud, and comply with financial
                regulations. These cookies are set by Stripe and governed by{' '}
                <a href="https://stripe.com/privacy" target="_blank" rel="noopener noreferrer">Stripe&apos;s Privacy Policy</a>.
              </p>
              <p>These cookies are essential for providing the Service and cannot be disabled without affecting functionality.</p>

              <h2>6. Data Security</h2>
              <p>We implement industry-standard security measures to protect your personal information:</p>
              <ul>
                <li>Encryption of data in transit using HTTPS/TLS</li>
                <li>Encryption of sensitive data at rest</li>
                <li>Secure authentication through Google and Apple OAuth 2.0</li>
                <li>Regular security audits and updates</li>
                <li>Access controls and monitoring</li>
              </ul>
              <p>However, no method of transmission over the Internet is 100% secure. We cannot guarantee absolute security.</p>

              <h2>7. Data Retention</h2>
              <p>
                We retain your personal information for as long as your account is active or as needed to provide
                the Service. You can delete your account and all associated data at any time from
                your <a href="/settings/account">Account Settings</a>.
              </p>
              <p>We may retain certain information for legitimate business purposes or as required by law, such as:</p>
              <ul>
                <li>Legal compliance and dispute resolution</li>
                <li>Fraud prevention and security</li>
                <li>Anonymized analytics data</li>
              </ul>

              <h2>8. Your Rights and Choices</h2>
              <p>Depending on your location, you may have the following rights:</p>
              <ul>
                <li><strong>Access:</strong> Request a copy of the personal data we hold about you</li>
                <li><strong>Correction:</strong> Request correction of inaccurate or incomplete data</li>
                <li><strong>Deletion:</strong> Request deletion of your personal data</li>
                <li><strong>Portability:</strong> Request a copy of your data in a machine-readable format</li>
                <li><strong>Objection:</strong> Object to certain processing of your data</li>
                <li><strong>Restriction:</strong> Request restriction of processing in certain circumstances</li>
              </ul>
              <p>
                You can delete your account and all associated data at any time from your{' '}
                <a href="/settings/account">Account Settings</a>.
                For other requests, please contact us at{' '}
                <a href="mailto:privacy@colardyn-it.be">privacy@colardyn-it.be</a>.
              </p>

              <h2>9. Children&apos;s Privacy</h2>
              <p>
                The Service is not intended for individuals under the age of 18. We do not knowingly collect
                personal information from children. If you believe we have collected information from a child,
                please contact us immediately.
              </p>

              <h2>10. International Data Transfers</h2>
              <p>
                Your information may be transferred to and processed in countries other than your country of residence.
                These countries may have different data protection laws. We ensure that appropriate safeguards are in
                place to protect your data in accordance with this Privacy Policy.
              </p>

              <h2>11. Third-Party Services</h2>
              <p>Our Service uses the following third-party authentication providers:</p>
              <ul>
                <li><strong>Google OAuth:</strong> Review <a href="https://policies.google.com/privacy" target="_blank" rel="noopener noreferrer">Google&apos;s Privacy Policy</a></li>
                <li><strong>Apple Sign In:</strong> Review <a href="https://www.apple.com/legal/privacy/" target="_blank" rel="noopener noreferrer">Apple&apos;s Privacy Policy</a></li>
                <li><strong>Stripe (Payment Processing):</strong> Review <a href="https://stripe.com/privacy" target="_blank" rel="noopener noreferrer">Stripe&apos;s Privacy Policy</a></li>
              </ul>
              <p>These services are governed by their own privacy policies, and we encourage you to review them.</p>

              <h2>12. Changes to This Privacy Policy</h2>
              <p>
                We may update this Privacy Policy from time to time. We will notify you of any material changes by
                updating the &quot;Last Updated&quot; date and, if appropriate, providing additional notice through
                the Service or via email.
              </p>

              <h2>13. Contact Us</h2>
              <p>
                If you have questions or concerns about this Privacy Policy or our data practices, please contact us:
              </p>
              <p>Email: <a href="mailto:privacy@colardyn-it.be">privacy@colardyn-it.be</a></p>
            </MarkdownContainer>
          </Article>
        </Panel>
      </Shell>
    </LandingLayout>
  );
}

// ─── Styled ──────────────────────────────────────────────────────────────

const Shell = styled.div`
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 24px 64px;
  width: 100%;
  min-width: 0;
`;

const PageHeader = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 4px 0 0;
`;

const Crumb = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: ${p => p.theme.colors.fgDimmed};

  & a {
    color: ${p => p.theme.colors.fgMuted};
    text-decoration: none;
    transition: color 120ms ease;
  }
  & a:hover { color: ${p => p.theme.colors.foreground}; }
`;

const CrumbSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const CrumbMid = styled.span`
  color: ${p => p.theme.colors.fgMuted};
`;

const CrumbCurrent = styled.span`
  color: ${p => p.theme.colors.brand};
`;

const H1 = styled.h1`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 28px;
  letter-spacing: -0.012em;
  color: ${p => p.theme.colors.fgStrong};
  font-weight: ${p => p.theme.typography.fontWeight.semibold};
  margin: 0;
  line-height: 1.25;

  @media (max-width: 768px) { font-size: 24px; }
`;

const Desc = styled.p`
  font-family: ${p => p.theme.typography.fontFamily.sans};
  font-size: 14px;
  line-height: 1.55;
  color: ${p => p.theme.colors.fgMuted};
  margin: 0;
  max-width: 78ch;
`;

const Meta = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-family: ${p => p.theme.typography.fontFamily.mono};
  font-size: 12.5px;
  color: ${p => p.theme.colors.fgDimmed};

  & a {
    color: ${p => p.theme.colors.info};
    text-decoration: none;
  }
  & a:hover { text-decoration: underline; }
`;

const MetaLabel = styled.span`
  color: ${p => p.theme.colors.fgDimmed};
  letter-spacing: 0.06em;
  text-transform: uppercase;
  font-size: 11.5px;
`;

const MetaValue = styled.span`
  color: ${p => p.theme.colors.foreground};
  font-variant-numeric: tabular-nums;
`;

const MetaSep = styled.span`
  color: ${p => p.theme.colors.border};
`;

const Article = styled.article`
  padding: 32px 28px 36px;
  max-width: 760px;
  margin: 0 auto;
`;
