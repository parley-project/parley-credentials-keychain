/**
 * <PRE>
 *
 * Copyright Tony Bringarder 1998, 2026 <A href="http://bringardner.com/tony">Tony Bringardner</A>
 *
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       <A href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</A>
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  </PRE>
 *
 *
 *	@author Tony Bringardner
 *
 *
 * ~version~V000.00.01-V000.00.00-
 */
package us.bringardner.parley.credentials.keychain;

import us.bringardner.parley.files.credentials.CredentialException;
import us.bringardner.parley.files.credentials.CredentialProvider;
import us.bringardner.parley.files.credentials.CredentialRef;
import us.bringardner.parley.files.credentials.CredentialRequest;

/**
 * {@code @keychain:SERVICE/ACCOUNT}: a secret in the operating system's keychain, which keeps
 * it encrypted and unlocked with the user's login: the macOS Keychain, the Linux Secret
 * Service (GNOME Keyring, KWallet) or the Windows Credential Manager.
 * <p>
 * The locator is split at its first '/': {@code @keychain:parley/prod-db} is service
 * "parley", account "prod-db". With no '/', the service is "parley" and the whole locator is
 * the account. A secret stored by another tool can be named the same way.
 * <p>
 * Found through {@link java.util.ServiceLoader}: putting this module on the class path is
 * enough. Where no keychain can be reached (a server with no desktop session, say) it reports
 * itself unavailable and the reason. Keychains can't be listed, so {@link #list()} is empty.
 */
public class KeychainCredentialProvider implements CredentialProvider {

	/** The service used when the locator has no '/'. */
	public static final String DEFAULT_SERVICE = "parley";

	private final KeychainStore store;

	public KeychainCredentialProvider() {
		this(KeychainStore.system());
	}

	/** For tests and other keychains: the store to use. */
	public KeychainCredentialProvider(KeychainStore store) {
		this.store = store;
	}

	@Override
	public String scheme() {
		return "keychain";
	}

	@Override
	public String description() {
		return "the "+store.name();
	}

	@Override
	public boolean isAvailable() {
		return store.isAvailable();
	}

	@Override
	public String unavailableReason() {
		return store.isAvailable() ? "" : store.unavailableReason();
	}

	@Override
	public char[] resolve(CredentialRef ref, CredentialRequest request) throws CredentialException {
		String[] sa = serviceAndAccount(ref);
		String val;
		String where = "(service "+sa[0]+", account "+sa[1]+")";
		try {
			val = store.get(sa[0], sa[1]);
		} catch (KeychainStore.KeychainException e) {
			throw new CredentialException(ref, "the keychain entry "+where+" is "+e.getMessage());
		}
		if( val == null || val.isEmpty()) {
			throw new CredentialException(ref, "not found in the keychain "+where);
		}
		return val.toCharArray();
	}

	@Override
	public boolean canStore() {
		return true;
	}

	@Override
	public void store(CredentialRef ref, char[] secret) throws CredentialException {
		if( secret == null || secret.length == 0 ) {
			throw new CredentialException(ref, "can't store an empty secret");
		}
		String[] sa = serviceAndAccount(ref);
		try {
			store.set(sa[0], sa[1], new String(secret));
		} catch (KeychainStore.KeychainException e) {
			throw new CredentialException(ref, "can't write to the keychain: "+e.getMessage());
		}
	}

	@Override
	public void remove(CredentialRef ref) throws CredentialException {
		String[] sa = serviceAndAccount(ref);
		try {
			store.delete(sa[0], sa[1]);
		} catch (KeychainStore.KeychainException e) {
			throw new CredentialException(ref, "can't remove it from the keychain: "+e.getMessage());
		}
	}

	/** The service and account a reference names: split at the first '/'. */
	static String[] serviceAndAccount(CredentialRef ref) throws CredentialException {
		String loc = ref.locator();
		int slash = loc.indexOf('/');
		String service = slash < 0 ? DEFAULT_SERVICE : loc.substring(0, slash);
		String account = slash < 0 ? loc : loc.substring(slash+1);
		if( service.isEmpty() || account.isEmpty()) {
			throw new CredentialException(ref, "names no keychain entry; use @keychain:service/account");
		}
		return new String[] {service, account};
	}
}
