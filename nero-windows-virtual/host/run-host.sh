#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "Chưa có .env. Sao chép .env.example thành .env và điền đường dẫn trước."
  exit 1
fi

python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
exec uvicorn server:app --host 0.0.0.0 --port 8765
