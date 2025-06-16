package cz.nocard.android;

import android.os.Build;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InputStreamCompat {

    public static void transferTo(InputStream in, OutputStream out) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            in.transferTo(out);
        }
        else {
            byte[] buffer = new byte[1024*128];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }
}
