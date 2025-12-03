import os
from typing import Any, Dict, Optional

import requests


class GigaChatClient:
    """
    Minimal client for sending prompts to GigaChat-compatible API.

    Authentication: expects a bearer token (GIGACHAT_TOKEN env var by default).
    Endpoint: configurable via base_url, defaults to value from GIGACHAT_BASE_URL or https://gigachat.devices.sberbank.ru/api/v1/chat/completions.
    """

    def __init__(
        self,
        token: Optional[str] = None,
        base_url: Optional[str] = None,
        timeout: int = 30,
        verify: Optional[bool] = None,
    ) -> None:
        # Токен можно передать параметром или через переменную окружения
        self.token = token or os.getenv("GIGACHAT_TOKEN")
        if not self.token:
            raise ValueError("GigaChat token is required (set GIGACHAT_TOKEN or pass token param)")
        # Базовый URL по умолчанию или из окружения
        self.base_url = base_url or os.getenv(
            "GIGACHAT_BASE_URL",
            "https://gigachat.devices.sberbank.ru/api/v1/chat/completions",
        )
        self.timeout = timeout
        # Поддержка самоподписанных сертификатов:
        # - если GIGACHAT_CA_BUNDLE задан, используем путь к сертификату
        # - если GIGACHAT_VERIFY=false/0, отключаем проверку
        # - иначе используем verify или True по умолчанию
        env_verify = os.getenv("GIGACHAT_VERIFY")
        ca_bundle = os.getenv("GIGACHAT_CA_BUNDLE")
        if ca_bundle:
            self.verify = ca_bundle
        elif env_verify is not None:
            self.verify = env_verify.lower() not in {"0", "false", "no"}
        else:
            self.verify = True if verify is None else verify

    def send_prompt(self, prompt: str, model: str = "GigaChat", **extra_kwargs: Any) -> Dict[str, Any]:
        """
        Send a prompt to GigaChat and return parsed JSON response.

        extra_kwargs can include any additional fields the API supports (e.g., temperature, max_tokens).
        """
        # Формируем payload в формате Chat Completions
        payload: Dict[str, Any] = {"model": model, "messages": [{"role": "user", "content": prompt}]}
        payload.update(extra_kwargs)
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json",
        }
        response = requests.post(
            self.base_url,
            json=payload,
            headers=headers,
            timeout=self.timeout,
            verify=self.verify,
        )
        response.raise_for_status()
        return response.json()
