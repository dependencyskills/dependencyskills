def sbom = new File(basedir, "target/dependencyskills/bom.cdx.json").text
assert sbom.contains('"purl":"pkg:maven/com.example.acme/acme-text@1.0"') : sbom
assert sbom.contains('{"name":"dependencyskills:project","value":"com.example:acme-app"}') : sbom
assert sbom.contains('"bomFormat": "CycloneDX"') : sbom

// The librarian, in the cross-agent location and, asked for, a copy for Claude Code — never a link.
assert new File(basedir, ".agents/skills/librarian/SKILL.md").isFile()
def claude = new File(basedir, ".claude/skills/librarian")
assert claude.isDirectory() && !java.nio.file.Files.isSymbolicLink(claude.toPath())
def lock = new File(basedir, "dependencyskills-lock.json").text
assert lock.contains('".claude/skills/librarian"') : lock

// The library's sources jar is in the local repository, where the lookup finds its skill.
assert new File(localRepositoryPath, "com/example/acme/acme-text/1.0/acme-text-1.0-sources.jar").isFile()
return true
