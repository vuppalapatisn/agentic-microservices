import type { InvestigationResponse } from "../types";
import {
  LOGS_LINK_LABEL,
  METRICS_LINK_LABEL,
  renderSummaryWithLinks,
} from "../utils/summaryLinks";

type Props = {
  response: InvestigationResponse;
};

export default function AssistantMessage({ response }: Props) {
  const isWelcome = response.investigationId === "welcome";

  return (
    <div className="bubble assistant-bubble">
      {!isWelcome && (
        <div className="badge-row">
          <span className="badge">{response.probableRootCause}</span>
          <span className="meta">ID {response.correlationId}</span>
        </div>
      )}
      <p className="summary">
        {isWelcome
          ? response.summary
          : renderSummaryWithLinks(
              response.summary,
              response.grafanaExploreUrl,
              response.grafanaDashboardUrl,
            )}
      </p>
      {response.evidence.length > 0 && (
        <ul className="evidence">
          {response.evidence.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
      {(response.grafanaExploreUrl || response.grafanaDashboardUrl) && (
        <div className="links">
          {response.grafanaExploreUrl && (
            <a href={response.grafanaExploreUrl} target="_blank" rel="noreferrer">
              {LOGS_LINK_LABEL}
            </a>
          )}
          {response.grafanaDashboardUrl && (
            <a href={response.grafanaDashboardUrl} target="_blank" rel="noreferrer">
              {METRICS_LINK_LABEL}
            </a>
          )}
        </div>
      )}
    </div>
  );
}
