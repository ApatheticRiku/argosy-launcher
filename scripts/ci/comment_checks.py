"""Argosy comment checks shared by the CI smell check and the Claude smell guard: KDoc
discipline and the TODO/FIXME/STOPSHIP ban inside block comments.

Callers pass the file as a list of lines and a `touched(index)` predicate naming which
lines are new, so only added blocks are judged. A None entry in `lines` is a line the
caller cannot see; a KDoc whose declaration falls on one skips the declaration checks."""

import re

PATHS = ["app/src/main/**/*.kt"]

UNKNOWN = object()

DECL_RE = re.compile(
    r"^\s*(?:(?P<vis>public|internal|private|protected)\s+)?"
    r"(?:(?:suspend|inline|noinline|crossinline|open|override|abstract|final|sealed|data|value|"
    r"annotation|enum|external|infix|operator|tailrec|const|lateinit|companion|expect|actual|"
    r"tailrec|vararg|reified)\s+)*"
    r"(?P<kind>fun|val|var|class|object|interface|typealias)\s+"
    r"(?P<name>[A-Za-z_][A-Za-z0-9_]*)"
)

NARRATIVE_TELLS = [
    "used to", "previously", "which is why", "would have", "turned out",
    "meant that", "no longer", "historically", "the old", "before this",
    "the point is", "worse than", "better than", "the fix", "we", "our",
    "it is worth", "note that", "in practice", "let", "used to be",
    "this used", "originally", "at one point", "for now", "as discussed",
    "temporarily", "TODO", "FIXME", "XXX", "HACK",
    "because", "rather than", "instead of", "so that", "in order to",
    "to avoid", "which means", "this ensures", "that way", "otherwise",
    "the reason", "not just", "on purpose", "deliberately", "intentionally",
]

NARRATIVE_RE = re.compile(
    "|".join(r"\b" + re.escape(t).replace(r"\ ", r"\s+") + r"\b" for t in NARRATIVE_TELLS),
    re.IGNORECASE,
)

MARKER_RE = re.compile(r"\b(?:TODO|FIXME|STOPSHIP)\b")

STOPWORDS = {
    "a", "an", "the", "of", "for", "to", "in", "on", "is", "are", "and", "or",
    "that", "this", "it", "its", "as", "by", "with", "from", "at", "be", "was",
    "one", "each", "every", "all", "any", "when", "which", "what", "how",
    "whether", "there", "has", "have", "had", "does", "do", "not", "no",
    "only", "ever", "never", "if", "then", "else", "than", "so", "but",
    "into", "over", "under", "up", "down", "out", "off", "per", "via", "also",
    "just", "still", "already", "may", "can", "will", "would", "should",
    "must", "here", "these", "those", "them", "they", "some", "such", "same",
}

WORD_RE = re.compile(r"[A-Za-z]+")

RULES = {
    "kdoc-not-on-declaration": (
        "KDoc that does not sit above a declaration",
        "A KDoc that does not sit directly above a declaration is an inline comment "
        "wearing a docblock. Delete it.",
    ),
    "kdoc-on-non-public": (
        "KDoc on a private, protected or internal declaration",
        "KDoc is for non-obvious PUBLIC contracts. A private declaration explains itself "
        "in code or needs a better name. Delete it.",
    ),
    "kdoc-narrative": (
        "KDoc carrying rationale or history",
        "KDoc states WHAT the declaration is, nothing else. Rationale, history, and "
        "what the code used to do belong in the commit message.",
    ),
    "kdoc-too-long": (
        "KDoc longer than four lines",
        "A KDoc past four lines is prose. Say what it is in one or two sentences or "
        "delete it.",
    ),
    "kdoc-restates-name": (
        "KDoc that restates the declaration name",
        "The declaration already says this. Delete the KDoc.",
    ),
    "comment-work-marker": (
        "TODO, FIXME or STOPSHIP inside a block comment",
        "Unfinished-work markers do not ship in source. Finish the work, or track it in an "
        "issue and delete the marker.",
    ),
}


def text_of(line):
    return "" if line is None else line


def split_identifier(name):
    parts = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name).replace("_", " ")
    return {w.lower() for w in WORD_RE.findall(parts)}


def kdoc_blocks(lines):
    blocks, i = [], 0
    while i < len(lines):
        if text_of(lines[i]).lstrip().startswith("/**"):
            start = i
            body = []
            while i < len(lines):
                current = text_of(lines[i])
                body.append(current)
                if "*/" in current and not (i == start and current.lstrip() == "/**"):
                    break
                if i > start and "*/" in current:
                    break
                i += 1
            blocks.append((start, i, body))
        i += 1
    return blocks


def documented_declaration(lines, end):
    j = end + 1
    while j < len(lines):
        if lines[j] is None:
            return UNKNOWN
        stripped = lines[j].strip()
        if not stripped or stripped.startswith("@"):
            j += 1
            continue
        return lines[j]
    return None


def finding(rule_id, snippet, index):
    return (rule_id, RULES[rule_id][1], snippet, index)


def kdoc_findings(lines, touched=None):
    out = []

    for start, end, body in kdoc_blocks(lines):
        if touched is not None and not any(touched(start + k) for k in range(len(body))):
            continue
        prose = " ".join(
            ln.strip().lstrip("/*").lstrip("*").strip() for ln in body
        ).replace("*/", " ").strip()
        content_lines = [
            ln for ln in body
            if ln.strip().strip("/*").strip("*").strip() and not ln.strip() in ("/**", "*/")
        ]

        decl = documented_declaration(lines, end)
        m = None
        if decl is None:
            out.append(finding("kdoc-not-on-declaration", prose[:110], start))
            continue
        if decl is not UNKNOWN:
            m = DECL_RE.match(decl)
            if not m:
                out.append(finding("kdoc-not-on-declaration", decl.strip()[:110], start))
                continue
            if m.group("vis") in ("private", "protected", "internal"):
                out.append(finding("kdoc-on-non-public", decl.strip()[:110], start))
                continue

        hits = sorted({h.group(0).lower() for h in NARRATIVE_RE.finditer(prose)})
        if hits:
            out.append(finding(
                "kdoc-narrative",
                "Found: {} | {}".format(", ".join(hits[:4]), prose[:90]),
                start,
            ))
            continue

        if len(content_lines) > 5:
            out.append(finding("kdoc-too-long", prose[:110], start))
            continue

        if m is None:
            continue
        doc_words = {w.lower() for w in WORD_RE.findall(prose)} - STOPWORDS
        name_words = split_identifier(m.group("name"))
        if doc_words and len(doc_words - name_words) <= 1:
            out.append(finding("kdoc-restates-name", prose[:110], start))

    return out


def block_comment_lines(lines):
    inside, flags = False, []
    for line in lines:
        stripped = text_of(line).strip()
        if inside:
            flags.append(True)
            if "*/" in stripped:
                inside = False
            continue
        if stripped.startswith("/*"):
            flags.append(True)
            inside = "*/" not in stripped[2:]
            continue
        flags.append(stripped.startswith("*") or bool(re.search(r"/\*.*\*/", stripped)))
    return flags


def marker_findings(lines, touched=None):
    out = []
    for index, in_comment in enumerate(block_comment_lines(lines)):
        if not in_comment or lines[index] is None:
            continue
        if touched is not None and not touched(index):
            continue
        if MARKER_RE.search(lines[index]):
            out.append(finding("comment-work-marker", lines[index].strip()[:110], index))
    return out


def comment_findings(lines, touched=None):
    return kdoc_findings(lines, touched) + marker_findings(lines, touched)
