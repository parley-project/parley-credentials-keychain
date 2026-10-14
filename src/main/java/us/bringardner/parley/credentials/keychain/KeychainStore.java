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

import com.github.javakeyring.BackendNotSupportedException;
import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

/**
 * A keychain: where {@link KeychainCredentialProvider} reads and writes. The system one is
 * java-keyring's; tests use one in memory.
 */
public interface KeychainStore {

	/** A keychain operation failed. Its message never holds a secret. */
	class KeychainException extends Exception {
		private static final long serialVersionUID = 1L;

		public KeychainException(String message) {
			super(message);
		}
	}

	/** What a person calls this keychain, such as "macOS Keychain". */
	String name();

	/** False if this keychain can't be reached here (no backend, no desktop session...). */
	boolean isAvailable();

	/** Why {@link #isAvailable()} is false. */
	String unavailableReason();

	/** The secret for service and account, or null if there is none. */
	String get(String service, String account) throws KeychainException;

	/** Saves secret for service and account, replacing any it had. */
	void set(String service, String account, String secret) throws KeychainException;

	/** Removes the secret for service and account. */
	void delete(String service, String account) throws KeychainException;

	/** The operating system's keychain, through java-keyring; opened on first use. */
	static KeychainStore system() {
		return new SystemKeychain();
	}

	/** java-keyring: macOS Keychain, Linux Secret Service, Windows Credential Manager. */
	final class SystemKeychain implements KeychainStore {

		private Keyring keyring;
		private String failure;
		private boolean tried;

		private synchronized Keyring keyring() throws KeychainException {
			if( !tried ) {
				tried = true;
				try {
					keyring = Keyring.create();
				} catch (BackendNotSupportedException | RuntimeException | LinkageError e) {
					failure = "no keychain on this system ("+e.getMessage()+")";
				}
			}
			if( keyring == null ) {
				throw new KeychainException(failure);
			}
			return keyring;
		}

		@Override
		public String name() {
			String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
			if( os.startsWith("mac")) {
				return "macOS Keychain";
			}
			if( os.startsWith("windows")) {
				return "Windows Credential Manager";
			}
			return "Secret Service keyring";
		}

		@Override
		public synchronized boolean isAvailable() {
			try {
				keyring();
				return true;
			} catch (KeychainException e) {
				return false;
			}
		}

		@Override
		public synchronized String unavailableReason() {
			return isAvailable() ? "" : failure;
		}

		@Override
		public synchronized String get(String service, String account) throws KeychainException {
			try {
				return keyring().getPassword(service, account);
			} catch (PasswordAccessException e) {
				// most backends report a missing entry this way, as well as real failures
				throw new KeychainException("not found, or can't be read ("+e.getMessage()+")");
			}
		}

		@Override
		public synchronized void set(String service, String account, String secret) throws KeychainException {
			try {
				keyring().setPassword(service, account, secret);
			} catch (PasswordAccessException e) {
				throw new KeychainException(e.getMessage());
			}
		}

		@Override
		public synchronized void delete(String service, String account) throws KeychainException {
			try {
				keyring().deletePassword(service, account);
			} catch (PasswordAccessException e) {
				throw new KeychainException("not found, or can't be removed ("+e.getMessage()+")");
			}
		}
	}
}
