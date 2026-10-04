#!/bin/sh
set -eu

project_dir="${CODEGRAPH_HOME_DIR}/project"
mkdir -p "${project_dir}"

if [ -f "${CODEGRAPH_SOURCE_DIR}/slow.marker" ]; then
  sleep 2
else
  sleep 0.1
fi

printf 'repo=%s\ncommit=%s\n' "${CODEGRAPH_REPOSITORY_ID}" "${CODEGRAPH_COMMIT_SHA}" > "${project_dir}/metadata.txt"
