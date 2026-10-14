# Changelog

## parley-credentials-keychain 1.0.0 (unreleased)

First release.

- `KeychainCredentialProvider`: the `@keychain:service/account` credential provider for parley-files,
  on the macOS Keychain, the Linux Secret Service and the Windows Credential Manager, through
  java-keyring 1.0.4. Registered for `ServiceLoader`.
- Stores and removes entries (`cred set`, `cred rm` in fsh); can't list them.
- Reports itself unavailable, with the reason, where no keychain can be reached.
