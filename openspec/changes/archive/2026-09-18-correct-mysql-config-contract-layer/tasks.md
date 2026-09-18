## 1. Correct contract ownership

- [x] 1.1 Move `MysqlConnectionSettings` from the shared `types/config` package to `infrastructure/mysql` and verify no production or test import still points to the old package.
- [x] 1.2 Move `MysqlConnectionSettingsRegistry` to the same Infrastructure technical package, correct its ownership documentation, and verify the Spring bean wiring remains unchanged.
- [x] 1.3 Keep `MysqlRuntimeSettings` in `types/config`, update package-level documentation to distinguish the neutral App Config bridge from Infrastructure-only contracts, and verify `MysqlConnectionProperties` remains its sole production implementation.

## 2. Record and verify the architecture boundary

- [x] 2.1 Update `docs/architecture/README.md` with the observed package-boundary error, the ownership rule, and the allowed App Config-to-Infrastructure contract direction.
- [x] 2.2 Add a change evidence note documenting the before/after ownership and the non-goals (no DDL, Mapper, Domain API, or MCP behavior changes), then verify the evidence is linked from the change artifacts.
- [x] 2.3 Run repository-wide reference checks, XML validation, `git diff --check`, and the available focused test/build commands; verify no dependency cycle or runtime contract change is introduced. Maven is unavailable in the current environment, so full Maven tests remain a review follow-up.
