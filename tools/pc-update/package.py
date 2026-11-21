"""Download the private FeuTimer web package selected by the release channel."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import tempfile
from zipfile import ZipFile

REPO = "ryoken99/FeuTimer"


def gh(*args):
    return subprocess.check_output(["gh", *args, "--repo", REPO], timeout=90)


def prepare(root, channel, current):
    if channel == "bootstrap":
        tag = "v1.3.5"
    elif channel == "stable":
        tag = json.loads(gh("release", "view", "--json", "tagName"))["tagName"]
    elif channel == "test":
        releases = json.loads(gh("release", "list", "--limit", "100", "--json", "tagName,isDraft,publishedAt"))
        tag = max((r for r in releases if not r["isDraft"]), key=lambda r: r["publishedAt"])["tagName"]
    else:
        raise ValueError("Canal desconhecido")
    if not re.fullmatch(r"v[0-9][A-Za-z0-9._-]{0,90}", tag):
        raise ValueError("Etiqueta de versão inválida")
    if tag == current:
        return {"tag": tag}
    release = json.loads(gh("release", "view", tag, "--json", "assets"))
    assets = [a for a in release["assets"] if a["name"].startswith("FeuTimer-web") and a["name"].endswith(".zip")]
    if len(assets) != 1:
        raise ValueError("A versão não contém um único pacote web")
    asset = assets[0]
    root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=root) as tmp:
        archive = Path(tmp) / "package.zip"
        gh("release", "download", tag, "-p", asset["name"], "-O", str(archive))
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        if asset.get("digest") != "sha256:" + digest:
            raise ValueError("O ficheiro não corresponde à versão publicada")
        dest = root / (tag + "-" + digest[:16])
        if not dest.exists():
            stage = Path(tmp) / "stage"
            with ZipFile(archive) as z:
                for name in z.namelist():
                    parts = PurePosixPath(name)
                    if parts.is_absolute() or ".." in parts.parts or ":" in name or chr(92) in name:
                        raise ValueError("Caminho inválido no pacote")
                z.extractall(stage)
            for name in ["web/index.html", "web/serve.cjs", "web/js/app.js"]:
                if not (stage / name).is_file():
                    raise ValueError("Pacote web incompleto")
            os.replace(stage, dest)
        return {"tag": tag, "dir": str(dest), "layout": (dest / "Abrir Layout Test.html").is_file()}


if __name__ == "__main__":
    print(json.dumps(prepare(Path(sys.argv[1]), sys.argv[2], sys.argv[3])))
