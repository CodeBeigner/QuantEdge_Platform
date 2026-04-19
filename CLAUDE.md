# QuantEdge Platform - Claude Code Guidelines

## Project Boundary (STRICT)

**All file operations (read, write, edit, delete, create) MUST be scoped to `/Users/abhinavunmesh/Desktop/QuantEdge_Platform/` and its subdirectories.**

- Do NOT read, write, edit, or create files outside this project directory.
- Do NOT run commands that modify files outside this directory (e.g., no `cd ~` and editing dotfiles, no touching other repos).
- Temporary files (if needed) should be created inside this project directory.
- Git operations should only target this repository.

**Exceptions:**
- Reading global config/docs for reference (e.g., checking installed tool versions) is acceptable.
- Installing dependencies via package managers (npm, pip, mvn) that write to system paths is acceptable.
- Running the project's own dev servers, Docker commands, and test suites is acceptable.

## Project Structure

- `QuantPlatformApplication/` - Java Spring Boot backend (port 8080)
- `frontend/` - React 19 + TypeScript + Vite frontend (port 3000)
- `ml-service/` - Python FastAPI ML service (port 5001)

## Tech Stack

- Backend: Java 21, Spring Boot 3.5, PostgreSQL 15, Redis 7, Flyway migrations
- Frontend: React 19, TypeScript 5.9, Vite 8, Tailwind CSS 4, Zustand 5
- ML: Python 3.9+, FastAPI, XGBoost, PyTorch (LSTM)
- Brokers: Paper (built-in), Alpaca (US equities), Delta Exchange (crypto derivatives)
- AI: Anthropic Claude for agent decision-making
