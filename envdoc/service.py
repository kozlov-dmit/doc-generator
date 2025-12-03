import json
import re
import subprocess
import tempfile
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Set, Tuple

import yaml

from .models import CodeUsage, EnvVarInfo, YamlLocation

# Ищем плейсхолдеры вида ${ENV_VAR:default} или ${ENV_VAR}
PLACEHOLDER_RE = re.compile(r"\$\{([^}:]+)(?::([^}]*))?\}")


def clone_repo(repo_url: str, branch: Optional[str] = None, workdir: Optional[Path] = None) -> Path:
    """
    Clone repository into a temporary folder and return the path.
    """
    # Клонируем в целевую директорию (временную, если не указана явно)
    target_dir = Path(workdir) if workdir else Path(tempfile.mkdtemp(prefix="envdoc_"))
    repo_path = target_dir / "repo"
    cmd = ["git", "clone", "--depth", "1"]
    if branch:
        cmd.extend(["--branch", branch])
    cmd.extend([repo_url, str(repo_path)])
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"Failed to clone repository: {result.stderr.strip()}")
    return repo_path.resolve()


def find_application_yamls(repo_path: Path) -> List[Path]:
    # Ищем все application*.yml в репозитории
    return list(repo_path.rglob("application*.yml"))


def parse_yaml_file(path: Path) -> List[Tuple[str, str, str, Optional[str]]]:
    """
    Parse YAML and return list of (property_path, raw_value, env_name, default_value) for env placeholders.
    """
    results: List[Tuple[str, str, str, Optional[str]]] = []
    with path.open("r", encoding="utf-8") as f:
        documents = list(yaml.safe_load_all(f))

    def walk(node, path_parts: List[str]) -> None:
        # Рекурсивно обходим YAML, запоминаем путь до строки
        if isinstance(node, dict):
            for key, value in node.items():
                walk(value, path_parts + [str(key)])
        elif isinstance(node, list):
            for idx, value in enumerate(node):
                walk(value, path_parts + [f"[{idx}]"])
        else:
            if isinstance(node, str):
                # В строке ищем плейсхолдеры ${ENV:default}
                for match in PLACEHOLDER_RE.finditer(node):
                    env_name = match.group(1).strip()
                    default_value = match.group(2)
                    results.append((".".join(path_parts), node, env_name, default_value))

    for doc in documents:
        walk(doc, [])
    return results


def infer_type(value: Optional[str]) -> str:
    # Простая эвристика определения типа по строковому значению по умолчанию
    if value is None:
        return "string"
    lowered = value.lower()
    if lowered in {"true", "false"}:
        return "boolean"
    if re.fullmatch(r"-?\d+", value):
        return "integer"
    if re.fullmatch(r"-?\d+\.\d+", value):
        return "float"
    return "string"


def build_description(property_path: str) -> str:
    # Черновое описание на основе пути свойства
    return f"Value for {property_path}"

def parse_configuration_properties(file_path: Path) -> Dict[str, Set[str]]:
    """
    Heuristically parse @ConfigurationProperties classes to map property paths to field names/getters.
    Returns mapping property_path -> identifiers.
    """
    try:
        content = file_path.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        return {}

    mappings: Dict[str, Set[str]] = {}

    annotation_pattern = re.compile(
        r"@ConfigurationProperties\s*\(\s*(?:prefix\s*=\s*)?\"([^\"]+)\"", re.MULTILINE
    )
    class_pattern = re.compile(r"class\s+(\w+)")
    field_pattern = re.compile(
        r"(?:private|protected|public)\s+[^\s]+\s+(\w+)\s*(?:[;=({])", re.MULTILINE
    )

    for match in annotation_pattern.finditer(content):
        prefix = match.group(1).strip()
        class_match = class_pattern.search(content, match.end())
        if not class_match:
            continue
        class_start = class_match.end()
        brace_start = content.find("{", class_start)
        if brace_start == -1:
            continue
        # Грубый поиск границ тела класса по балансу скобок
        depth = 0
        end_idx = brace_start
        for idx in range(brace_start, len(content)):
            if content[idx] == "{":
                depth += 1
            elif content[idx] == "}":
                depth -= 1
                if depth == 0:
                    end_idx = idx
                    break
        class_body = content[brace_start:end_idx]
        for field_match in field_pattern.finditer(class_body):
            field = field_match.group(1)
            property_path = f"{prefix}.{field}" if prefix else field
            # Связываем property path с полем и его геттерами
            identifiers = mappings.setdefault(property_path, set())
            identifiers.add(field)
            identifiers.add(f"get{field[:1].upper()}{field[1:]}")
            identifiers.add(f"is{field[:1].upper()}{field[1:]}")
    return mappings

