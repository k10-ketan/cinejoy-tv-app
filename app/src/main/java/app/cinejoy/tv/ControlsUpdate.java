package app.cinejoy.tv;

import android.content.Context;
import android.os.Handler;
import android.util.AtomicFile;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;

final class ControlsUpdate {
    interface Listener {
        void onCached(ControlsBundle bundle);
        void onChecked(ControlsBundle bundle);
    }

    private static final String TAG = "CineJoyControls";
    private static final String ENDPOINT =
        "https://raw.githubusercontent.com/k10-ketan/cinejoy-tv-app/tv-controls/controls.bin";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    static void check(Context application, Handler ui, Listener listener) {
        WORKER.execute(() -> {
            ControlsBundle cached = null;
            ControlsBundle current = null;
            try {
                PublicKey key = readKey(application);
                AtomicFile file = new AtomicFile(new File(application.getNoBackupFilesDir(), "tv-controls.bin"));
                cached = readCached(file, key);
                current = cached;
                ControlsBundle initial = cached;
                ui.post(() -> listener.onCached(initial));
                byte[] bytes = download();
                ControlsBundle candidate = ControlsBundle.verify(bytes, key);
                if (cached == null || candidate.version > cached.version) {
                    writeCache(file, bytes);
                    current = candidate;
                    Log.i(TAG, "Saved controls bundle " + candidate.version);
                }
            } catch (IOException | GeneralSecurityException failure) {
                Log.d(TAG, "Controls update unavailable", failure);
            }
            ControlsBundle result = current;
            ui.post(() -> listener.onChecked(result));
        });
    }

    private static PublicKey readKey(Context application) throws IOException, GeneralSecurityException {
        try (InputStream input = application.getAssets().open("controls-public.der")) {
            return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(ControlsBundle.readBounded(input)));
        }
    }

    private static ControlsBundle readCached(AtomicFile file, PublicKey key) {
        try (InputStream input = file.openRead()) {
            return ControlsBundle.verify(ControlsBundle.readBounded(input), key);
        } catch (FileNotFoundException missing) {
            return null;
        } catch (IOException | GeneralSecurityException failure) {
            Log.w(TAG, "Ignoring invalid cached controls", failure);
            return null;
        }
    }

    private static byte[] download() throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(ENDPOINT).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            connection.setUseCaches(false);
            if (connection.getResponseCode() != 200) throw new IOException("Controls channel returned a non-200 response");
            try (InputStream input = connection.getInputStream()) {
                return ControlsBundle.readBounded(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void writeCache(AtomicFile file, byte[] bytes) throws IOException {
        FileOutputStream output = file.startWrite();
        try {
            output.write(bytes);
            file.finishWrite(output);
        } catch (IOException failure) {
            file.failWrite(output);
            throw failure;
        }
    }
}
