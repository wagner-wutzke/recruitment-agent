## Folder Architecture Result

When you run `./sdd-multi.sh init authentication` and `./sdd-multi.sh init payment-billing`, your
repository clean structure scales dynamically like this:

```textmate
├── AGENTS.md                  # Global orchestrator file
├── sdd-multi.sh               # The multi-directory script
├── specs/
├──── authentication/          # Feature Suite A
│     ├── 01-requirements.md
│     ├── 02-design.md
│     ├── 03-tasks.md
│     └── prompts.md
├──── payment-billing/          # Feature Suite B
│     ├── 01-requirements.md
│     ├── 02-design.md
│     └── 03-tasks.md
└── prompts.md
```

## Day-to-Day Workflow Usage Example

When starting your coding cycle for the day on your authentication system's tasks:

```bash
./sdd-multi.sh bundle authentication 3
```

```bash
./sdd-multi.sh bundle authentication 1   # 
./sdd-multi.sh bundle authentication 2   # 
./sdd-multi.sh bundle authentication 3   #
./sdd-multi.sh bundle authentication 4   #
```

It instantly finds specs/authentication/03-tasks.md, appends your global AGENTS.md rules, formats
them cleanly, copies it directly into your clipboard, and you're ready to paste straight into your
AI terminal context.

## MongoDB Docker Container
Run this command on terminal:
```bash
docker run -d --rm \
  --name mongodb-local \
  -p 27017:27017 \
  -v ./data/mongodb:/data/mongodb \
  -e MONGO_INITDB_ROOT_USERNAME=admin \
  -e MONGO_INITDB_ROOT_PASSWORD=secret_password \
  -e GLIBC_TUNABLES=glibc.pthread.rseq=1 \
  mongo:latest
```

Set `MONGODB_URI` for the application using the credentials configured for the container, for
example: `mongodb://<username>:<password>@localhost:27017/recruitment_agent?authSource=admin`.
Resume metadata is stored in the MongoDB `resumes` collection; no relational database is used.

Stop, start and delete the container later:
```bash
docker stop mongodb-server

docker start mongodb-server

docker rm mongodb-server
```

---

## ChromaDB Vector Store
Run as Docker container:
```bash
docker run -d --rm \
--name chromadb \
-p 8000:8000 \
-v ./data/chroma:/chroma/chroma \
-e ANONYMIZED_TELEMETRY=False \
chromadb/chroma:latest
```
