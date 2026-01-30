/* global React, ReactDOM, marked */

const { useState } = React;

function App() {
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
    React.createElement("div", { className: "page" },
      React.createElement("div", { className: "shell" },
        React.createElement("header", null,
          React.createElement("h1", null, "EnvDoc Agent"),
          React.createElement("p", null, "Синхронная генерация документации по переменным окружения. Заполните поля и получите Markdown-документ.")
        ),
        React.createElement("div", { className: "grid" },
          React.createElement("div", { className: "panel" },
            React.createElement("h2", null, "Параметры запуска"),
            React.createElement("label", null, "Bitbucket Url"),
            React.createElement("input", {
              value: bitbucketUrl,
              onChange: (e) => setBitbucketUrl(e.target.value),
              placeholder: "https://bitbucket.org/org/repo.git"
            }),
            React.createElement("div", { className: "hint" }, "Можно оставить пустым, если используете repositoryUrl."),
            React.createElement("label", null, "repositoryUrl"),
            React.createElement("input", {
              value: repositoryUrl,
              onChange: (e) => setRepositoryUrl(e.target.value),
              placeholder: "https://github.com/org/repo.git"
            }),
            React.createElement("label", null, "branch"),
            React.createElement("input", {
              value: branch,
              onChange: (e) => setBranch(e.target.value),
              placeholder: "main"
            }),
            React.createElement("button", { onClick: submit, disabled: loading }, loading ? "Выполняется..." : "Сгенерировать"),
            React.createElement("div", { className: "status" }, status)
          ),
          React.createElement("div", { className: "panel markdown" },
            markdown
              ? React.createElement("div", { dangerouslySetInnerHTML: { __html: marked.parse(markdown) } })
              : React.createElement("p", null, "Здесь появится результат в формате Markdown.")
          )
        )
      )
    )
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(React.createElement(App));
