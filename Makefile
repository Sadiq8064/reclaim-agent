.PHONY: up down build test run demo eval video-demo verify-audit tunnel clean

SHELL := /bin/bash

# Default target
all: build test

up:
	docker-compose up -d
	@echo "Waiting for services to become healthy..."
	@sleep 5

down:
	docker-compose down -v

build:
	mvn clean package -DskipTests

test:
	mvn test

run:
	mvn -pl reclaim-app spring-boot:run

demo: up
	@echo "===================================================="
	@echo "  RECLAIM - LIVE AUTONOMOUS REVENUE RECOVERY DEMO   "
	@echo "===================================================="
	@mvn -pl reclaim-replay exec:java -Dexec.mainClass="dev.reclaim.replay.EventReplayer" -Dexec.args="--mode=demo"

eval:
	@echo "===================================================="
	@echo "  RECLAIM - 4-ARM EVALUATION HARNESS (300 CASES)    "
	@echo "===================================================="
	@mvn -pl reclaim-eval exec:java -Dexec.mainClass="dev.reclaim.eval.ReportGenerator"

video-demo:
	@echo "===================================================="
	@echo "  RECLAIM - SLOW-PACED VIDEO RECORDING DEMO LOOP    "
	@echo "===================================================="
	@mvn -pl reclaim-replay exec:java -Dexec.mainClass="dev.reclaim.replay.EventReplayer" -Dexec.args="--mode=video --delay=1500"

verify-audit:
	@curl -s http://localhost:8080/api/audit/verify | jq .

tunnel:
	@echo "Starting Cloudflare quick tunnel for Razorpay webhook ingress..."
	@cloudflared tunnel --url http://localhost:8080

clean:
	mvn clean
	rm -rf target results/run-*.json results/*.local.json
