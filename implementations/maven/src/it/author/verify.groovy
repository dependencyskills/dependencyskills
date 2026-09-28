import java.util.zip.ZipFile

def entries = { name -> new ZipFile(new File(basedir, "target/" + name)).withCloseable { z -> z.entries().collect { it.name } } }
def sources = entries("acme-text-1.0-sources.jar")
def classes = entries("acme-text-1.0.jar")
def skill = "skills/com-example-acme-acme-text/"

// The sources jar carries the skill under the coordinate's name, with its references, and never scripts/.
assert sources.contains(skill + "SKILL.md") : sources
assert sources.contains(skill + "references/usage.md") : sources
assert !sources.any { it.contains("scripts/") } : sources
assert sources.contains("com/example/acme/AcmeText.java") : sources
// The main jar is untouched.
assert !classes.any { it.startsWith("skills/") } : classes

def log = new File(basedir, "build.log").text
assert log.contains("scripts/ directory is not packaged") : "the check warns about scripts/"
assert !log.contains("`name` is") : "a correctly named skill draws no name warning"

// The author's agent skill, recorded in a lock file that names nothing on this machine.
assert new File(basedir, ".agents/skills/to-library-skill/SKILL.md").isFile()
assert new File(basedir, ".agents/skills/to-library-skill/references/per-language.md").isFile()
def lock = new File(basedir, "dependencyskills-lock.json").text
assert lock.contains('".agents/skills/to-library-skill"') : lock
assert !lock.contains(basedir.absolutePath) : lock
return true
