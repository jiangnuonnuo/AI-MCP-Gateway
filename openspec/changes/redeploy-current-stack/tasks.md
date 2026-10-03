## 1. OpenSpec and deployment contract

- [x] 1.1 Add the full-stack deployment script and configuration contract; verify `bash -n` and `openspec validate --changes redeploy-current-stack` pass
- [x] 1.2 Add migration directory/ledger rules and operator documentation; verify tracked files contain no credentials or secret values

## 2. Database safety

- [x] 2.1 Implement pre-update MySQL logical backup using the running MySQL container; verify the backup file is created with mode 600 and the database is not dropped
- [x] 2.2 Implement ordered, idempotent migration discovery and ledger recording; verify already-applied migrations are skipped and a failed migration stops the update

## 3. Frontend and backend replacement

- [x] 3.1 Stage and replace the current repository frontend in the existing bind mount, reload the existing Nginx container, and verify the served asset fingerprint
- [x] 3.2 Build the current backend image and replace only the gateway container using captured runtime/network metadata; verify MySQL and Redis container IDs remain unchanged
- [x] 3.3 Add health probes for login, session, and an application API; verify failed probes restore the prior gateway container

## 4. Operations and verification

- [x] 4.1 Add status and rollback operations for the latest full-stack snapshot; verify repeated updates do not accumulate duplicate application or Nginx containers
- [x] 4.2 Run local static checks and execute the full update on the target host; verify frontend, backend, database tables, MySQL, Redis, and Nginx after deployment
