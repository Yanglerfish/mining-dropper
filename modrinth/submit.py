"""Publish the verified Mining Dropper release for unlisted Modrinth review."""

import argparse
import hashlib
import http.client
import json
import os
import sys
import uuid
from pathlib import Path


SOURCE = "https://github.com/Yanglerfish/mining-dropper"
JAR = "MiningDropper-1.2.3-mc26.3.jar"
EXPECTED = "f1d99908ef8e608bd9cf61f967d19a91c78ae6874a586a167db0c1e8a71c418d"
DISCLOSURE = {"type": "ai_content", "uses": ["code", "text"],
              "note": "The project owner specified and tested the features. Codex generated the implementation and the project description."}


def api(path, *, method="GET", data=None, media="application/json", missing_ok=False):
    headers = {"User-Agent": "Yanglerfish/mining-dropper/1.2.3 (" + SOURCE + ")"}
    if token := os.getenv("MODRINTH_TOKEN"):
        headers["Authorization"] = token
    if data is not None:
        headers["Content-Type"] = media
        if not isinstance(data, bytes):
            data = json.dumps(data).encode()
    conn = http.client.HTTPSConnection("api.modrinth.com", timeout=45)
    try:
        conn.request(method, path, body=data, headers=headers)
        reply = conn.getresponse()
        payload = reply.read()
        if reply.status == 404 and missing_ok:
            return None
        if not 200 <= reply.status < 300:
            raise RuntimeError(f"Modrinth {method} {path} returned {reply.status}: {payload[:1200].decode(errors='replace')}")
        return json.loads(payload) if payload.strip() else None
    finally:
        conn.close()


def multipart(metadata, jar):
    boundary = "MiningDropper" + uuid.uuid4().hex
    body = b"\r\n".join([
        ("--" + boundary).encode(),
        b'Content-Disposition: form-data; name="data"',
        b"Content-Type: application/json", b"", json.dumps(metadata).encode(),
        ("--" + boundary).encode(),
        f'Content-Disposition: form-data; name="jar"; filename="{JAR}"'.encode(),
        b"Content-Type: application/java-archive", b"", jar,
        ("--" + boundary + "--").encode(), b""])
    return body, "multipart/form-data; boundary=" + boundary


def release_data(root):
    jar = (root / "release" / JAR).read_bytes()
    if hashlib.sha256(jar).hexdigest() != EXPECTED:
        raise RuntimeError("The JAR differs from the verified GitHub release.")
    game_versions = api("/v2/tag/game_version")
    if not any(v["version"] == "26.3" for v in game_versions):
        raise RuntimeError("Modrinth does not currently list Minecraft 26.3.")
    if not any(c["name"] == "utility" and c["project_type"] == "mod" for c in api("/v2/tag/category")):
        raise RuntimeError("Modrinth does not currently list the utility category for mods.")
    dependencies = [{"project_id": api("/v2/project/" + slug)["id"], "dependency_type": kind}
                    for slug, kind in [("fabric-api", "required"), ("modmenu", "optional")]]
    version = {"file_parts": ["jar"], "primary_file": "jar", "version_number": "1.2.3",
               "name": "Mining Dropper 1.2.3 for Minecraft 26.3", "version_type": "release",
               "changelog": (root / "release/notes.md").read_text(encoding="utf-8"),
               "loaders": ["fabric"], "game_versions": ["26.3"], "featured": True,
               "dependencies": dependencies}
    project = {"title": "Mining Dropper", "project_type": "mod", "categories": ["utility"],
               "description": (root / "modrinth/summary.txt").read_text(encoding="utf-8").strip(),
               "body": (root / "modrinth/description.md").read_text(encoding="utf-8"),
               "client_side": "required", "server_side": "unsupported", "source_url": SOURCE,
               "issues_url": SOURCE + "/issues", "license_id": "LicenseRef-All-Rights-Reserved",
               "is_draft": True, "requested_status": "unlisted", "initial_versions": [version]}
    return project, jar


def find_or_create(user_id, metadata, jar):
    for slug in ("mining-dropper", "mining-dropper-yanglerfish"):
        project = api("/v2/project/" + slug, missing_ok=True)
        if project is None:
            data, media = multipart(dict(metadata, slug=slug), jar)
            return api("/v2/project", method="POST", data=data, media=media)
        if project.get("source_url") == SOURCE and project["title"] == metadata["title"]:
            members = api("/v2/project/" + project["id"] + "/members")
            if any(m["user"]["id"] == user_id and (m.get("is_owner") or m.get("role", "").lower() == "owner")
                   for m in members):
                return project
    raise RuntimeError("Both Modrinth project names belong to existing projects. No project was changed.")


def submit(project, jar):
    url = "https://modrinth.com/mod/" + project["slug"]
    print("Modrinth project:", url, flush=True)
    endpoint = "/project/" + project["id"]
    versions = api("/v2" + endpoint + "/version")
    digest = hashlib.sha512(jar).hexdigest()
    if not any(v["version_number"] == "1.2.3" and "26.3" in v["game_versions"] and "fabric" in v["loaders"]
               and any(f["filename"] == JAR and f["hashes"].get("sha512") == digest for f in v["files"])
               for v in versions):
        raise RuntimeError("The matching release was not found on this project. No review was requested.")
    disclosures_url = "/v3" + endpoint + "/disclosures"

    def has_disclosure():
        disclosures = api(disclosures_url)["disclosures"]
        return any(d["type"] == "ai_content" and {"code", "text"} <= set(d["uses"]) for d in disclosures)

    if project["status"] == "draft":
        if not has_disclosure():
            api(disclosures_url, method="PATCH", data={"set": [DISCLOSURE], "remove": []})
        if not has_disclosure():
            raise RuntimeError("Modrinth did not confirm the AI disclosures.")
        if project.get("requested_status") != "unlisted":
            api("/v2" + endpoint, method="PATCH", data={"requested_status": "unlisted"})
        if api("/v2" + endpoint).get("requested_status") != "unlisted":
            raise RuntimeError("Modrinth did not confirm the requested unlisted visibility.")
        nags = api("/v3" + endpoint + "/validate")["nags"]
        required = [n for n in nags if n.get("severity") == "required"]
        if required:
            raise RuntimeError("Modrinth requires these changes before review: " + json.dumps(required))
        api("/v2" + endpoint, method="PATCH", data={"status": "processing", "requested_status": "unlisted"})
    final = api("/v2" + endpoint)
    if not (final["status"] == "unlisted" or
            (final["status"] == "processing" and final.get("requested_status") == "unlisted")):
        raise RuntimeError("Modrinth reports project status " + final["status"] + ". No further changes made.")
    if not has_disclosure():
        raise RuntimeError("The published disclosure could not be verified.")
    print("Verified release file and AI disclosures. Modrinth status:", final["status"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check release inputs and public Modrinth metadata")
    args = parser.parse_args()
    metadata, jar = release_data(Path(__file__).resolve().parents[1])
    if args.check:
        print("Verified the release JAR, game version, category, and both dependency project IDs.")
        return
    if not os.getenv("MODRINTH_TOKEN"):
        raise RuntimeError("Set the MODRINTH_TOKEN Actions secret to submit for unlisted review.")
    user = api("/v2/user")
    project = find_or_create(user["id"], metadata, jar)
    submit(project, jar)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, http.client.HTTPException) as exc:
        sys.exit(str(exc))
