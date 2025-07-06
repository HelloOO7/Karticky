package cz.nocard.android;

import android.content.Context;

import androidx.core.content.res.ResourcesCompat;

import com.google.zxing.BarcodeFormat;

public class BarcodeTypeNames {

    public static String get(Context context, BarcodeFormat barcodeFormat) {
        int resId = switch (barcodeFormat) {
            case AZTEC -> R.string.code_type_aztec;
            case ITF -> R.string.code_type_itf;
            case UPC_A -> R.string.code_type_upc_a;
            case UPC_E -> R.string.code_type_upc_e;
            case EAN_8 -> R.string.code_type_ean_8;
            case EAN_13 -> R.string.code_type_ean_13;
            case CODE_39 -> R.string.code_type_code_39;
            case CODE_93 -> R.string.code_type_code_93;
            case CODE_128 -> R.string.code_type_code_128;
            case CODABAR -> R.string.code_type_codabar;
            case QR_CODE -> R.string.code_type_qr;
            case PDF_417 -> R.string.code_type_pdf417;
            case DATA_MATRIX -> R.string.code_type_data_matrix;
            case RSS_14 -> R.string.code_type_rss_14;
            case RSS_EXPANDED -> R.string.code_type_rss_expanded;
            case MAXICODE -> R.string.code_type_maxicode;
            default -> ResourcesCompat.ID_NULL;
        };
        if (resId != ResourcesCompat.ID_NULL) {
            return context.getString(resId);
        } else {
            return null;
        }
    }

    public static String getOrDefault(Context context, BarcodeFormat barcodeFormat) {
        String name = get(context, barcodeFormat);
        return name != null ? name : barcodeFormat.toString();
    }
}
