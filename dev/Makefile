# Local dev targets (8.06). Run from dev/.
.PHONY: up down seed test doctor logs

up: doctor
	docker compose up -d --wait
	@echo "MySQL :3306, LocalStack :4566. Now run the api + worker with the 'local' profile."

down:
	docker compose down

# Seed = restart LocalStack init (idempotent) + api's Flyway V999 runs on next boot with the
# local profile (db/seed location).
seed:
	docker compose restart localstack

test:
	cd ../backend && gradle test --no-daemon
	cd ../frontend && npm test

doctor:
	@command -v docker >/dev/null || { echo "docker missing"; exit 1; }
	@docker compose version >/dev/null || { echo "docker compose v2 missing"; exit 1; }
	@command -v java >/dev/null && java -version 2>&1 | head -1 || echo "WARN: JDK 21 not on PATH"
	@command -v node >/dev/null && node --version || echo "WARN: Node 20+ not on PATH"
	@echo "doctor: OK"

logs:
	docker compose logs -f
