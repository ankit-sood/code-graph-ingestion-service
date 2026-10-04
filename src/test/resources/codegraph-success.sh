#!/bin/sh
set -eu

project_dir="${CODEGRAPH_HOME_DIR}/project"
mkdir -p "${project_dir}"

printf 'repo=%s\ncommit=%s\n' "${CODEGRAPH_REPOSITORY_ID}" "${CODEGRAPH_COMMIT_SHA}" > "${project_dir}/metadata.txt"
if [ -d "${CODEGRAPH_SOURCE_DIR}" ]; then
  find "${CODEGRAPH_SOURCE_DIR}" -type f | head -n 1 | while read -r f; do
    cp "$f" "${project_dir}/source-sample.txt"
  done
fi
