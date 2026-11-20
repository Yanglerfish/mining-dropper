"""Build with the libraries already installed by Prism Launcher. Requires Python 3.9+ and JDK 25."""
import argparse
import json
import os
import subprocess
import tempfile
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent
NAME = "MiningDropper-1.2.3-mc26.3.jar"


def library_path(prism, coordinate):
    group, name, version, *classifier = coordinate.split(":")
    filename = "-".join([name, version, *classifier]) + ".jar"
    return prism / "libraries" / group.replace(".", "/") / name / version / filename


def libraries(prism):
    result = []
    for uid, version in [("net.minecraft", "26.3"), ("net.fabricmc.fabric-loader", "0.19.5"),
                         ("org.lwjgl3", "3.4.3")]:
        meta = json.loads((prism / "meta" / uid / (version + ".json")).read_text(encoding="utf-8"))
        for lib in meta["libraries"]:
            jar = library_path(prism, lib["name"])
            if jar.is_file():
                result.append(jar)
    game = library_path(prism, "com.mojang:minecraft:26.3:client")
    loader = library_path(prism, "net.fabricmc:fabric-loader:0.19.5")
    return list(dict.fromkeys([game, loader, *result]))


def argfile(path, args):
    path.write_text("\n".join('"' + str(a).replace("\\", "/") + '"' for a in args), encoding="utf-8")


def build(prism, instance, jdk, output):
    mods = instance / "minecraft" / "mods"
    api = mods / "fabric-api-0.165.2+26.3.jar"
    modmenu = mods / "modmenu-21.0.0.jar"
    with tempfile.TemporaryDirectory() as work:
        work = Path(work)
        cp = libraries(prism) + [modmenu]
        with zipfile.ZipFile(api) as archive:
            for entry in archive.namelist():
                if entry.startswith("META-INF/jars/") and entry.endswith(".jar"):
                    target = work / Path(entry).name
                    target.write_bytes(archive.read(entry))
                    cp.append(target)
        classes = work / "classes"
        classes.mkdir()
        sources = sorted((ROOT / "src/main/java").rglob("*.java"))
        args = work / "javac.args"
        argfile(args, ["--release", "25", "-encoding", "UTF-8", "-proc:none", "-classpath",
                       os.pathsep.join(map(str, cp)), "-d", classes, *sources])
        compiler = jdk / "bin" / ("javac.exe" if os.name == "nt" else "javac")
        subprocess.run([str(compiler), "@" + str(args)], check=True)
        output.mkdir(parents=True, exist_ok=True)
        out = output / NAME
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as archive:
            for folder in [classes, ROOT / "src/main/resources"]:
                for f in sorted(folder.rglob("*")):
                    if f.is_file():
                        archive.write(f, f.relative_to(folder).as_posix())
            for f in [ROOT / "build.py", *(ROOT / "src").rglob("*")]:
                if f.is_file():
                    archive.write(f, "META-INF/source/" + f.relative_to(ROOT).as_posix())
        print(out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--prism", type=Path, required=True)
    parser.add_argument("--instance", default="Build and Mine")
    parser.add_argument("--jdk", type=Path, help="Defaults to Prism's java-runtime-epsilon")
    parser.add_argument("--output", type=Path, default=ROOT / "dist")
    args = parser.parse_args()
    prism = args.prism.resolve()
    build(prism, prism / "instances" / args.instance, args.jdk or prism / "java/java-runtime-epsilon",
          args.output.resolve())
