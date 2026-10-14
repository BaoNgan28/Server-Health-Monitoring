# E-mail setup

Group 10 — Server Health Monitoring System

E-mail is the remote alert channel: it is what reaches an administrator who is
not sitting in front of the monitoring client.

**Right now it is switched off**, because the file below still contains its
shipped placeholders. That is a normal, safe state — the application runs
completely normally, alerts still appear in the window and in the database, and
no SMTP login is ever attempted with a placeholder. Filling in two values turns
it on.

---

## 1. What you need

* The sender Gmail account: **bocon2663@gmail.com**
* A **Gmail App Password** for that account — 16 characters, not the account
  password. Google displays it in four groups of four; type it **without the
  spaces**.
* A recipient address. Any valid e-mail address works, and it may be the same as
  the sender.

An App Password requires 2-Step Verification to be enabled on the Google account.
It is created at **Google Account → Security → 2-Step Verification → App
passwords**.

---

## 2. Configure it

Open this file in any text editor:

```
F:\Project LTM\ServerHealthMonitoring\config\email-secrets.properties
```

It currently reads:

```properties
smtp.username=bocon2663@gmail.com
smtp.appPassword=PASTE_GMAIL_APP_PASSWORD_HERE
smtp.recipient=PASTE_RECIPIENT_EMAIL_HERE

smtp.host=smtp.gmail.com
smtp.port=587
smtp.starttls=true
smtp.auth=true
```

Change exactly two lines:

```properties
smtp.username=bocon2663@gmail.com
smtp.appPassword=abcdefghijklmnop
smtp.recipient=your.address@example.com
```

Leave `smtp.host`, `smtp.port`, `smtp.starttls` and `smtp.auth` as they are.
Save the file.

**No source code change. No Maven rebuild.** The file is read from outside the
jar at runtime, which is the whole reason it lives in `config\` rather than
inside the packaged application.

---

## 3. Turn it on

Either restart the monitoring client, or, without restarting:

**Settings → Reload Email Configuration**

Then check **Settings → Notifications**:

```
Email Notification Status : CONFIGURED
Sender                    : bocon2663@gmail.com
Recipient                 : your.address@example.com
SMTP                      : smtp.gmail.com:587 STARTTLS
```

---

## 4. Send a test e-mail

**Settings → Send Test Email.**

The button calls the real notifier over a real SMTP session. On success a dialog
says:

```
Test email sent successfully.
```

and the recipient receives:

> **Subject:** `[TEST] Group 10 Server Health Monitoring`
>
> ```
> GROUP 10 - SERVER HEALTH MONITORING
>
> This is a test notification.
>
> SMTP email notification is configured successfully.
>
> Sender: bocon2663@gmail.com
> Recipient: your.address@example.com
> Time: 2026-09-29 15:04:11
> ```

If it fails, the dialog shows the reason the mail server itself gave. The
password is never shown, and is masked out of the message before it reaches the
dialog or the log.

While e-mail is still unconfigured, the button does not attempt a login. It shows
a dialog naming the file to edit and the two keys to set.

---

## 5. What gets e-mailed

| Event | Subject |
|---|---|
| CPU over its threshold | `[WARNING] CPU HIGH - AGENT-01` |
| Memory over its threshold | `[WARNING] MEMORY HIGH - AGENT-01` |
| Network over its threshold | `[WARNING] NETWORK TRAFFIC HIGH - AGENT-01` |
| Agent stopped answering | `[CRITICAL] SERVER OFFLINE - AGENT-02` |
| Agent came back | `[RECOVERED] SERVER ONLINE - AGENT-02` |
| Test button | `[TEST] Group 10 Server Health Monitoring` |

A CPU alert body:

```
GROUP 10 - SERVER HEALTH MONITORING

Alert Type: CPU HIGH
Severity: WARNING

Agent:
AGENT-01

Server:
LOCAL-AGENT-01

Current CPU:
25.4 %

Threshold:
5.0 %

Detected At:
2026-09-29 15:04:11
```

An offline body:

```
GROUP 10 - SERVER HEALTH MONITORING

CRITICAL ALERT

Agent:
AGENT-02

Server:
LOCAL-AGENT-02

Status:
OFFLINE

Reason:
No valid heartbeat received for more than 15 seconds.

Last Seen:
2026-09-29 15:03:47

Detected At:
2026-09-29 15:04:05
```

**One e-mail per incident, not per sample.** A CPU that stays above its threshold
for an hour produces one message, not 3600. A new message is only sent after the
condition has cleared and tripped again.

**Forged packets are never e-mailed.** `SECURITY_INVALID_HMAC` and
`SECURITY_REPLAY_PACKET` are recorded in the alert panel and the log, but a flood
of forged packets must not turn into a flood of mail.

---

## 6. Using environment variables instead

Environment variables take priority over the file, which suits a real deployment
where a password should not sit on disk:

```bat
set SMTP_USERNAME=bocon2663@gmail.com
set SMTP_APP_PASSWORD=abcdefghijklmnop
set ALERT_RECIPIENT_EMAIL=your.address@example.com
```

Start the monitoring client from that same command window. Any variable that is
not set falls back to the file, so the two can be mixed.

---

## 7. If it does not work

| Symptom | Cause and fix |
|---|---|
| Status stays `NOT CONFIGURED` | A placeholder is still in place, or a value is not a valid e-mail address. The `Reason:` line in Settings names the offending key. |
| `535 Username and Password not accepted` | The account password was used instead of an App Password, or the App Password was typed with its spaces. Remove the spaces. |
| `Couldn't connect to host, port: smtp.gmail.com, 587` | No internet, or a firewall or proxy is blocking outbound TCP 587. |
| `javax.net.ssl` handshake errors | The system clock is badly wrong, or TLS is being intercepted on the network. |
| Nothing arrives, no error | Check the recipient's spam folder; Gmail may file the first message from a new sender there. |

Every attempt is logged in `logs\monitor-0.log`, with the server's own reason and
never the password.

---

## 8. Security notes

* `config/email-secrets.properties` is listed in `.gitignore`, so a real App
  Password cannot be committed by accident. The tracked template is
  `email-secrets.properties.example`.
* The password is never hard-coded in Java source, never written to a log, never
  displayed in the GUI, and never included in an exception message — it is
  replaced with `***` before any failure reason is shown.
* Sending runs on one background thread behind a bounded queue, so a mail server
  that stops answering cannot block the UDP receiver, the Swing event dispatch
  thread or the heartbeat timer, and cannot make memory grow.
* Revoke an App Password at any time from the same Google Account page; the
  monitoring client then reports the failure and keeps running.
