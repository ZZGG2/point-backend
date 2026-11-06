.PHONY: local-up local-down run-monolith run-event test

local-up:
	docker compose up -d

local-down:
	docker compose down

run-monolith:
	./gradlew :monolith:bootRun

run-event:
	./gradlew :event-service:bootRun

test:
	./gradlew test
