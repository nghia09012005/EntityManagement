
import json
import random
import hashlib
from datetime import datetime, timedelta

NUM_LOGS = 1000

users = [
    "nghia",
    "admin",
    "guest",
    "svc-backup",
    "attacker"
]

hosts = [
    "WIN-PC01",
    "WIN-DC01",
    "SRV-FILE01",
    "SRV-WEB01"
]

ips = [
    "192.168.1.100",
    "192.168.1.101",
    "192.168.1.102",
    "185.220.101.42",  # suspicious
    "203.0.113.10"
]

domains = [
    "google.com",
    "github.com",
    "microsoft.com",
    "evil-c2.com",
    "malware-download.net"
]

processes = [
    "powershell.exe",
    "cmd.exe",
    "outlook.exe",
    "chrome.exe",
    "mimikatz.exe",
    "psexec.exe"
]


def random_hash():
    return hashlib.sha256(
        str(random.random()).encode()
    ).hexdigest()


def random_timestamp():
    start = datetime.utcnow() - timedelta(days=7)

    return (
        start +
        timedelta(seconds=random.randint(0, 7 * 24 * 3600))
    ).isoformat() + "Z"


def auth_event():
    return {
        "eventType": "AUTHENTICATION",

        "username": random.choice(users),
        "ipAddress": random.choice(ips),
        "success": random.random() > 0.15,
        "workstation": random.choice(hosts),

        "timestamp": random_timestamp()
    }


def process_event():
    process = random.choice(processes)

    return {
        "eventType": "PROCESS",

        "processName": process,
        "processPath": f"C:\\Windows\\System32\\{process}",
        "fileHash": random_hash(),
        "commandLine": f"{process} /c test",

        "timestamp": random_timestamp()
    }


def network_event():
    return {
        "eventType": "NETWORK",

        "srcIp": random.choice(ips),
        "dstIp": random.choice(ips),
        "dstDomain": random.choice(domains),
        "dstPort": random.choice([
            80,
            443,
            8080,
            3389
        ]),

        "timestamp": random_timestamp()
    }


def alert_event():
    suspicious_hash = random_hash()

    return {
        "eventType": "ALERT",

        "alertName": random.choice([
            "Credential Dumping",
            "Suspicious PowerShell",
            "Malicious Domain Access",
            "Lateral Movement"
        ]),

        "severity": random.choice([
            "LOW",
            "MEDIUM",
            "HIGH"
        ]),

        "description": "Generated security alert",

        "targetIp": random.choice(ips),
        "targetUser": random.choice(users),
        "targetHost": random.choice(hosts),
        "targetDomain": random.choice(domains),
        "targetFileHash": suspicious_hash,

        "timestamp": random_timestamp()
    }


def generate_event():

    event_type = random.choices(
        population=[
            "AUTH",
            "PROCESS",
            "NETWORK",
            "ALERT"
        ],
        weights=[
            50,
            25,
            15,
            10
        ]
    )[0]

    if event_type == "AUTH":
        return auth_event()

    if event_type == "PROCESS":
        return process_event()

    if event_type == "NETWORK":
        return network_event()

    return alert_event()


with open("soc_logs.json", "w") as f:
    for _ in range(NUM_LOGS):
        f.write(
            json.dumps(generate_event()) + "\n"
        )

print(
    f"Generated {NUM_LOGS} logs into soc_logs.json"
)

