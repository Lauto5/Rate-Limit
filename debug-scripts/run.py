#!/usr/bin/env python3

import argparse
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(ROOT)
REACTOR_POM = os.path.join(REPO, "rate-limit", "pom.xml")
MODULES = {
    "core": "rate-limit-core",
    "inmemory": "rate-limit-inmemory",
    "redis": "rate-limit-redis",
}
EXAMPLES = ["core-inmemory", "core-redis"]

DRY_RUN = False


def run(cmd, cwd=None):
    print("[run] " + " ".join(cmd), flush=True)
    if not DRY_RUN:
        subprocess.run(cmd, cwd=cwd, check=True)


def mvn_reactor(*args):
    run(["mvn", "-f", REACTOR_POM, *args], cwd=REPO)


def docker_available():
    if shutil.which("docker") is None:
        return False
    try:
        subprocess.run(["docker", "info"], stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL, check=True, timeout=15)
        return True
    except Exception:
        return False


def print_version(bin_name, *args):
    path = shutil.which(bin_name)
    if path is None:
        print(f"[env] {bin_name}: NO encontrado")
        return
    try:
        out = subprocess.run([bin_name, *args], capture_output=True,
                             text=True, timeout=60)
        first = (out.stdout or out.stderr).splitlines()[0].strip() if (out.stdout or out.stderr) else ""
        print(f"[env] {bin_name}: {first}")
    except Exception as e:
        print(f"[env] {bin_name}: error al consultar version ({e})")


def cmd_env(args):
    print_version("java", "-version")
    print_version("mvn", "-version")
    print_version("python3", "--version")
    print("[env] docker: " + ("disponible" if docker_available()
                              else "NO disponible (los tests de integracion Redis se saltaran/fallaran)"))


def cmd_status(args):
    run(["git", "status", "--short"], cwd=REPO)
    run(["git", "log", "--oneline", "-10"], cwd=REPO)


def cmd_compile(args):
    mvn_reactor("compile")
    for example in EXAMPLES:
        run(["mvn", "compile"], cwd=os.path.join(REPO, "examples", example))


def cmd_test(args):
    if args.module:
        mvn_reactor("test", "-pl", MODULES[args.module], "-am")
    else:
        mvn_reactor("test")


def cmd_verify(args):
    if not docker_available():
        print("[warn] Docker no esta disponible: los tests de integracion "
              "con Redis real no podran ejecutarse", flush=True)
    mvn_reactor("clean", "verify")


def cmd_install(args):
    mvn_reactor("install", "-DskipTests")


def cmd_examples(args):
    for example in EXAMPLES:
        run(["mvn", "clean", "package"],
            cwd=os.path.join(REPO, "examples", example))


def cmd_all(args):
    cmd_env(args)
    if not docker_available():
        print("[warn] Docker no esta disponible: los tests de integracion "
              "con Redis real no podran ejecutarse", flush=True)
    mvn_reactor("install", "-DskipTests")
    cmd_examples(args)
    mvn_reactor("clean", "verify")


def build_parser():
    parser = argparse.ArgumentParser(
        description="Herramienta de depuracion que ejecuta los comandos Maven/Git del "
                    "proyecto Rate Limit (reactor multi-modulo + ejemplos).",
    )
    parser.add_argument("--dry-run", action="store_true",
                        help="solo muestra los comandos, no los ejecuta")
    sub = parser.add_subparsers(dest="command", metavar="comando")

    def add(name, fn, help_text):
        p = sub.add_parser(name, help=help_text)
        p.set_defaults(handler=fn)
        return p

    add("env", cmd_env, "muestra las versiones de Java/Maven/Python y disponibilidad de Docker")
    add("status", cmd_status, "git status resumido + ultimos commits")
    add("compile", cmd_compile, "compila el reactor (core, inmemory, redis) y los ejemplos")
    p_test = add("test", cmd_test, "ejecuta los tests del reactor (o de un modulo con -m)")
    p_test.add_argument("-m", "--module", choices=sorted(MODULES),
                        help="modulo a testear: " + ", ".join(sorted(MODULES)))
    add("verify", cmd_verify, "mvn clean verify completo con todos los tests")
    add("install", cmd_install, "instala los modulos en el repositorio local de Maven (sin tests)")
    add("examples", cmd_examples, "compila y empaqueta los proyectos de ejemplo")
    add("all", cmd_all, "pipeline completa: env -> install -> examples -> clean verify")
    return parser


def main():
    global DRY_RUN
    parser = build_parser()
    args = parser.parse_args()
    DRY_RUN = args.dry_run
    if args.command is None:
        parser.print_help()
        return 0
    try:
        args.handler(args)
    except FileNotFoundError:
        print("mvn no encontrado en el PATH. Instala Maven o ajusta el PATH.", file=sys.stderr)
        return 1
    except subprocess.CalledProcessError as e:
        print(f"Comando fallo con codigo {e.returncode}: "
              + " ".join(e.cmd), file=sys.stderr)
        return e.returncode
    return 0


if __name__ == "__main__":
    sys.exit(main())