package us.bringardner.parley.credentials.keychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import us.bringardner.parley.files.credentials.CredentialException;
import us.bringardner.parley.files.credentials.CredentialRef;
import us.bringardner.parley.files.credentials.CredentialRequest;
import us.bringardner.parley.files.credentials.Credentials;
import us.bringardner.parley.files.credentials.KeyStoreCredentialProvider;

public class KeychainCredentialProviderTest {

	private static final CredentialRequest REQ = new CredentialRequest("sftp", "password", "prod", "deploy");

	/** A keychain in memory. */
	static class MemoryStore implements KeychainStore {
		final Map<String, String> entries = new HashMap<>();

		@Override
		public String name() {
			return "test keychain";
		}

		@Override
		public boolean isAvailable() {
			return true;
		}

		@Override
		public String unavailableReason() {
			return "";
		}

		@Override
		public String get(String service, String account) {
			return entries.get(service+"/"+account);
		}

		@Override
		public void set(String service, String account, String secret) {
			entries.put(service+"/"+account, secret);
		}

		@Override
		public void delete(String service, String account) throws KeychainException {
			if( entries.remove(service+"/"+account) == null ) {
				throw new KeychainException("not found");
			}
		}
	}

	@TempDir
	Path dir;

	@AfterEach
	public void defaults() {
		Credentials.restoreDefaults();
		Credentials.setPrompter(null);
	}

	@Test
	public void itIsFoundThroughServiceLoader() {
		assertTrue(Credentials.get("keychain").orElseThrow() instanceof KeychainCredentialProvider);
	}

	@Test
	public void theLocatorIsServiceSlashAccount() throws CredentialException {
		assertEquals("parley|prod-db", String.join("|", KeychainCredentialProvider.serviceAndAccount(ref("parley/prod-db"))));
		assertEquals("parley|prod-db", String.join("|", KeychainCredentialProvider.serviceAndAccount(ref("prod-db"))));
		assertEquals("git|https://host/x", String.join("|", KeychainCredentialProvider.serviceAndAccount(ref("git/https://host/x"))),
				"only the first '/' splits");
		assertThrows(CredentialException.class, () -> KeychainCredentialProvider.serviceAndAccount(ref("parley/")));
		assertThrows(CredentialException.class, () -> KeychainCredentialProvider.serviceAndAccount(ref("")));
	}

	@Test
	public void storeResolveAndRemove() throws IOException {
		MemoryStore store = new MemoryStore();
		Credentials.register(new KeychainCredentialProvider(store));
		Credentials.store(ref("parley/prod-db"), "pä=ss".toCharArray());
		assertEquals("pä=ss", store.entries.get("parley/prod-db"));
		assertEquals("pä=ss", new String(Credentials.secret("@keychain:parley/prod-db", REQ)));

		Credentials.remove(ref("parley/prod-db"));
		CredentialException e = assertThrows(CredentialException.class, () -> Credentials.secret("@keychain:parley/prod-db", REQ));
		assertTrue(e.getMessage().contains("not found in the keychain (service parley, account prod-db)"), e.getMessage());
		assertThrows(CredentialException.class, () -> Credentials.remove(ref("parley/prod-db")));
	}

	@Test
	public void theKeystoreTakesItsMasterPasswordFromTheKeychain() throws IOException {
		MemoryStore store = new MemoryStore();
		store.entries.put("parley/keystore", "master-from-keychain");
		Credentials.register(new KeychainCredentialProvider(store));
		KeyStoreCredentialProvider ks = new KeyStoreCredentialProvider(dir.resolve("creds.p12"));
		Credentials.register(ks);
		// no prompter: the master password can only come from the keychain
		Credentials.store(new CredentialRef("keystore", "db"), "secret".toCharArray());
		ks.lock();
		assertEquals("secret", new String(Credentials.secret("@keystore:db", REQ)));
	}

	@Test
	public void theSystemKeychainReportsWhetherItIsThere() {
		KeychainCredentialProvider p = new KeychainCredentialProvider();
		// either way, asking doesn't throw; unavailable comes with a reason
		if( !p.isAvailable()) {
			assertFalse(p.unavailableReason().isEmpty());
			CredentialException e = assertThrows(CredentialException.class, () -> {
				Credentials.register(p);
				Credentials.secret("@keychain:parley/x", REQ);
			});
			assertTrue(e.getMessage().contains("unavailable"), e.getMessage());
		}
	}

	private static CredentialRef ref(String locator) {
		return new CredentialRef("keychain", locator);
	}
}
