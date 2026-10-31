# Shared settings, sourced by the other scripts, so they run on Linux, macOS
# and Windows (Git Bash).
#   PY      a Python 3 interpreter ("python3" is a Store stub on some Windows PCs)
#   cp_join  joins paths into a Java classpath with the platform's separator
PY=python3
"$PY" -c '' > /dev/null 2>&1 || PY=python

cp_join() {
  local sep=":" out="" p
  case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=";" ;; esac
  for p in "$@"; do
    command -v cygpath > /dev/null 2>&1 && p="$(cygpath -w "$p")"
    out="${out:+$out$sep}$p"
  done
  printf '%s' "$out"
}
