# -*- coding: utf-8 -*-
"""Проверка Vikunja API из Python: UTF-8 текст задачи, создание PUT, чтение, удаление."""
import json
import os
import urllib.request

BASE = os.environ.get("VIKUNJA_URL", "https://task.urldot.ru")
TOK = os.environ.get("VIKUNJA_TOKEN", "")
if not TOK:
    raise SystemExit("Set VIKUNJA_TOKEN (Settings -> API Tokens in Vikunja)")
TITLE = "Тест голосового ввода (удалить)"


def call(method, path, payload=None):
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        BASE + path,
        data=data,
        method=method,
        headers={
            "Authorization": "Bearer " + TOK,
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    with urllib.request.urlopen(req, timeout=20) as r:
        body = r.read().decode("utf-8")
        return r.status, json.loads(body) if body.strip() else None


status, task = call("PUT", "/api/v1/projects/1/tasks", {"title": TITLE})
title_ok = task.get("title") == TITLE
tid = task.get("id")
print("create:", status, "| id:", tid, "| title_ok:", title_ok)

status, got = call("GET", "/api/v1/tasks/%s" % tid)
print("read:  ", status, "| title_ok:", got.get("title") == TITLE)

status, _ = call("DELETE", "/api/v1/tasks/%s" % tid)
print("delete:", status)
