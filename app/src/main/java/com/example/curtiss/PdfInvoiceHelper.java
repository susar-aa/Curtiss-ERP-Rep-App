package com.example.curtiss;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfInvoiceHelper {

    public static File generateInvoicePdf(
            Context context,
            String invoiceNum,
            String customerName,
            String customerPhone,
            String paymentMethod,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal grandTotal,
            List<BillingActivity.CartItemModel> items
    ) {
        PdfDocument pdfDocument = new PdfDocument();
        int pageWidth = 595;
        int pageHeight = 842;

        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
        PdfDocument.Page page = pdfDocument.startPage(pageInfo);
        Canvas canvas = page.getCanvas();

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        Paint titlePaint = new Paint();
        titlePaint.setAntiAlias(true);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(22);
        titlePaint.setColor(Color.BLACK);
        canvas.drawText("CURTISS ERP", 36, 50, titlePaint);

        paint.setTextSize(12);
        paint.setColor(Color.parseColor("#475569"));
        canvas.drawText("Sales Invoice & Receipt", 36, 68, paint);

        Paint boldPaint = new Paint();
        boldPaint.setAntiAlias(true);
        boldPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        boldPaint.setTextSize(14);
        boldPaint.setColor(Color.BLACK);
        canvas.drawText(invoiceNum != null ? invoiceNum : "INVOICE", 400, 50, boldPaint);

        // Top divider
        paint.setColor(Color.parseColor("#E2E8F0"));
        paint.setStrokeWidth(1);
        canvas.drawLine(36, 85, 559, 85, paint);

        // Metadata box
        paint.setColor(Color.parseColor("#F8FAFC"));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRect(36, 95, 559, 175, paint);

        paint.setColor(Color.BLACK);
        paint.setTextSize(11);
        canvas.drawText("Customer / Shop:", 50, 118, boldPaint);
        canvas.drawText(customerName != null ? customerName : "N/A", 160, 118, paint);

        canvas.drawText("Phone:", 50, 138, boldPaint);
        canvas.drawText(customerPhone != null && !customerPhone.isEmpty() ? customerPhone : "N/A", 160, 138, paint);

        canvas.drawText("Billing Date:", 50, 158, boldPaint);
        String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
        canvas.drawText(dateStr, 160, 158, paint);

        canvas.drawText("Payment:", 360, 158, boldPaint);
        canvas.drawText(paymentMethod != null ? paymentMethod : "Cash", 430, 158, paint);

        // Table Header
        int y = 200;
        paint.setColor(Color.parseColor("#000000"));
        canvas.drawRect(36, y, 559, y + 26, paint);

        Paint headerPaint = new Paint();
        headerPaint.setAntiAlias(true);
        headerPaint.setColor(Color.WHITE);
        headerPaint.setTextSize(11);
        headerPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("ITEM DESCRIPTION", 46, y + 17, headerPaint);
        canvas.drawText("QTY", 320, y + 17, headerPaint);
        canvas.drawText("PRICE (LKR)", 380, y + 17, headerPaint);
        canvas.drawText("TOTAL (LKR)", 470, y + 17, headerPaint);

        y += 38;
        paint.setColor(Color.BLACK);
        paint.setTextSize(10);

        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                BillingActivity.CartItemModel item = items.get(i);

                if (i % 2 == 1) {
                    Paint bgPaint = new Paint();
                    bgPaint.setColor(Color.parseColor("#F8FAFC"));
                    canvas.drawRect(36, y - 12, 559, y + 14, bgPaint);
                }

                String prodName = item.name != null ? item.name : "Product";
                if (prodName.length() > 38) {
                    prodName = prodName.substring(0, 35) + "...";
                }
                canvas.drawText(prodName, 46, y, paint);
                canvas.drawText(String.valueOf(item.quantity), 320, y, paint);

                double priceVal = item.activePrice != null ? item.activePrice.doubleValue() : item.price.doubleValue();
                double totalVal = item.total != null ? item.total.doubleValue() : (priceVal * item.quantity);

                canvas.drawText(String.format(Locale.getDefault(), "%,.2f", priceVal), 380, y, paint);
                canvas.drawText(String.format(Locale.getDefault(), "%,.2f", totalVal), 470, y, paint);

                y += 24;
                if (y > 700) {
                    break;
                }
            }
        }

        // Divider before summary
        paint.setColor(Color.parseColor("#CBD5E1"));
        canvas.drawLine(36, y + 5, 559, y + 5, paint);
        y += 25;

        // Totals summary
        boldPaint.setTextSize(11);
        canvas.drawText("Subtotal:", 350, y, boldPaint);
        canvas.drawText(CurrencyUtils.formatLKR(subtotal != null ? subtotal : BigDecimal.ZERO), 460, y, paint);
        y += 20;

        canvas.drawText("Discounts:", 350, y, boldPaint);
        canvas.drawText(CurrencyUtils.formatLKR(discount != null ? discount : BigDecimal.ZERO), 460, y, paint);
        y += 22;

        // Net total highlight
        Paint netBg = new Paint();
        netBg.setColor(Color.parseColor("#000000"));
        canvas.drawRect(330, y - 14, 559, y + 18, netBg);

        Paint netText = new Paint();
        netText.setAntiAlias(true);
        netText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        netText.setTextSize(13);
        netText.setColor(Color.WHITE);
        canvas.drawText("NET TOTAL:", 345, y + 4, netText);
        canvas.drawText(CurrencyUtils.formatLKR(grandTotal != null ? grandTotal : BigDecimal.ZERO), 460, y + 4, netText);

        // Footer
        y = 800;
        paint.setColor(Color.parseColor("#94A3B8"));
        paint.setTextSize(9);
        canvas.drawText("Thank you for your business! - Curtiss ERP System", 36, y, paint);
        canvas.drawText("Generated: " + dateStr, 420, y, paint);

        pdfDocument.finishPage(page);

        // Save File to accessible app storage
        String safeFileName = "Invoice_" + (invoiceNum != null ? invoiceNum.replaceAll("[^a-zA-Z0-9_-]", "_") : "DOC") + ".pdf";
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = context.getCacheDir();
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File pdfFile = new File(dir, safeFileName);
        try {
            FileOutputStream fos = new FileOutputStream(pdfFile);
            pdfDocument.writeTo(fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            android.util.Log.e("PdfInvoiceHelper", "Error writing PDF: " + e.getMessage(), e);
        } finally {
            pdfDocument.close();
        }

        return pdfFile;
    }

    public static void shareInvoiceViaWhatsApp(
            Activity activity,
            File pdfFile,
            String invoiceNum,
            String customerName,
            String customerPhone,
            BigDecimal grandTotal,
            String digitalInvoiceUrl
    ) {
        try {
            String cleanPhone = customerPhone;
            if (cleanPhone != null) {
                cleanPhone = cleanPhone.replaceAll("[^0-9]", "");
                if (cleanPhone.startsWith("0")) {
                    cleanPhone = "94" + cleanPhone.substring(1);
                }
            } else {
                cleanPhone = "";
            }

            StringBuilder msg = new StringBuilder();
            msg.append("Dear ").append(customerName != null ? customerName : "Customer").append(",\n\n");
            msg.append("Thank you for your business with Curtiss!\n");
            msg.append("📄 *Invoice No:* ").append(invoiceNum).append("\n");
            msg.append("💰 *Total Amount:* ").append(CurrencyUtils.formatLKR(grandTotal != null ? grandTotal : BigDecimal.ZERO)).append("\n\n");
            msg.append("Please find your official PDF Invoice attached to this message.\n");
            if (digitalInvoiceUrl != null && !digitalInvoiceUrl.isEmpty()) {
                msg.append("🌐 *View Live Online Receipt:*\n").append(digitalInvoiceUrl).append("\n");
            }

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_TEXT, msg.toString());

            if (pdfFile != null && pdfFile.exists()) {
                Uri fileUri = FileProvider.getUriForFile(
                        activity,
                        activity.getPackageName() + ".fileprovider",
                        pdfFile
                );
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            // Target WhatsApp specifically if available
            shareIntent.setPackage("com.whatsapp");

            try {
                activity.startActivity(shareIntent);
            } catch (Exception e) {
                // If direct WhatsApp package intent fails, open chooser without package restriction
                shareIntent.setPackage(null);
                activity.startActivity(Intent.createChooser(shareIntent, "Share Invoice via"));
            }
        } catch (Exception e) {
            Toast.makeText(activity, "Error sharing invoice: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            android.util.Log.e("PdfInvoiceHelper", "Share error", e);
        }
    }
}
