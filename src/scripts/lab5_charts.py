import json
import pathlib
import sys

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

RESULTS = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "results/lab5")
OUT = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "../docs/img/lab5")
OUT.mkdir(parents=True, exist_ok=True)


def load(name):
    path = RESULTS / f"{name}.json"
    return json.loads(path.read_text(encoding="utf-8-sig")) if path.exists() else None


def rows(report):
    return report["rows"]


def scenario_chart(name, title):
    report = load(name)
    if not report:
        return
    data = rows(report)
    vus = [r["vus"] for r in data]
    fig, (left, right) = plt.subplots(1, 2, figsize=(12, 4.2))
    left.plot(vus, [r["rps"] for r in data], marker="o", color="#2b6cb0")
    left.set_title(f"{title}: throughput")
    left.set_xlabel("Virtual users")
    left.set_ylabel("Requests / s")
    left.grid(alpha=0.3)
    for key, label, color in (("p50Ms", "p50", "#38a169"), ("p95Ms", "p95", "#dd6b20"), ("p99Ms", "p99", "#c53030")):
        right.plot(vus, [r[key] for r in data], marker="o", label=label, color=color)
    right.plot(vus, [r["avgMs"] for r in data], marker="x", linestyle="--", label="avg", color="#4a5568")
    right.set_title(f"{title}: latency")
    right.set_xlabel("Virtual users")
    right.set_ylabel("ms")
    right.set_yscale("log")
    right.grid(alpha=0.3, which="both")
    right.legend()
    errors = [r["errorRate"] * 100 for r in data]
    if any(errors):
        twin = left.twinx()
        twin.bar(vus, errors, width=[v * 0.15 for v in vus], alpha=0.25, color="#c53030")
        twin.set_ylabel("Errors, %")
    fig.tight_layout()
    fig.savefig(OUT / f"{name}.png", dpi=120)
    plt.close(fig)


def comparison_chart(file_name, title, runs):
    loaded = [(label, load(run)) for label, run in runs]
    loaded = [(label, report) for label, report in loaded if report]
    if len(loaded) < 2:
        return
    fig, (left, right) = plt.subplots(1, 2, figsize=(12, 4.2))
    for label, report in loaded:
        data = rows(report)
        vus = [r["vus"] for r in data]
        left.plot(vus, [r["rps"] for r in data], marker="o", label=label)
        right.plot(vus, [r["p95Ms"] for r in data], marker="o", label=f"{label} p95")
        right.plot(vus, [r["p99Ms"] for r in data], marker=".", linestyle=":", label=f"{label} p99")
    left.set_title(f"{title}: throughput")
    left.set_xlabel("Virtual users")
    left.set_ylabel("Requests / s")
    left.grid(alpha=0.3)
    left.legend()
    right.set_title(f"{title}: tail latency")
    right.set_xlabel("Virtual users")
    right.set_ylabel("ms")
    right.set_yscale("log")
    right.grid(alpha=0.3, which="both")
    right.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(OUT / f"{file_name}.png", dpi=120)
    plt.close(fig)


scenario_chart("baseline-a", "Scenario A (read)")
scenario_chart("baseline-b", "Scenario B (write)")
scenario_chart("baseline-c", "Scenario C (workflow)")
comparison_chart("scaling", "Scenario A, user-service instances", [
    ("1 instance", "scaling-1-instance"), ("2 instances", "scaling-2-instances"), ("3 instances", "scaling-3-instances")])
comparison_chart("caching", "Scenario A, Redis cache", [
    ("cache disabled", "caching-disabled"), ("cache enabled", "caching-enabled")])
comparison_chart("replication", "Scenario A, PostgreSQL topology", [
    ("single primary", "replication-single-primary"), ("primary + replica", "replication-primary-replica")])
print("charts written to", OUT.resolve())
