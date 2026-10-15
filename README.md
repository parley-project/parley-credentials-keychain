# parley-credentials-keychain

A [parley-files](https://github.com/parley-project/parley-files) credential provider for the
operating system's keychain, part of **Parley**, a family of Java libraries for implementing internet
protocols. A secret connection setting (an SFTP, FTP or JDBC password, say) can name a keychain entry
instead of holding the password:

```
password=@keychain:parley/prod-db
```

The keychain keeps the secret encrypted and unlocked with your login:

| System | Keychain |
| --- | --- |
| macOS | Keychain |
| Linux | Secret Service (GNOME Keyring, KWallet), over D-Bus |
| Windows | Credential Manager |

Put it on the class path and parley-files finds it through `ServiceLoader`; nothing else to configure.
fsh bundles it.

Requires Java 17 or later (java-keyring's Secret Service client needs it). Depends on `parley-files`
and [java-keyring](https://github.com/javakeyring/java-keyring).

```xml
<dependency>
    <groupId>us.bringardner.parley</groupId>
    <artifactId>parley-credentials-keychain</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Naming an entry

`@keychain:SERVICE/ACCOUNT`, split at the first `/`:

| Reference | Service | Account |
| --- | --- | --- |
| `@keychain:parley/prod-db` | parley | prod-db |
| `@keychain:prod-db` | parley | prod-db |
| `@keychain:git/https://host/x` | git | https://host/x |

So an entry another tool stored can be named too.

## Storing a secret

From fsh: `cred set keychain:parley/prod-db` (asks without echo). From Java:

```java
Credentials.store(new CredentialRef("keychain", "parley/prod-db"), password);
```

Or with the system's own tool: `security add-generic-password -s parley -a prod-db -w` on macOS,
`secret-tool store --label=prod-db service parley account prod-db` on Linux.

## The keystore's master password

parley-files' `@keystore:` provider looks for its master password at `@keychain:parley/keystore`
before asking for it, so storing it here unlocks the keystore without a prompt.

## Limits

- Keychains can't be listed: `list()` is empty, so `cred list` shows no keychain entries.
- Where no keychain can be reached (a server with no desktop session, a container) the provider reports
  itself unavailable with the reason; use `@env:`, `@file:` or `@keystore:` there.
- java-keyring passes secrets as `String`s, so they can't be zeroed after use.
