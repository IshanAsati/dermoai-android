import type { Metadata, Viewport } from "next";
import { Lora, Raleway } from "next/font/google";
import "./globals.css";

// Self-hosted at build time by next/font: no request to Google from a doctor's browser.
const lora = Lora({ subsets: ["latin"], variable: "--font-lora", display: "swap" });
const raleway = Raleway({ subsets: ["latin"], variable: "--font-raleway", display: "swap" });

export const metadata: Metadata = {
  title: { default: "DermoAI Doctor", template: "%s · DermoAI Doctor" },
  description: "Triage and follow the patients who have shared their DermoAI skin scans with you.",
  robots: { index: false, follow: false },
  applicationName: "DermoAI Doctor",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#eae4da",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${lora.variable} ${raleway.variable}`}>
      <body>
        <a className="skip-link" href="#main">
          Skip to content
        </a>
        {children}
      </body>
    </html>
  );
}
