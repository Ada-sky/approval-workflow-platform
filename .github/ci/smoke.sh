#!/usr/bin/env bash
# Only GitHub-hosted runners may operate this disposable project.
set -euo pipefail
[[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]] || { echo 'GitHub-hosted CI required'; exit 1; }
[[ "${GITHUB_RUN_ID:-}" =~ ^[0-9]+$ && "${GITHUB_RUN_ATTEMPT:-}" =~ ^[0-9]+$ && "${GITHUB_SHA:-}" =~ ^[0-9a-f]{40}$ ]] || exit 1
[[ -n "${RUNNER_TEMP:-}" && -n "${GITHUB_WORKSPACE:-}" ]] || exit 1
cd "$GITHUB_WORKSPACE"
project="hpoa-ci-${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}"
directory="$RUNNER_TEMP/$project"
env_file="$directory/credentials.env"
compose=(docker compose --project-name "$project" --env-file "$env_file" -f compose.yaml -f .github/ci/compose.smoke.yaml)

check_resources() {
  python3 - "$project" <<'PY'
import json, subprocess, sys
project = sys.argv[1]
for kind, name in [('volume', project + '_mysql-data'), ('network', project + '_application')]:
    result = subprocess.run(['docker', kind, 'inspect', name], capture_output=True, text=True)
    if result.returncode == 0:
        resource = json.loads(result.stdout)[0]
        labels = resource.get('Labels') or {}
        if labels.get('com.docker.compose.project') != project:
            raise SystemExit('Unexpected resource ownership; cleanup refused')
PY
}

if [[ "${1:-}" == cleanup ]]; then
  # If preparation never completed, no Compose resources were started.
  if [[ ! -f "$env_file" ]]; then exit 0; fi
  check_resources
  "${compose[@]}" down --volumes --remove-orphans --timeout 30
  rm -- "$env_file"
  exit 0
fi
[[ "${1:-}" == run ]] || exit 1
[[ ! -e "$directory" ]] || { echo 'CI temporary directory already exists'; exit 1; }
for resource in "$project"; do
  [[ -z "$(docker ps -aq --filter "label=com.docker.compose.project=$resource")" ]] || exit 1
done
if docker volume inspect "${project}_mysql-data" >/dev/null 2>&1 || docker network inspect "${project}_application" >/dev/null 2>&1; then
  echo 'CI resources already exist; refusing reuse'; exit 1
fi
umask 077
mkdir "$directory"
python3 - "$env_file" <<'PY'
import pathlib, secrets, sys
values = {key: secrets.token_hex(32) for key in ['DOCKER_DB_PASSWORD', 'DOCKER_ROOT_PASSWORD']}
for value in values.values():
    print('::add-mask::' + value)
pathlib.Path(sys.argv[1]).write_text(''.join(key + '=' + value + '\n' for key, value in values.items()))
PY
(cd image-artifact && sha256sum --check SHA256SUMS)
gzip -dc image-artifact/image.tar.gz | docker load
[[ "$(docker image inspect --format '{{.Id}}' "hpoa-backend:sha-$GITHUB_SHA")" == "$(cat image-artifact/image-id.txt)" ]]

diagnostics() {
  status=$?
  if [[ "$status" != 0 ]]; then
    "${compose[@]}" ps --all || true
    # Capture logs and redact generated credentials before writing diagnostics.
    python3 - "$env_file" "$project" <<'PY' || true
import pathlib, subprocess, sys
result = subprocess.run(['docker', 'compose', '--project-name', sys.argv[2], '--env-file', sys.argv[1], '-f', 'compose.yaml', '-f', '.github/ci/compose.smoke.yaml', 'logs', '--no-color', '--tail', '120', 'db', 'initialize', 'backend'], capture_output=True, text=True)
text = result.stdout + result.stderr
for line in pathlib.Path(sys.argv[1]).read_text().splitlines():
    text = text.replace(line.split('=', 1)[1], '[REDACTED]')
print(text)
PY
  fi
  exit "$status"
}
trap diagnostics EXIT
"${compose[@]}" config --format json | python3 -c '
import json,re,sys
c=json.load(sys.stdin); db=c["services"]["db"]
assert re.fullmatch(r"hpoa-ci-[0-9]+-[0-9]+",c["name"])
assert c["volumes"]["mysql-data"]["name"] == c["name"] + "_mysql-data"
assert c["networks"]["application"]["name"] == c["name"] + "_application"
assert not c["networks"]["application"].get("external",False)
assert not db.get("ports")
assert len(db["volumes"]) == 1
v=db["volumes"][0]
assert v["type"] == "volume" and v["source"] == "mysql-data" and v["target"] == "/var/lib/mysql"
assert not c["volumes"]["mysql-data"].get("external",False)
print("Compose isolation checks passed")'
"${compose[@]}" up -d --wait --wait-timeout 180 db
# Foreground one-shot exit must succeed before any normal backend startup.
"${compose[@]}" --profile setup run --rm --no-deps --no-build initialize

"${compose[@]}" exec -T db sh -c 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE" -N -B' <<'SQL' > "$directory/schema.txt"
SELECT DATABASE(),CURRENT_USER();
SELECT version,success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;
SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND LEFT(table_name,2)='t_' ORDER BY table_name;
SELECT column_type,is_nullable,column_default FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='t_holiday_apply' AND column_name='applicant_archived';
SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='schema.version';
SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ACT_HI_IDENTITYLINK' AND column_name='DETAILS_';
SELECT KEY_ FROM ACT_RE_PROCDEF;
SQL
python3 - "$directory/schema.txt" <<'PY'
import pathlib, re, sys
rows = pathlib.Path(sys.argv[1]).read_text().splitlines()
assert rows.pop(0) == 'hpoa_docker\thpoa_docker@%'
migrations = pathlib.Path('backend/src/main/resources/db/migration/application')
versions = sorted((re.match(r'V(\d+)__', p.name).group(1) for p in migrations.glob('V*__*.sql')), key=int)
assert rows[:len(versions)] == [version + '\t1' for version in versions], 'Migration state mismatch'
del rows[:len(versions)]
tables = sorted(re.findall(r'CREATE TABLE `([^`]+)`', (migrations / 'V1__create_application_schema.sql').read_text()))
assert len(tables) == 12 and rows[:12] == tables, 'Application table inventory mismatch'
del rows[:12]
assert rows == ['tinyint(1)\tNO\t0', '8.1.0', 'longblob', 'hr_employee_holiday'], 'Application/Activiti schema mismatch'
print('Flyway migrations, application tables and Activiti/BPMN checks passed')
PY
"${compose[@]}" up -d --no-deps --no-build --wait --wait-timeout 180 backend
python3 - "$project" <<'PY'
import json, subprocess, sys
container = sys.argv[1] + '-backend-1'
def get(path):
    result = subprocess.run(['docker', 'exec', container, 'curl', '--silent', '--show-error', '--max-time', '20', '--write-out', '\n%{http_code}', 'http://127.0.0.1:8080' + path], capture_output=True, text=True, check=True)
    body, status = result.stdout.rsplit('\n', 1)
    return body, status
body, status = get('/api/auth/csrf')
assert status == '200'
csrf = json.loads(body)
assert all(isinstance(csrf.get(key), str) and csrf[key] for key in ['token', 'headerName', 'parameterName'])
assert get('/api/auth/me')[1] == '401'
print('Backend HTTP CSRF bootstrap and unauthenticated protection passed')
PY
