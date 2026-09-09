package com.example.curtiss;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CheckoutService {

    private static final String TAG = "CheckoutService";

    public interface CheckoutListener {
        void onCheckoutSuccess(String invoiceNum, String customerName, String phone, BigDecimal netTotal);
        void onCheckoutFailed(String message);
    }

    public static void performCheckout(
            final Context context,
            final DatabaseHelper dbHelper,
            final long editInvoiceId,
            final BillingActivity.CustomerModel customer,
            final long currentRouteLocalId,
            final List<BillingActivity.CartItemModel> cartList,
            final String paymentMethod,
            final BillingActivity.PaymentTermModel selectedTerm,
            final BigDecimal discount,
            final String discountType,
            final double discountRate,
            final double latitude,
            final double longitude,
            final CheckoutListener listener
    ) {
        if (customer == null) {
            if (listener != null) listener.onCheckoutFailed("Customer is not selected.");
            return;
        }
        if (cartList == null || cartList.isEmpty()) {
            if (listener != null) listener.onCheckoutFailed("Shopping cart is empty.");
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int dbRetries = 5;
        int dbAttempt = 0;
        boolean success = false;
        String finalInvoiceNum = "";
        BigDecimal finalNetTotal = BigDecimal.ZERO;

        while (dbAttempt < dbRetries) {
            dbAttempt++;
            try {
                db.beginTransaction();
                try {
                    String invoiceNum = "";
                    String uuidString = "";
                    String dateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

                    if (editInvoiceId != -1) {
                        Cursor origCursor = db.rawQuery("SELECT uuid, invoice_number FROM invoices WHERE id = ?", new String[]{String.valueOf(editInvoiceId)});
                        if (origCursor.moveToFirst()) {
                            uuidString = origCursor.getString(0);
                            invoiceNum = origCursor.getString(1);
                        }
                        origCursor.close();

                        // REVERT old stock reservations
                        Cursor oldItemsCursor = db.rawQuery("SELECT product_id, quantity FROM invoice_items WHERE invoice_id = ?", new String[]{String.valueOf(editInvoiceId)});
                        while (oldItemsCursor.moveToNext()) {
                            int oldProductId = oldItemsCursor.getInt(0);
                            int oldQty = oldItemsCursor.getInt(1);
                            db.execSQL(
                                "UPDATE products SET quantity_reserved = CASE WHEN (quantity_reserved - ?) < 0 THEN 0 ELSE (quantity_reserved - ?) END WHERE id = ?",
                                new Object[]{oldQty, oldQty, oldProductId}
                            );
                        }
                        oldItemsCursor.close();

                        // DELETE old items
                        db.delete("invoice_items", "invoice_id = ?", new String[]{String.valueOf(editInvoiceId)});
                    } else {
                        String todayDateCompact = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());

                        SharedPreferences seqPrefs = context.getSharedPreferences("CurtissPrefs", Context.MODE_PRIVATE);
                        int seq = seqPrefs.getInt("global_invoice_seq", 0);

                        if (seq == 0) {
                            Cursor maxCursor = db.rawQuery(
                                "SELECT invoice_number FROM invoices ORDER BY id DESC LIMIT 1", null
                            );
                            if (maxCursor != null) {
                                if (maxCursor.moveToFirst()) {
                                    String lastInvoiceNum = maxCursor.getString(0);
                                    if (lastInvoiceNum.length() >= 4) {
                                        try {
                                            String suffix = lastInvoiceNum.substring(lastInvoiceNum.length() - 4);
                                            seq = Integer.parseInt(suffix);
                                        } catch (Exception e) {
                                            seq = 0;
                                        }
                                    }
                                }
                                maxCursor.close();
                            }
                        }

                        seq++;
                        seqPrefs.edit().putInt("global_invoice_seq", seq).apply();

                        int userId = SecurePreferences.getSessionPrefs(context).getInt("user_id", 0);
                        invoiceNum = String.format(Locale.getDefault(), "%s%02d%04d", todayDateCompact, userId, seq);
                        uuidString = java.util.UUID.randomUUID().toString();
                    }

                    BigDecimal subtotal = BigDecimal.ZERO;
                    for (BillingActivity.CartItemModel item : cartList) {
                        subtotal = subtotal.add(item.total);
                    }

                    BigDecimal netTotal = subtotal.subtract(discount);
                    if (netTotal.compareTo(BigDecimal.ZERO) < 0) netTotal = BigDecimal.ZERO;
                    BigDecimal tax = BigDecimal.ZERO;

                    Integer paymentTermId = null;
                    int daysOffset = 0;
                    if (selectedTerm != null) {
                        paymentTermId = selectedTerm.id;
                        daysOffset = selectedTerm.daysDue;
                    }

                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    if (daysOffset > 0) {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, daysOffset);
                    }
                    String dueDateString = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(cal.getTime());

                    ContentValues cvHeader = new ContentValues();
                    cvHeader.put("customer_id", customer.id);
                    cvHeader.put("route_id", currentRouteLocalId);
                    cvHeader.put("invoice_date", dateString);
                    cvHeader.put("due_date", dueDateString);
                    if (paymentTermId != null) {
                        cvHeader.put("payment_term_id", paymentTermId);
                    } else {
                        cvHeader.putNull("payment_term_id");
                    }
                    cvHeader.put("subtotal", subtotal.doubleValue());
                    cvHeader.put("discount", discount.doubleValue());
                    cvHeader.put("discount_type", discountType);
                    cvHeader.put("discount_rate", discountRate);
                    cvHeader.put("tax", tax.doubleValue());
                    cvHeader.put("grand_total", netTotal.doubleValue());
                    cvHeader.put("payment_method", paymentMethod);
                    cvHeader.put("latitude", latitude);
                    cvHeader.put("longitude", longitude);
                    cvHeader.put("is_synced", 0);
                    cvHeader.put("sync_status", 1); // 1 = Pending Sync
                    cvHeader.put("sync_attempts", 0);
                    cvHeader.putNull("failure_reason");

                    long localInvId;
                    if (editInvoiceId != -1) {
                        db.update("invoices", cvHeader, "id = ?", new String[]{String.valueOf(editInvoiceId)});
                        localInvId = editInvoiceId;
                    } else {
                        cvHeader.put("invoice_number", invoiceNum);
                        cvHeader.put("uuid", uuidString);
                        localInvId = db.insert("invoices", null, cvHeader);
                    }

                    for (BillingActivity.CartItemModel item : cartList) {
                        ContentValues cvItem = new ContentValues();
                        cvItem.put("invoice_id", localInvId);
                        cvItem.put("product_id", item.productId);
                        cvItem.put("product_name", item.name);
                        cvItem.put("quantity", item.quantity);
                        cvItem.put("unit_price", item.activePrice.doubleValue());
                        cvItem.put("discount_val", item.discountVal.doubleValue());
                        cvItem.put("discount_type", item.isPercentDiscountActive ? "%" : "Rs");
                        double itemDiscountRate = item.isPercentDiscountActive ? item.discountPercent.doubleValue() : item.discountAmount.doubleValue();
                        cvItem.put("discount_rate", itemDiscountRate);
                        cvItem.put("total", item.total.doubleValue());
                        cvItem.put("selected_variation", item.selectedVariation != null ? item.selectedVariation : "");
                        cvItem.put("variation_option_id", item.variationOptionId);

                        db.insert("invoice_items", null, cvItem);
                        db.execSQL(
                            "UPDATE products SET quantity_reserved = quantity_reserved + ? WHERE id = ?",
                            new Object[]{item.quantity, item.productId}
                        );
                    }

                    // Create/update sync logs entry
                    try {
                        db.delete("sync_logs", "bill_id = ?", new String[]{String.valueOf(localInvId)});
                        ContentValues cvLog = new ContentValues();
                        cvLog.put("bill_id", localInvId);
                        cvLog.put("uuid", uuidString);
                        cvLog.put("created_time", dateString);
                        cvLog.put("upload_started", "");
                        cvLog.put("upload_completed", "");
                        cvLog.put("erp_response", "");
                        cvLog.put("failure_reason", "");
                        cvLog.put("retry_count", 0);
                        db.insert("sync_logs", null, cvLog);
                    } catch (Exception ex) {
                        Log.e(TAG, "Error updating sync log: " + ex.getMessage());
                    }

                    db.setTransactionSuccessful();
                    finalInvoiceNum = invoiceNum;
                    finalNetTotal = netTotal;
                    success = true;
                    break; // Success, break retry loop
                } finally {
                    db.endTransaction();
                }
            } catch (Exception e) {
                Log.w(TAG, "Database transaction failed (attempt " + dbAttempt + "/" + dbRetries + "): " + e.getMessage());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        }

        if (success) {
            if (listener != null) {
                listener.onCheckoutSuccess(finalInvoiceNum, customer.name, customer.phone, finalNetTotal);
            }
        } else {
            if (listener != null) {
                listener.onCheckoutFailed("Failed to record invoice after " + dbRetries + " retries.");
            }
        }
    }
}
