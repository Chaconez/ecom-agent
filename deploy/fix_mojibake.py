#!/usr/bin/env python3
"""修复 ecom_cs 库中双重编码的中文种子数据。

背景：schema.sql 预置时客户端连接字符集为 latin1，UTF-8 中文字节被按
CP1252 解读后以 utf8mb4 存储，形成 "ä¸Šæµ·" 类乱码。
本脚本通过 HEX() 读取（纯 ASCII 传输，避免二次损坏），在本地还原后
以 UNHEX() 写回。
"""
import os
import shlex
import subprocess
import sys

# 通过环境变量注入，避免把凭据写进仓库：
#   NAS_SSH=user@your-nas-ip MYSQL_ROOT_PASSWORD=xxx python3 fix_mojibake.py
NAS = os.environ.get("NAS_SSH", "user@your-nas-ip")
DB = "ecom_cs"

# CP1252 中 0x80-0x9F 特殊字符 → 字节值 的逆映射
CP1252_REV = {
    0x20AC: 0x80, 0x201A: 0x82, 0x0192: 0x83, 0x201E: 0x84, 0x2026: 0x85,
    0x2020: 0x86, 0x2021: 0x87, 0x02C6: 0x88, 0x2030: 0x89, 0x0160: 0x8A,
    0x2039: 0x8B, 0x0152: 0x8C, 0x017D: 0x8E, 0x2018: 0x91, 0x2019: 0x92,
    0x201C: 0x93, 0x201D: 0x94, 0x2022: 0x95, 0x2013: 0x96, 0x2014: 0x97,
    0x02DC: 0x98, 0x2122: 0x99, 0x0161: 0x9A, 0x203A: 0x9B, 0x0153: 0x9C,
    0x017E: 0x9E, 0x0178: 0x9F,
}

# 乱码签名字符（CP1252 解读 UTF-8 中文字节后的常见字符）
MOJIBAKE_HINT = set("äåæçèéêëìíîïðñòóôõö÷øùúûüýþÿ"
                    "€‚ƒ„…†‡ˆ‰Š‹ŒŽ''""•–—˜™š›œžŸ"
                    "¡¢£¤¥¦§¨©ª«¬®¯°±²³´µ¶·¸¹º»¼½¾¿")


def demojibake(s: str):
    """把双重编码字符串还原为正确中文；无法还原时返回 None。"""
    bs = bytearray()
    for ch in s:
        o = ord(ch)
        if o < 0x100:
            bs.append(o)
        elif o in CP1252_REV:
            bs.append(CP1252_REV[o])
        else:
            return None
    try:
        return bytes(bs).decode("utf-8")
    except UnicodeDecodeError:
        return None


def is_double_encoded(s: str):
    """判断是否为双重编码：含乱码签名字符，且能还原出 CJK 文本。"""
    if not any(ch in MOJIBAKE_HINT for ch in s):
        return None
    fixed = demojibake(s)
    if fixed and any("\u4e00" <= ch <= "\u9fff" for ch in fixed):
        return fixed
    return None


def mysql(query: str) -> str:
    root_pwd = os.environ.get("MYSQL_ROOT_PASSWORD", "")
    if not root_pwd:
        raise RuntimeError("请先设置环境变量 MYSQL_ROOT_PASSWORD")
    cmd = [
        "ssh", NAS,
        "docker run --rm --network host mysql:8 mysql -h 127.0.0.1 "
        f"-uroot -p{shlex.quote(root_pwd)} {DB} -e {shlex.quote(query)}",
    ]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(f"SQL 失败: {query[:80]}...\n{r.stderr}")
    return r.stdout


def rows(query: str):
    out = mysql(query)
    lines = out.splitlines()
    if len(lines) < 2:
        return []
    header = lines[0].split("\t")
    result = []
    for line in lines[1:]:
        parts = line.split("\t")
        if len(parts) < len(header):
            parts += [""] * (len(header) - len(parts))
        result.append(dict(zip(header, parts)))
    return result


def main():
    tables = [r["Tables_in_" + DB] for r in rows("SHOW TABLES")]
    print(f"共 {len(tables)} 张表: {', '.join(tables)}")

    total_fixed = 0
    for t in tables:
        # 主键列
        keys = rows(f"SHOW KEYS FROM `{t}` WHERE Key_name = 'PRIMARY'")
        if not keys:
            print(f"[跳过] {t}: 无主键")
            continue
        pk = keys[0]["Column_name"]

        # 文本类列
        cols = []
        for c in rows(f"SHOW COLUMNS FROM `{t}`"):
            ctype = c["Type"].lower()
            if any(k in ctype for k in ("char", "text", "json", "enum", "set")):
                cols.append(c["Field"])
        if not cols:
            continue

        table_fixes = []
        for col in cols:
            for r in rows(f"SELECT `{pk}`, HEX(`{col}`) AS h FROM `{t}`"):
                h = r.get("h") or ""
                if not h or h == "NULL":
                    continue
                try:
                    raw = bytes.fromhex(h)
                except ValueError:
                    continue
                try:
                    s = raw.decode("utf-8")
                except UnicodeDecodeError:
                    continue
                if not any(ord(ch) > 127 for ch in s):
                    continue
                fixed = is_double_encoded(s)
                if fixed:
                    table_fixes.append((pk, r[pk], col, fixed))

        if not table_fixes:
            continue
        print(f"\n[{t}] 发现 {len(table_fixes)} 处双重编码:")
        for _, rid, col, fixed in table_fixes:
            print(f"  id={rid} {col}: -> {fixed[:40]}{'...' if len(fixed) > 40 else ''}")

        for _, rid, col, fixed in table_fixes:
            newhex = fixed.encode("utf-8").hex()
            rid_sql = rid if rid.isdigit() else "'" + rid.replace("'", "''") + "'"
            mysql(f"UPDATE `{t}` SET `{col}` = UNHEX('{newhex}') WHERE `{pk}` = {rid_sql}")
            total_fixed += 1

    print(f"\n===== 修复完成，共更新 {total_fixed} 个字段 =====")


if __name__ == "__main__":
    sys.exit(main())
