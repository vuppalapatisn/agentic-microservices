import Chat from "./components/Chat";

export default function App() {
  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>Observability Assistant</h1>
          <p>Ask about slow requests, errors, and metrics across your microservices.</p>
        </div>
      </header>
      <main className="main">
        <Chat />
      </main>
    </div>
  );
}
