package app.cinejoy.tv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public final class ControlsBundleTest {
    private static final int FORMAT = 0x434a5431;

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair key = generator.generateKeyPair();
        byte[] payload = header(2, 3, 5).put(new byte[] {'f', 'o', 'o', 'b', 'a', 'r', '(', ')'}).array();
        byte[] bundle = sign(payload, key);
        ControlsBundle verified = ControlsBundle.verify(bundle, key.getPublic());
        check(verified.version == 2 && verified.remoteScript.equals("foo") && verified.mediaScript.equals("bar()"));

        byte[] tampered = bundle.clone();
        tampered[tampered.length - 1] ^= 1;
        rejects(() -> ControlsBundle.verify(tampered, key.getPublic()));
        rejects(() -> ControlsBundle.verify(bundle, generator.generateKeyPair().getPublic()));
        rejects(() -> ControlsBundle.verify(Arrays.copyOf(bundle, bundle.length - 1), key.getPublic()));

        byte[] unsupported = payload.clone();
        ByteBuffer.wrap(unsupported).putInt(0, FORMAT + 1);
        rejects(() -> ControlsBundle.verify(sign(unsupported, key), key.getPublic()));

        byte[] invalidLength = payload.clone();
        ByteBuffer.wrap(invalidLength).putInt(8, Integer.MAX_VALUE);
        rejects(() -> ControlsBundle.verify(sign(invalidLength, key), key.getPublic()));

        byte[] malformedUtf8 = payload.clone();
        malformedUtf8[16] = (byte) 0xff;
        rejects(() -> ControlsBundle.verify(sign(malformedUtf8, key), key.getPublic()));
        rejects(() -> ControlsBundle.verify(sign(header(0, 3, 5).put(payload, 16, 8).array(), key), key.getPublic()));
        rejects(() -> ControlsBundle.readBounded(new ByteArrayInputStream(new byte[ControlsBundle.MAX_BYTES + 1])));
        check(ControlsBundle.readBounded(new ByteArrayInputStream(new byte[ControlsBundle.MAX_BYTES])).length ==
            ControlsBundle.MAX_BYTES);

        if (args.length == 2) {
            PublicKey releaseKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Files.readAllBytes(Paths.get(args[0]))));
            ControlsBundle release = ControlsBundle.verify(Files.readAllBytes(Paths.get(args[1])), releaseKey);
            System.out.println("Verified published controls format and signature for version " + release.version);
        }
        System.out.println("PASS controls bundle verification");
    }

    private static ByteBuffer header(int version, int remoteSize, int mediaSize) {
        return ByteBuffer.allocate(24).putInt(FORMAT).putInt(version).putInt(remoteSize).putInt(mediaSize);
    }

    private static byte[] sign(byte[] payload, KeyPair key) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key.getPrivate());
        signer.update(payload);
        byte[] signature = signer.sign();
        return ByteBuffer.allocate(signature.length + payload.length).put(signature).put(payload).array();
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Unexpected controls bundle verification result");
    }

    private static void rejects(Probe probe) throws Exception {
        try {
            probe.run();
            throw new AssertionError("Invalid controls bundle was accepted");
        } catch (IOException | GeneralSecurityException expected) {
            // Failure is required before the bundle can be activated.
        }
    }

    private interface Probe {
        void run() throws Exception;
    }
}
