import type { InvestigationRequest, InvestigationResponse } from "./types";

const apiBase = import.meta.env.VITE_API_BASE_URL ?? "";

export async function investigate(
  body: InvestigationRequest
): Promise<InvestigationResponse> {
  const response = await fetch(`${apiBase}/api/v1/investigate`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    let detail = response.statusText;
    try {
      const err = (await response.json()) as { detail?: string };
      if (err.detail) detail = err.detail;
    } catch {
      /* ignore */
    }
    throw new Error(detail);
  }

  return response.json() as Promise<InvestigationResponse>;
}
