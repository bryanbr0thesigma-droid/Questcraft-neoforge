"""Downloads Minecraft 26.3 the way the official launcher lays it out (versions/, libraries/ for
Linux x86_64) plus the server jar, to compile Horizon's exporters against. For this build machine
only: nothing here is redistributed.

usage: python3 -I mcfetch.py OUT_DIR"""
import hashlib, json, os, sys, time, urllib.request

out = sys.argv[1]
VERSION = "26.3"


def get(url, tries=6):
    for i in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=120) as r:
                return r.read()
        except Exception as e:  # 429s and resets: wait and try again
            if i == tries - 1:
                raise
            time.sleep(3 * (i + 1))


def fetch(url, path, sha1):
    if os.path.isfile(path) and hashlib.sha1(open(path, "rb").read()).hexdigest() == sha1:
        return
    data = get(url)
    if hashlib.sha1(data).hexdigest() != sha1:
        raise SystemExit("checksum mismatch: " + url)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)


manifest = json.loads(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"))
entry = next(v for v in manifest["versions"] if v["id"] == VERSION)
vdir = os.path.join(out, "versions", VERSION)
fetch(entry["url"], os.path.join(vdir, VERSION + ".json"), entry["sha1"])
info = json.load(open(os.path.join(vdir, VERSION + ".json")))
fetch(info["downloads"]["client"]["url"], os.path.join(vdir, VERSION + ".jar"), info["downloads"]["client"]["sha1"])
fetch(info["downloads"]["server"]["url"], os.path.join(out, "server.jar"), info["downloads"]["server"]["sha1"])


def allowed(lib):
    rules = lib.get("rules")
    if not rules:
        return True
    ok = False
    for r in rules:
        name = r.get("os", {}).get("name")
        if name is None or name == "linux":
            ok = r["action"] == "allow"
    return ok


for lib in info["libraries"]:
    art = lib.get("downloads", {}).get("artifact")
    if not art or not allowed(lib):
        continue
    classifier = lib["name"].split(":")[3] if lib["name"].count(":") >= 3 else ""
    if any(classifier.endswith(s) for s in ("arm64", "aarch64", "aarch_64")):
        continue
    fetch(art["url"], os.path.join(out, "libraries", art["path"]), art["sha1"])
print("ok", out)