def collect_config_property_mappings(repo_path: Path) -> Dict[str, Set[str]]:
    combined: Dict[str, Set[str]] = {}
    for java_file in repo_path.rglob("*.java"):
        for prop, identifiers in parse_configuration_properties(java_file).items():
            combined.setdefault(prop, set()).update(identifiers)
    return combined


def collect_env_vars(repo_path: Path) -> Dict[str, EnvVarInfo]:
    env_vars: Dict[str, EnvVarInfo] = {}
    # Собираем все переменные окружения, найденные в YAML
    for yml_path in find_application_yamls(repo_path):
        for property_path, raw_value, env_name, default_value in parse_yaml_file(yml_path):
            info = env_vars.get(env_name)
            if not info:
                info = EnvVarInfo(
                    name=env_name,
                    default_value=default_value,
                    inferred_type=infer_type(default_value),
                    description=build_description(property_path),
                )
                env_vars[env_name] = info
            else:
                if info.default_value is None and default_value:
                    info.default_value = default_value
                    info.inferred_type = infer_type(default_value)
            info.yaml_locations.append(
                YamlLocation(
                    file=yml_path,
                    property_path=property_path,
                    raw_value=raw_value,
                    default_value=default_value,
                )
            )
    return env_vars


def build_term_map(env_vars: Dict[str, EnvVarInfo], property_identifiers: Dict[str, Set[str]]) -> Dict[str, List[str]]:
    # Готовим словарь термов для поиска в коде: имена переменных, property path и поля/геттеры конфиг-классов
    term_map: Dict[str, List[str]] = {}
    for name, info in env_vars.items():
        term_map.setdefault(name, []).append(name)
        for loc in info.yaml_locations:
            if loc.property_path:
                term_map.setdefault(loc.property_path, []).append(name)
                if loc.property_path in property_identifiers:
                    for identifier in property_identifiers[loc.property_path]:
                        term_map.setdefault(identifier, []).append(name)
    return term_map


def scan_code_usage(repo_path: Path, env_vars: Dict[str, EnvVarInfo]) -> None:
    property_identifiers = collect_config_property_mappings(repo_path)
    term_map = build_term_map(env_vars, property_identifiers)
    terms = list(term_map.keys())
    if not terms:
        return
    java_files = list(repo_path.rglob("*.java"))
    for file_path in java_files:
        try:
            lines = file_path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            continue
        for idx, line in enumerate(lines, start=1):
            # В каждой строке ищем совпадения с любым термом
            for term, names in term_map.items():
                if term in line:
                    context = line.strip()
                    for env_name in names:
                        env_vars[env_name].code_usages.append(
                            CodeUsage(file=file_path, line=idx, context=context)
                        )


def analyze_repository(repo_path: Path) -> Dict[str, EnvVarInfo]:
    # Основной конвейер: собрать переменные и их использования
    env_vars = collect_env_vars(repo_path)
    scan_code_usage(repo_path, env_vars)
    return env_vars


def render_report(env_vars: Dict[str, EnvVarInfo], output_format: str = "json") -> str:
    # Вывод отчёта в JSON или текстовом виде
    if output_format == "json":
        return json.dumps([env.as_dict() for env in env_vars.values()], indent=2)
    lines: List[str] = []
    for env in env_vars.values():
        lines.append(f"Environment variable: {env.name}")
        lines.append(f"  Type: {env.inferred_type}")
        lines.append(f"  Default: {env.default_value}")
        lines.append(f"  Description: {env.description}")
        lines.append("  YAML:")
        for loc in env.yaml_locations:
            lines.append(f"    - {loc.file}::{loc.property_path} -> {loc.raw_value}")
        if env.code_usages:
            lines.append("  Code usage:")
            for usage in env.code_usages:
                lines.append(f"    - {usage.file}:{usage.line} - {usage.context}")
        else:
            lines.append("  Code usage: not found")
        lines.append("")
    return "\n".join(lines)
