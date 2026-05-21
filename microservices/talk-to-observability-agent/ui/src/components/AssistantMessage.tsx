import type { InvestigationResponse } from "../types";

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
      <p className="summary">{response.summary}</p>
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
              Open logs in Grafana Explore
            </a>
          )}
          {response.grafanaDashboardUrl && (
            <a href={response.grafanaDashboardUrl} target="_blank" rel="noreferrer">
              Open metrics dashboard
            </a>
          )}
        </div>
      )}
    </div>
  );
}
