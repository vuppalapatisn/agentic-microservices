export interface InvestigationRequest {
  query: string;
  correlationId?: string;
}

export interface InvestigationResponse {
  investigationId: string;
  correlationId: string;
  summary: string;
  probableRootCause: string;
  evidence: string[];
  grafanaExploreUrl?: string | null;
  grafanaDashboardUrl?: string | null;
}

export type ChatMessage =
  | { id: string; role: "user"; text: string; correlationId?: string }
  | { id: string; role: "assistant"; response: InvestigationResponse }
  | { id: string; role: "error"; text: string };
