# InviteCode

**InviteCode** is a Paper plugin that requires players to use `/join [invite code]` within a configurable time limit, or they will be kicked/banned from the server, with configurable amount of retries.

---

## **Features**

* Enforces player verification via invite codes.
* Configurable time limit for verification (default: 60 seconds).
* Configurable punishment (kick, ban, ban-ip)
* Configurable attempts (default: 3)
* Keeps track of verified players across server restarts.
* Lightweight and easy to set up & works on cracked/online mode.

---

## **Installation**

1. Place the `invitecode.jar` file into your server’s `plugins` folder.
2. Start the server once — a `config.yml` and `verified.yml` will be automatically generated.
3. Edit `config.yml` to add your invite codes and modify the kick timer, punishment type, retries as needed.

---

## **Permissions**

* `invitecode.join` — Allows a player to use the `/join` command.

---

## **Commands**

```text
/join [code]  - Verify your account using an invite code.
/join reload  - Console / operators only, reloads config.yml.
```

**Notes:**

* Codes are **case-insensitive** (recommended for ease of use).