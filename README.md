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

It instantly finds specs/authentication/03-tasks.md, appends your global AGENTS.md rules, formats
them cleanly, copies it directly into your clipboard, and you're ready to paste straight into your
AI terminal context.