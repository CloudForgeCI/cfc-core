#!/usr/bin/env python3
"""Starts, stops or restarts an emulator through InteractiveDeployer's platform menu.

    python3 scripts/localstack-platform.py localstack restart

The menu numbers platforms in an order that can change between runs, so the platform is chosen by name:
this reads the printed menu and answers with the matching number. Run from cfc-testing. The classpath
defaults to target/classes plus target/dependency and can be overridden with CFC_CLASSPATH.
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

CLASSPATH = os.environ.get("CFC_CLASSPATH", "target/classes:target/dependency/*")
HEALTH_URL = "http://localhost:4566/_localstack/health"


def control(platform, action):
    proc = subprocess.Popen(
        ["java", "-cp", CLASSPATH, "com.cloudforgeci.samples.app.InteractiveDeployer", "--platform"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    buffer = b""

    def read_until(marker):
        nonlocal buffer
        while marker not in buffer:
            char = proc.stdout.read(1)
            if not char:
                break
            buffer += char

    read_until(b"Platform [")
    menu = dict((name.decode(), number) for number, name in re.findall(rb"^(\d)\. (\w+)$", buffer, re.M))
    if platform not in menu:
        proc.kill()
        raise SystemExit(f"platform {platform!r} not in menu {sorted(menu)}")
    proc.stdin.write(menu[platform] + b"\n")
    proc.stdin.flush()
    read_until(b"Choose [")
    proc.stdin.write(action.encode() + b"\n")
    proc.stdin.flush()
    proc.stdin.close()
    return proc.stdout.read().decode(errors="replace")


def wait_healthy(timeout=180):
    """True once LocalStack reports CloudFormation available or running -- "available" means not
    yet started, "running" means a started service, both usable; parsing JSON instead of matching
    a byte string also survives whitespace differences in the response."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(HEALTH_URL, timeout=5) as response:
                services = json.loads(response.read()).get("services", {})
                if services.get("cloudformation") in ("available", "running"):
                    return True
        except (OSError, ValueError):
            pass
        time.sleep(4)
    return False


def restart_clean():
    """Recreates the LocalStack container so the next deployment starts from empty state."""
    control("localstack", "restart")
    return wait_healthy()


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    print(control(sys.argv[1], sys.argv[2])[-800:])
