package app.cinejoy.tv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;

final class ControlsBundle {
    static final int MAX_BYTES = 256 * 1024;
    private static final int SIGNATURE_BYTES = 256;
    private static final int FORMAT = 0x434a5431; // CJT1
    private static final int HEADER_BYTES = 16;

    final int version;
    final String remoteScript;
    final String mediaScript;

    private ControlsBundle(int version, String remoteScript, String mediaScript) {
        this.version = version;
        this.remoteScript = remoteScript;
        this.mediaScript = mediaScript;
    }

    static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) throw new IOException("Controls bundle is too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    static ControlsBundle verify(byte[] bytes, PublicKey key) throws GeneralSecurityException, IOException {
        if (bytes.length < SIGNATURE_BYTES + HEADER_BYTES || bytes.length > MAX_BYTES) {
            throw new IOException("Invalid controls bundle size");
        }
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(key);
        signature.update(bytes, SIGNATURE_BYTES, bytes.length - SIGNATURE_BYTES);
        if (!signature.verify(bytes, 0, SIGNATURE_BYTES)) {
            throw new GeneralSecurityException("Invalid controls signature");
        }

        DataInputStream payload = new DataInputStream(new ByteArrayInputStream(
            bytes, SIGNATURE_BYTES, bytes.length - SIGNATURE_BYTES));
        if (payload.readInt() != FORMAT) throw new IOException("Unsupported controls format");
        int version = payload.readInt();
        int remoteLength = payload.readInt();
        int mediaLength = payload.readInt();
        if (version < 1 || remoteLength < 1 || mediaLength < 1 ||
                (long) remoteLength + mediaLength != payload.available()) {
            throw new IOException("Invalid controls bundle header");
        }
        return new ControlsBundle(version, readScript(payload, remoteLength), readScript(payload, mediaLength));
    }

    private static String readScript(DataInputStream payload, int length) throws IOException {
        byte[] script = new byte[length];
        payload.readFully(script);
        return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(script)).toString();
    }
}
