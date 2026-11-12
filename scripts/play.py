#!/usr/bin/env python3
"""Small Google Play Developer API helper (no dependencies beyond openssl).

Usage: scripts/play.py check | upload <aab> <track> [status] | listing
Reads the service account from play-service-account.json (git-ignored).
"""
import base64, json, subprocess, sys, time, urllib.request, urllib.error, pathlib, tempfile, os

ROOT = pathlib.Path(__file__).resolve().parent.parent
PKG = "dev.nihal.edgereach"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/" + PKG
UP = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/" + PKG


def b64(b): return base64.urlsafe_b64encode(b).rstrip(b"=")


def token():
    sa = json.loads((ROOT / "play-service-account.json").read_text())
    now = int(time.time())
    claim = {"iss": sa["client_email"], "scope": "https://www.googleapis.com/auth/androidpublisher",
             "aud": sa["token_uri"], "iat": now, "exp": now + 3000}
    msg = b64(b'{"alg":"RS256","typ":"JWT"}') + b"." + b64(json.dumps(claim).encode())
    with tempfile.NamedTemporaryFile("w", delete=False) as f:
        f.write(sa["private_key"]); key = f.name
    try:
        sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", key], input=msg, capture_output=True, check=True).stdout
    finally:
        os.unlink(key)
    body = ("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + (msg + b"." + b64(sig)).decode()).encode()
    return json.load(urllib.request.urlopen(urllib.request.Request(sa["token_uri"], body)))["access_token"]


def call(tok, method, url, data=None, ctype="application/json"):
    if data is not None and not isinstance(data, bytes): data = json.dumps(data).encode()
    req = urllib.request.Request(url, data, method=method, headers={"Authorization": "Bearer " + tok, "Content-Type": ctype})
    try:
        r = urllib.request.urlopen(req, timeout=300).read()
        return json.loads(r) if r else {}
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {url.split('/applications/')[-1]} -> {e.code}: {e.read().decode()[:600]}")


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    tok = token()
    edit = call(tok, "POST", API + "/edits", {})["id"]
    E = f"{API}/edits/{edit}"
    if cmd == "check":
        print("access ok, edit", edit)
        print("tracks:", json.dumps(call(tok, "GET", E + "/tracks"))[:600])
        print("listings:", json.dumps(call(tok, "GET", E + "/listings"))[:300])
        call(tok, "DELETE", E)
        return
    if cmd == "upload":
        aab, track = sys.argv[2], sys.argv[3]
        status = sys.argv[4] if len(sys.argv) > 4 else "completed"
        b = call(tok, "POST", f"{UP}/edits/{edit}/bundles?uploadType=media", pathlib.Path(aab).read_bytes(), "application/octet-stream")
        print("uploaded versionCode", b["versionCode"])
        call(tok, "PUT", f"{E}/tracks/{track}", {"track": track, "releases": [{"versionCodes": [str(b["versionCode"])], "status": status}]})
    elif cmd == "listing":
        d = ROOT / "fastlane/metadata/android/en-US"
        call(tok, "PUT", E + "/listings/en-US", {"language": "en-US", "title": (d / "title.txt").read_text().strip(),
             "shortDescription": (d / "short_description.txt").read_text().strip(),
             "fullDescription": (d / "full_description.txt").read_text().strip()})
        for kind, name in (("icon", "icon.png"), ("featureGraphic", "featureGraphic.png")):
            call(tok, "DELETE", f"{E}/listings/en-US/{kind}")
            call(tok, "POST", f"{UP}/edits/{edit}/listings/en-US/{kind}?uploadType=media", (d / "images" / name).read_bytes(), "image/png")
        shots = sorted((d / "images/phoneScreenshots").glob("*.png")) if (d / "images/phoneScreenshots").exists() else []
        if shots:
            call(tok, "DELETE", f"{E}/listings/en-US/phoneScreenshots")
            for s in shots:
                call(tok, "POST", f"{UP}/edits/{edit}/listings/en-US/phoneScreenshots?uploadType=media", s.read_bytes(), "image/png")
        print("listing staged,", len(shots), "screenshots")
    print("committed:", call(tok, "POST", E + ":commit"))


main()
