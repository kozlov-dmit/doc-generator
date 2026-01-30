import { useState } from "react";
import ReactMarkdown from "react-markdown";
import Field from "./components/Field.jsx";
import Panel from "./components/Panel.jsx";
import Header from "./components/Header.jsx";

export default function App() {
  const [bitbucketUrl, setBitbucketUrl] = useState("");
  const [repositoryUrl, setRepositoryUrl] = useState("");
  const [branch, setBranch] = useState("");
  const [markdown, setMarkdown] = useState("");
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);

  const submit = async () => {
    const repo = bitbucketUrl.trim() || repositoryUrl.trim();
    if (!repo) {
      setStatus("Заполните Bitbucket Url или repositoryUrl.");
      return;
    }

    setLoading(true);
    setStatus("Анализ запускается...");
    setMarkdown("");

    try {
      const response = await fetch("/api/v1/analyze-sync", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          repositoryUrl: repo,
          branch: branch.trim() || null
        })
      });

      if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `HTTP ${response.status}`);
      }

      const text = await response.text();
      setMarkdown(text);
      setStatus("Готово.");
    } catch (err) {
      setStatus(`Ошибка: ${err.message}`);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="page">
      <div className="shell">
        <Header
          title="EnvDoc Agent"
          subtitle="Синхронная генерация документации по переменным окружения."
        />
        <div className="grid">
          <Panel title="Параметры запуска">
            <Field
              label="Bitbucket Url"
              placeholder="https://bitbucket.org/org/repo.git"
              value={bitbucketUrl}
              onChange={setBitbucketUrl}
              hint="Можно оставить пустым, если используете repositoryUrl."
            />
            <Field
              label="repositoryUrl"
              placeholder="https://github.com/org/repo.git"
              value={repositoryUrl}
              onChange={setRepositoryUrl}
            />
            <Field
              label="branch"
              placeholder="main"
              value={branch}
              onChange={setBranch}
            />
            <button type="button" onClick={submit} disabled={loading}>
              {loading ? "Выполняется..." : "Сгенерировать"}
            </button>
            <div className="status">{status}</div>
          </Panel>
          <Panel title="Документация (Markdown)">
            {markdown ? (
              <div className="markdown">
                <ReactMarkdown>{markdown}</ReactMarkdown>
              </div>
            ) : (
              <p>Здесь появится результат в формате Markdown.</p>
            )}
          </Panel>
        </div>
      </div>
    </div>
  );
}
