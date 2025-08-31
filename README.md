# InviteCode

**InviteCode** is a Paper plugin that requires players to use `/join [invite code]` within a configurable time limit, or they will be kicked from the server.

---

## **Features**

* Enforces player verification via invite codes.
* Configurable time limit for verification (default: 60 seconds).
* Keeps track of verified players across server restarts.
* Lightweight and easy to set up.

---

## **Installation**

1. Place the `InviteCode.jar` file into your server’s `plugins` folder.
2. Start the server once — a `config.yml` and `verified.yml` will be automatically generated.
3. Edit `config.yml` to add your invite codes and modify the kick timer as needed.

---

## **Permissions**

* `invitecode.join` — Allows a player to use the `/join` command.

---

## **Commands**

```text
/join [code]  - Verify your account using an invite code.
```

**Notes:**

* Codes are **case-insensitive** (recommended for ease of use).
* Players who do not join within the configured time will be automatically kicked.

