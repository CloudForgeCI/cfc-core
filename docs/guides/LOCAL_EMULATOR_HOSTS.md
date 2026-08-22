# Local Emulator Hostnames (`*.cloudforge.localhost`)

Friendly browser names for CloudForge apps on **MiniStack** or **LocalStack**. Both emulators publish ECS tasks on the Docker host at `127.0.0.1`, so one `/etc/hosts` block works for either target.

See also: [Local Emulator Quick Start](LOCAL_EMULATOR_QUICK_START.md) · [MiniStack Setup](../ministack/SETUP.md) · [LocalStack README](../localstack/README.md)

---

## Why this works for both emulators

| Fact | Implication |
|------|-------------|
| MiniStack and LocalStack both bind gateway **`:4566`** | Run **one** emulator at a time |
| App containers publish ports on the **Docker host** | Browser traffic goes to `127.0.0.1:<app-port>` |
| `/etc/hosts` maps **name → IP only** | You still include the **port** in the URL |

```text
  Browser
     │
     │  http://jenkins.cloudforge.localhost:8080
     ▼
  /etc/hosts  →  127.0.0.1
     │
     ▼
  Docker host port (ECS task)   ← MiniStack OR LocalStack
```

You do **not** need separate hosts entries per emulator. Switch emulator with Maven stop/start; keep the same URLs when host ports match.

---

## Recommended scheme

Use short **`*.localhost`** names (RFC 6761). Always type **`http://`** — Safari/Chrome often treat bare multi-label names as a **Google search**.

| Hostname | Typical port | Role |
|----------|--------------|------|
| `localstack.localhost` | 4566 | LocalStack gateway |
| `ministack.localhost` | 4566 | MiniStack gateway |
| `emulator.localhost` | 4566 | Shared alias for whichever emulator owns `:4566` |
| `stackport.localhost` | 8888 | StackPort (simulated AWS console) |
| `nginx.localhost` | 80 | nginx edge status page |
| `manager.localhost` | 1958 | CloudForge Manager |
| `jenkins.localhost` | 8080 | Jenkins |
| `grafana.localhost` | 3000 | Grafana |
| `prometheus.localhost` | 9090 | Prometheus |
| `vault.localhost` | 8200 | Vault |
| `nexus.localhost` | 8081 | Nexus |
| `sonarqube.localhost` | 9000 | SonarQube |
| `redis.localhost` | 6379 | Redis (TCP) |
| `postgres.localhost` | 5432 | PostgreSQL (TCP) |

With the **nginx edge** running:

```bash
open "http://nginx.localhost/"
open "http://localstack.localhost/"
open "http://stackport.localhost/"
```

Longer aliases (`*.cloudforge.localhost`) resolve to the same edge routes. Do **not** use `*.local` (macOS mDNS hang).

**Optional app aliases** (same IPs — useful when docs mention emulator-specific names):

| Alias | Same as |
|-------|---------|
| `jenkins.ministack.local` | Prefer `jenkins.localhost` instead |
| `jenkins.localstack.local` | Prefer `jenkins.localhost` instead |

---

## One-time setup (macOS / Linux)

### Option A — helper script (recommended)

From the repository root:

```bash
./scripts/setup-cloudforge-local-hosts.sh
```

This installs the marked block from [`docs/guides/examples/cloudforge.localhost.hosts`](examples/cloudforge.localhost.hosts) into `/etc/hosts` (prompts for `sudo`). Re-run safely — it replaces the previous CloudForge block (including legacy `*.cfc.local` / `*.cloudforge.local` blocks).

Uninstall:

```bash
./scripts/setup-cloudforge-local-hosts.sh --remove
```

### Option B — manual copy

```bash
# Preview
cat docs/guides/examples/cloudforge.localhost.hosts

# Append (once)
sudo sh -c 'cat docs/guides/examples/cloudforge.localhost.hosts >> /etc/hosts'
```

### Verify

```bash
ping -c 1 jenkins.cloudforge.localhost
# should resolve to 127.0.0.1

curl -s -o /dev/null -w "%{http_code}\n" http://manager.cloudforge.localhost:1958/api/v1/health
```

---

## Day-to-day usage

1. Start **one** emulator from `InteractiveDeployer --platform` — it also starts StackPort + nginx edge.
2. Deploy an app (Interactive Deployer option 6** or **8**).
3. Open the friendly URL with the **app port**:

```bash
open "http://jenkins.cloudforge.localhost:8080"
open "http://manager.cloudforge.localhost:1958"
open "http://grafana.cloudforge.localhost:3000"
```

Find the live port when unsure:

```bash
# Stack output (MiniStack / LocalStack)
# MiniStackApplicationUrl / LocalStackApplicationUrl often look like http://localhost:8080/

# Or Docker host mappings
docker ps --format '{{.Names}}\t{{.Ports}}' | grep -i jenkins
```

### LocalStack Jenkins note

LocalStack may inject a Jenkins `--prefix` for path-style ELB URLs. Prefer the **direct ECS host port** from `docker ps` (or `LocalStackApplicationUrl`). If the root path 404s, try the prefixed path shown in adaptations, or use the ELB local URL from stack outputs.

Chrome may also treat `*.localhost.localstack.cloud` under Local Network Access rules; Safari + `*.cloudforge.localhost` on the ECS port is usually simpler for UI testing.

---

## Port collisions

Several apps default to **3000** (Grafana, Gitea, Metabase). Hostnames do not fix that — only one process can bind a host port.

- Deploy one of those apps at a time, **or**
- Override the published port in the deployment context / compose when you need several.

See [Local Emulator App Catalog — port collisions](LOCAL_EMULATOR_APP_CATALOG.md).

---

## What this does *not* replace

| Still use | Why |
|-----------|-----|
| Stack outputs (`*ApplicationUrl`, `*LocalUrl`) | Source of truth for port and ELB path |
| `AWS_ENDPOINT_URL=http://localhost:4566` | CLI / SDK / deploy path |
| Route53 records inside the emulator | Canonical AWS fidelity — not your Mac DNS |

Hosts entries are **browser convenience** only. CI and verification should assert CloudFormation / stack outputs, not `/etc/hosts`.

### Port-free URLs (nginx edge)

After hostnames are installed, run the optional **nginx edge** so you can open `http://jenkins.cloudforge.localhost/` **without** a port:

```bash
./scripts/emulator-edge-start.sh
# or: mvn -f cfc-testing cloudforge:emulator-edge-start
# After deploy, CloudForgeDeployment reconciles automatically; manual:
./scripts/emulator-edge-reconcile.sh
```

Full guide: [Local Emulator Edge (nginx)](LOCAL_EMULATOR_EDGE.md). Use [StackPort](../localstack/README.md#resource-browser-stackport) for AWS resource deep-dives — nginx has no app console.

---

## Optional: emulator-specific names only

If you prefer names that say which emulator you intend (same IP still):

```text
127.0.0.1 jenkins.ministack.local grafana.ministack.local manager.ministack.local
127.0.0.1 jenkins.localstack.local grafana.localstack.local manager.localstack.local
```

These are included as aliases in the example file. They do not create separate Docker networks — they only change the address bar label.

---

## Windows

Edit `C:\Windows\System32\drivers\etc\hosts` as Administrator and paste the same `127.0.0.1` lines from [`cloudforge.localhost.hosts`](examples/cloudforge.localhost.hosts) (ignore the `# BEGIN/END` markers if you prefer). Flush DNS if needed: `ipconfig /flushdns`.
