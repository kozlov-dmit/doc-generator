from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Optional


@dataclass
class YamlLocation:
    # Где в YAML встретилась переменная окружения
    file: Path
    property_path: str
    raw_value: str
    default_value: Optional[str]


@dataclass
class CodeUsage:
    # Где в коде встретилось упоминание переменной/свойства
    file: Path
    line: int
    context: str


@dataclass
class EnvVarInfo:
    # Собранная информация по одной переменной окружения
    name: str
    yaml_locations: List[YamlLocation] = field(default_factory=list)
    code_usages: List[CodeUsage] = field(default_factory=list)
    default_value: Optional[str] = None
    inferred_type: str = "string"
    description: str = ""

    def as_dict(self) -> dict:
        return {
            "name": self.name,
            "yaml_locations": [
                {
                    "file": str(loc.file),
                    "property_path": loc.property_path,
                    "raw_value": loc.raw_value,
                    "default_value": loc.default_value,
                }
                for loc in self.yaml_locations
            ],
            "code_usages": [
                {"file": str(usage.file), "line": usage.line, "context": usage.context}
                for usage in self.code_usages
            ],
            "default_value": self.default_value,
            "inferred_type": self.inferred_type,
            "description": self.description,
        }
