import type { ReactNode } from "react";

const URL_PATTERN = /https?:\/\/[^\s]+/gi;

function trimTrailingPunctuation(url: string): { href: string; suffix: string } {
  const trailing = url.match(/[.,;:!?)]+$/);
  if (!trailing) {
    return { href: url, suffix: "" };
  }
  return { href: url.slice(0, -trailing[0].length), suffix: trailing[0] };
}

const LOGS_LINK_LABEL = "Open logs in Grafana Explore";
const METRICS_LINK_LABEL = "Open metrics dashboard";

function linkLabel(
  href: string,
  exploreUrl?: string | null,
  dashboardUrl?: string | null,
): string {
  if (exploreUrl && href === exploreUrl) {
    return LOGS_LINK_LABEL;
  }
  if (dashboardUrl && href === dashboardUrl) {
    return METRICS_LINK_LABEL;
  }
  if (href.includes("/explore")) {
    return LOGS_LINK_LABEL;
  }
  if (href.includes("/d/")) {
    return METRICS_LINK_LABEL;
  }
  return "link";
}

export { LOGS_LINK_LABEL, METRICS_LINK_LABEL };

/** Remove embedded Grafana URLs from summary when dedicated link fields exist. */
export function cleanSummaryText(
  summary: string,
  exploreUrl?: string | null,
  dashboardUrl?: string | null,
): string {
  let text = summary;
  if (exploreUrl) {
    text = text.split(exploreUrl).join("");
  }
  if (dashboardUrl) {
    text = text.split(dashboardUrl).join("");
  }
  return text
    .replace(/View correlated logs in Grafana Explore:\s*/gi, "")
    .replace(/View JVM metrics for the incident window:\s*/gi, "")
    .replace(/\s{2,}/g, " ")
    .trim();
}

/** Render summary text with http(s) URLs as short clickable links. */
export function renderSummaryWithLinks(
  summary: string,
  exploreUrl?: string | null,
  dashboardUrl?: string | null,
): ReactNode {
  const text = cleanSummaryText(summary, exploreUrl, dashboardUrl);
  if (!text) {
    return null;
  }

  const parts: ReactNode[] = [];
  let lastIndex = 0;
  let key = 0;

  for (const match of text.matchAll(URL_PATTERN)) {
    const raw = match[0];
    const start = match.index ?? 0;
    if (start > lastIndex) {
      parts.push(text.slice(lastIndex, start));
    }
    const { href, suffix } = trimTrailingPunctuation(raw);
    parts.push(
      <a
        key={`link-${key++}`}
        className="inline-link"
        href={href}
        target="_blank"
        rel="noreferrer"
      >
        {linkLabel(href, exploreUrl, dashboardUrl)}
      </a>,
    );
    if (suffix) {
      parts.push(suffix);
    }
    lastIndex = start + raw.length;
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return parts.length === 1 ? parts[0] : <>{parts}</>;
}
