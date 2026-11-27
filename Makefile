.PHONY: local-up local-down run-core run-event test

local-up:
	docker compose up -d

local-down:
	docker compose down

run-core:
	./gradlew :core-service:bootRun

run-event:
	./gradlew :event-service:bootRun

test:
	./gradlew test
