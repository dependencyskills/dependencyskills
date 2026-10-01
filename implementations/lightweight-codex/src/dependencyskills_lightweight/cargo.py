"""A Cargo project and the crates it depends on, from Cargo's registry sources (RAD-0075, RAD-0079).

A crate ships its skills as `skills/<name>/SKILL.md` at the crate root; `cargo package` includes every
file unless `include` or `exclude` says otherwise, and once a build has run the crate is unpacked in
`<CARGO_HOME>/registry/src/<registry>/<name>-<version>/`. What the project may read is what its
`Cargo.toml` declares — dependencies, dev and build, per target too — at the version `Cargo.lock`
resolved; a `path` dependency is read where it is.
"""

import os
import tomllib
from pathlib import Path

from .packages import Package, mtimes

NAME = "cargo"
DECLARING = ("dependencies", "dev-dependencies", "build-dependencies")


def registry_sources():
    home = Path(os.environ["CARGO_HOME"]) if os.environ.get("CARGO_HOME") else Path.home() / ".cargo"
    base = home / "registry" / "src"
    return sorted(p for p in base.iterdir() if p.is_dir()) if base.is_dir() else []


def _toml(path):
    try:
        with open(path, "rb") as file:
            return tomllib.load(file)
    except (OSError, tomllib.TOMLDecodeError):
        return {}


def is_project(directory):
    return (Path(directory) / "Cargo.toml").is_file() and (Path(directory) / "Cargo.lock").is_file()


def stamp(directory):
    return mtimes(directory, ("Cargo.toml", "Cargo.lock"))


def _declared(directory):
    """{crate name: local path or None} for every dependency Cargo.toml declares, renamed ones by their real name."""
    manifest = _toml(Path(directory) / "Cargo.toml")
    tables = [manifest.get(field, {}) for field in DECLARING]
    tables += [target.get(field, {}) for target in manifest.get("target", {}).values() for field in DECLARING]
    tables.append(manifest.get("workspace", {}).get("dependencies", {}))
    found = {}
    for table in tables:
        for key, spec in table.items():
            name = spec.get("package", key) if isinstance(spec, dict) else key
            found[name] = spec.get("path") if isinstance(spec, dict) else None
    return found


def _locked(directory):
    """(name, version) of every package Cargo.lock resolved from a registry."""
    lock = _toml(Path(directory) / "Cargo.lock")
    return [(p["name"], p["version"]) for p in lock.get("package", [])
            if "name" in p and "version" in p and str(p.get("source", "")).startswith(("registry+", "sparse+"))]


def _unpacked(name, version):
    for registry in registry_sources():
        root = registry / f"{name}-{version}"
        if root.is_dir():
            return root
    return None


def _package(root, name, version):
    description = _toml(root / "Cargo.toml").get("package", {}).get("description", "")
    return Package(f"cargo:{name}:{version}", root, " ".join(str(description).split()))


def installed(directory):
    declared = _declared(directory)
    found = []
    for name, version in _locked(directory):
        if name in declared and declared[name] is None:
            root = _unpacked(name, version)
            if root:
                found.append(_package(root, name, version))
    for name, path in declared.items():
        if path:
            root = (Path(directory) / path).resolve()
            version = _toml(root / "Cargo.toml").get("package", {}).get("version", "0.0.0")
            if root.is_dir():
                found.append(_package(root, name, str(version)))
    return found


def everything(directory):
    """Every registry crate Cargo.lock resolved and a build has unpacked: what is on this machine for this project."""
    found = []
    for name, version in _locked(directory):
        root = _unpacked(name, version)
        if root:
            found.append(_package(root, name, version))
    return found


def manifest(directory):
    """(library, version, extra) for the crate whose root is `directory`, for its author; or None."""
    package = _toml(Path(directory) / "Cargo.toml").get("package", {})
    if not isinstance(package.get("name"), str):
        return None
    version = package.get("version")
    return f"cargo:{package['name']}", version if isinstance(version, str) else None, package
