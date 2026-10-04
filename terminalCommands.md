node --version
npm --version

npm install -g @astudioplus/codegraph-mcp

npm root -g
find "$(npm root -g)" -name 'codegraph-server*' -type f
/path/to/codegraph-server-darwin-arm64 --help

alias codegraph-server='/Users/a0s0f6i/.nvm/versions/node/v22.19.0/lib/node_modules/@astudioplus/codegraph-mcp/bin/codegraph-server-darwin-arm64'
codegraph-server --workspace .


HOME=/tmp/codegraph-home \                     
codegraph-server \
  --graph-only \
  --workspace /Users/a0s0f6i/Dev/Repositories/EBS/uds-shard-resolver \
  --run-tool codegraph_symbol_search \
  --tool-args '{"query":"UdsShardProperties"}'