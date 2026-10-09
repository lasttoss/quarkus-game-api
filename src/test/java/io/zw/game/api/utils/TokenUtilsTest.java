package io.zw.game.api.utils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The game API trusts the auth service because it can check the signature on the token with this
 * key, so the interesting cases are the ones where something that should not verify, does.
 *
 * These are RSA operations from the JDK, no library and no running server, which is also what
 * makes them a fair test of TokenUtils: it is handed a key, nothing else.
 */
class TokenUtilsTest {

    private static KeyPair keys;

    @BeforeAll
    static void generateAKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
    }

    @Test
    void readsAnRsaPublicKeyFromTheBase64TheAuthServicePublishes() throws Exception {
        RSAPublicKey key = TokenUtils.readPublicKey(publicKeyBase64());

        assertEquals("RSA", key.getAlgorithm());
        assertEquals(((RSAPrivateKey) keys.getPrivate()).getModulus(), key.getModulus(),
                "the key must be the one whose private half signed the token");
    }

    @Test
    void theKeyVerifiesATokenSignedByTheAuthService() throws Exception {
        RSAPublicKey key = TokenUtils.readPublicKey(publicKeyBase64());
        String token = sign("{\"sub\":\"user-42\",\"groups\":[\"USER\"]}", keys.getPrivate());

        assertTrue(verifies(token, key), "every request carries this token");
    }

    @Test
    void anotherKeyPairCannotForgeThatToken() throws Exception {
        RSAPublicKey key = TokenUtils.readPublicKey(publicKeyBase64());

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String forged = sign("{\"sub\":\"user-42\",\"groups\":[\"USER\"]}", generator.generateKeyPair().getPrivate());

        assertFalse(verifies(forged, key), "a forged token must not pass");
    }

    @Test
    void aPemBlockIsRefusedRatherThanMisreadAsAKey() {
        String pem = "-----BEGIN PUBLIC KEY-----\n" + publicKeyBase64() + "\n-----END PUBLIC KEY-----";

        // The letters in the BEGIN and END lines are themselves valid base64, so a lenient decoder
        // decodes the armour along with the key and hands a corrupted blob to the JDK. The key is
        // refused, which is the right outcome, but the message says "lengthTag=111, too big" and
        // nothing about the real mistake: the configuration wants the base64 body on its own.
        // This test is here so that nobody later "fixes" it into silently stripping the armour and
        // quietly accepting a key that was never valid.
        assertThrows(Exception.class, () -> TokenUtils.readPublicKey(pem));
    }

    @Test
    void nonsenseIsRefusedInsteadOfBecomingABrokenKey() {
        assertThrows(Exception.class, () -> TokenUtils.readPublicKey("not a key"));
    }

    @Test
    void anEmptyKeyIsRefused() {
        assertThrows(Exception.class, () -> TokenUtils.readPublicKey(""));
    }

    // ---------------------------------------------------------------- helpers

    private static String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    private static String sign(String payload, PrivateKey privateKey) throws Exception {
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String signingInput = header + "." + base64Url(payload);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private static boolean verifies(String token, RSAPublicKey key) throws Exception {
        String[] parts = token.split("\\.");
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(pad(parts[2])));
    }

    private static String base64Url(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String pad(String part) {
        return part + "=".repeat((4 - part.length() % 4) % 4);
    }
}
