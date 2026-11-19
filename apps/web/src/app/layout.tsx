import "@together/ui/globals.css";
import type { Metadata, Viewport } from "next";
import { AuthConfigProvider } from "@/components/auth-config-provider";
import { CloudflareWebAnalytics } from "@/components/cloudflare-web-analytics";
import { ServiceWorkerRegister } from "@/components/service-worker-register";
import { ThemeBootstrap } from "@/components/theme-bootstrap";
import { ToastProvider } from "@/components/toast";
import { UmamiAnalytics } from "@/components/umami-analytics";
import { absoluteUrl, siteUrl } from "@/lib/seo";
import { getSupabasePublicConfig } from "@/lib/supabase/public-config";

const title = "music — Listen Together";
const description =
  "A private listening space for two, with synchronized YouTube playback, shared queues, playlists, favourites and chat.";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: title,
    template: "%s | music",
  },
  description,
  manifest: "/manifest.json",
  icons: {
    icon: "/icon.svg",
    apple: "/icon.svg",
  },
  openGraph: {
    title,
    description,
    url: absoluteUrl("/"),
    type: "website",
    siteName: "music",
    images: [{ url: "/og/default.png", width: 1200, height: 630, alt: "music" }],
  },
  twitter: {
    card: "summary_large_image",
    title,
    description,
    images: ["/og/default.png"],
  },
  appleWebApp: {
    capable: true,
    statusBarStyle: "black-translucent",
    title: "music",
  },
};

export const viewport: Viewport = {
  themeColor: "#080808",
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  interactiveWidget: "resizes-content",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  const authConfig = getSupabasePublicConfig();

  return (
    <html lang="en" dir="ltr">
      <body className="min-h-dvh antialiased">
        <AuthConfigProvider config={authConfig}>
          <ThemeBootstrap />
          <a
            href="#main-content"
            className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[200] focus:rounded-lg focus:bg-[var(--accent)] focus:px-4 focus:py-2 focus:text-black"
          >
            Skip to main content
          </a>
          <ToastProvider>
            <ServiceWorkerRegister />
            <div id="main-content">{children}</div>
          </ToastProvider>
          <CloudflareWebAnalytics />
          <UmamiAnalytics />
        </AuthConfigProvider>
      </body>
    </html>
  );
}
