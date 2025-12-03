import argparse
import sys
import tempfile
from pathlib import Path

from .docgen import generate_doc_env_md
from .gigachat import GigaChatClient
from .service import analyze_repository, clone_repo, render_report


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Scan a Spring Boot repository for environment variable usage."
    )
    # Описание аргументов CLI
    parser.add_argument("--repo", help="Git URL of the repository to scan")
    parser.add_argument("--branch", help="Optional branch or tag to checkout")
    parser.add_argument(
        "--local-path", help="Use an existing local repository instead of cloning"
    )
    parser.add_argument(
        "--output-format",
        choices=["json", "text"],
        default="json",
        help="Output format for the report",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help="Write the report to the given file instead of stdout",
    )
    parser.add_argument(
        "--keep-clone",
        action="store_true",
        help="Keep the cloned repository on disk (printed to stdout)",
    )
    parser.add_argument(
        "--generate-doc",
        action="store_true",
        help="Generate doc-env.md via GigaChat after analysis",
    )
    parser.add_argument(
        "--doc-output",
        type=Path,
        default=Path("doc-env.md"),
        help="Where to write generated doc-env.md",
    )
    parser.add_argument(
        "--service-name",
        default="java-сервис",
        help="Service name for documentation prompt",
    )
    parser.add_argument(
        "--gigachat-model",
        default="GigaChat",
        help="Model name to use for GigaChat requests",
    )
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)

    def produce_outputs(repo_path: Path) -> int:
        # Основная работа: анализ репо, вывод отчёта и, по желанию, генерация doc-env.md через GigaChat
        env_vars = analyze_repository(repo_path)
        report = render_report(env_vars, args.output_format)
        if args.output:
            args.output.write_text(report, encoding="utf-8")
        else:
            print(report)

        if args.generate_doc:
            try:
                client = GigaChatClient()
            except Exception as exc:  # noqa: BLE001
                print(f"Failed to init GigaChat client: {exc}", file=sys.stderr)
                return 1
            try:
                doc_md = generate_doc_env_md(
                    env_vars,
                    client,
                    service_name=args.service_name,
                    model=args.gigachat_model,
                )
            except Exception as exc:  # noqa: BLE001
                print(f"Failed to generate doc via GigaChat: {exc}", file=sys.stderr)
                return 1
            args.doc_output.write_text(doc_md, encoding="utf-8")
            print(f"doc-env.md saved to {args.doc_output}")
        return 0

    # Обязателен либо URL репозитория, либо локальный путь
    if not args.repo and not args.local_path:
        print("Please provide --repo or --local-path", file=sys.stderr)
        return 1

    repo_path: Path
    # Режим работы с уже существующим локальным репозиторием
    if args.local_path:
        repo_path = Path(args.local_path).resolve()
        if not repo_path.exists():
            print(f"Local path does not exist: {repo_path}", file=sys.stderr)
            return 1
        return produce_outputs(repo_path)

    # Режим, когда хотим оставить клон на диске
    if args.keep_clone:
        workdir = Path(tempfile.mkdtemp(prefix="envdoc_run_"))
        repo_path = clone_repo(args.repo, args.branch, workdir)
        print(f"Cloned into: {repo_path}")
        return produce_outputs(repo_path)

    # Основной путь: клон во временный каталог, автоснос
    with tempfile.TemporaryDirectory(prefix="envdoc_run_") as tmpdir:
        repo_path = clone_repo(args.repo, args.branch, Path(tmpdir))
        return produce_outputs(repo_path)


if __name__ == "__main__":
    sys.exit(main())
