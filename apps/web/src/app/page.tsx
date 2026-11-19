import type { Metadata } from "next";
import { Suspense } from "react";
import { absoluteUrl, githubUrl, personalSiteUrl, siteUrl } from "@/lib/seo";
import HomePageClient from "./home-client";

const title = "music — Listen Together";
const description =
  "A private listening space for two, with synchronized YouTube playback, shared queues, playlists, favourites and chat.";

export const metadata: Metadata = {
  title,
  description,
  alternates: {
    canonical: "/",
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
};

const jsonLd = [
  {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: "music",
    url: siteUrl,
    sameAs: [githubUrl, personalSiteUrl],
  },
  {
    "@context": "https://schema.org",
    "@type": "SoftwareApplication",
    name: "music",
    applicationCategory: "MultimediaApplication",
    applicationSubCategory: "Music streaming",
    operatingSystem: "Web",
    url: siteUrl,
    description:
      "A private two-person music listening app with synchronized YouTube playback, shared playlists, favourites and chat.",
    keywords: ["music", "listening party", "YouTube", "shared playlists", "private room"],
    featureList: [
      "Synchronized YouTube playback",
      "Collaborative music queue",
      "Private listening rooms",
      "Room chat",
      "Shared playlists",
      "Favourite songs",
      "Listening history",
    ],
    offers: {
      "@type": "Offer",
      price: "0",
      priceCurrency: "USD",
    },
  },
];

export default function HomePage() {
  return (
    <>
      <script
        type="application/ld+json"
        suppressHydrationWarning
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <Suspense
        fallback={<div className="flex min-h-dvh items-center justify-center">Loading music...</div>}
      >
        <HomePageClient />
      </Suspense>
    </>
  );
}
