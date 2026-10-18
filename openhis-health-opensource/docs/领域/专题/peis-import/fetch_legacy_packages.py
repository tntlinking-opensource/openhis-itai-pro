# -*- coding: utf-8 -*-
"""抓取老体检系统套餐（/base/tc）完整数据。"""
from __future__ import annotations

import json
from pathlib import Path

import requests
import urllib3

urllib3.disable_warnings()

BASE = "https://172.16.101.5:90/zyb"
USER = "zhangxiu"
PWD = "zhangxiu"
OUT_DIR = Path(r"D:\0.product\02.Whale\whale-doc\health\peis-import")


def main() -> None:
    s = requests.Session()
    s.verify = False
    s.headers.update({"Content-Type": "application/json", "Accept": "application/json"})

    # 1) 按用户取组织机构
    r = s.post(f"{BASE}/sys/sub/getzzjg", json={"username": USER}, timeout=30)
    r.raise_for_status()
    zzjg_body = r.json()
    print("getzzjg code=", zzjg_body.get("code"), "msg=", zzjg_body.get("msg"))
    zzjgs = (zzjg_body.get("data") or {}).get("zzjgs") or []
    print("zzjg count", len(zzjgs))
    if zzjgs:
        print("first zzjg", json.dumps(zzjgs[0], ensure_ascii=False)[:200])
    zzjgxz = zzjgs[0]["id"] if zzjgs else ""

    # 2) 子系统列表（登录页 GET /sys/sub/listbb）
    r = s.get(f"{BASE}/sys/sub/listbb", timeout=30)
    r.raise_for_status()
    bb = r.json()
    print("listbb code=", bb.get("code"))
    lst = (bb.get("data") or {}).get("list") or []
    sub_id = ""
    if lst:
        sub_id = lst[0].get("id") or ""
        print("sub", sub_id, lst[0].get("name"))
    if not sub_id:
        raise SystemExit("no subsystem id")

    # 3) 登录
    login_form = {
        "username": USER,
        "password": PWD,
        "zzjgxz": zzjgxz,
        "rempwd": False,
        "error": "",
        "sub": {"id": sub_id},
    }
    r = s.post(f"{BASE}/sys/user/login", json=login_form, timeout=30)
    r.raise_for_status()
    login_body = r.json()
    print("login code=", login_body.get("code"), "msg=", login_body.get("msg"))
    if login_body.get("code") != 0:
        raise SystemExit("login failed: " + json.dumps(login_body, ensure_ascii=False)[:500])

    data = login_body.get("data") or {}
    token = data.get("token") or ""
    # 有的版本把 token 放在 data.token / 顶层
    if not token and isinstance(data, dict):
        token = data.get("t") or data.get("Token") or ""
    print("token len", len(token), "keys", list(data.keys())[:20] if isinstance(data, dict) else type(data))

    # 若返回多角色需 authorize
    rolelist = data.get("list") or data.get("rolelist") or []
    if rolelist and not token:
        print("need authorize, roles", len(rolelist))
        role = rolelist[0]
        r = s.post(
            f"{BASE}/sys/user/authorize",
            json={"username": USER, "roleid": role.get("id"), "zzjgxz": zzjgxz, "sub": {"id": sub_id}},
            timeout=30,
        )
        print("authorize", r.status_code, r.text[:300].encode("ascii", "backslashreplace").decode())
        ab = r.json()
        data = ab.get("data") or {}
        token = data.get("token") or ""

    if not token:
        # dump for debug
        open(OUT_DIR / "_login_resp.json", "w", encoding="utf-8").write(
            json.dumps(login_body, ensure_ascii=False, indent=2)
        )
        raise SystemExit("no token; saved _login_resp.json")

    s.headers["token"] = token
    open(OUT_DIR / "_login_resp.json", "w", encoding="utf-8").write(
        json.dumps(login_body, ensure_ascii=False, indent=2)
    )

    # 4) 分页拉套餐列表
    all_rows = []
    pageindex = 1
    pagesize = 100
    totalcount = 0
    while True:
        params = {
            "code": "",
            "zjf": "",
            "name": "",
            "zzjg": "",
            "pagesize": pagesize,
            "pageindex": pageindex,
            "totalcount": totalcount,
        }
        r = s.get(f"{BASE}/base/tc/listpage", params=params, timeout=60)
        r.raise_for_status()
        body = r.json()
        if body.get("code") != 0:
            raise SystemExit("listpage failed: " + json.dumps(body, ensure_ascii=False)[:500])
        page = body.get("data") or {}
        rows = page.get("list") or []
        totalcount = int(page.get("totalcount") or page.get("totalCount") or 0)
        print(f"page {pageindex}: got {len(rows)}, total={totalcount}")
        all_rows.extend(rows)
        if not rows or len(all_rows) >= totalcount or len(rows) < pagesize:
            break
        pageindex += 1

    list_path = OUT_DIR / "体检套餐json.txt"
    list_path.write_text(
        json.dumps({"code": 0, "data": {"list": all_rows, "totalcount": totalcount}}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print("saved list", list_path, "count", len(all_rows))

    # 5) 逐条拉详情（含项目 tcxms）
    details = []
    for i, row in enumerate(all_rows, 1):
        tid = row.get("id")
        if not tid:
            continue
        r = s.get(f"{BASE}/base/tc/one", params={"id": tid}, timeout=60)
        r.raise_for_status()
        body = r.json()
        if body.get("code") != 0:
            print("detail fail", tid, body.get("msg"))
            continue
        detail = body.get("data") or {}
        details.append(detail)
        if i % 20 == 0 or i == len(all_rows):
            print(f"detail {i}/{len(all_rows)}")

    detail_path = OUT_DIR / "体检套餐明细json.txt"
    detail_path.write_text(
        json.dumps({"code": 0, "data": {"list": details}}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print("saved details", detail_path, "count", len(details))

    # 摘要
    if details:
        sample = details[0]
        print("sample keys", list(sample.keys())[:30])
        tcxms = sample.get("tcxms") or sample.get("list") or []
        print("sample name", sample.get("name"), "tcxms", len(tcxms) if isinstance(tcxms, list) else type(tcxms))


if __name__ == "__main__":
    main()
