$file = "c:\xampp\htdocs\CURTISS\Curtiss ERP Rep App\app\src\main\java\com\example\curtiss\BillingActivity.java"
$lines = [System.IO.File]::ReadAllLines($file)

$newMethod = @'
    private void showProductConfigDialog(final ProductModel p, final CartItemModel existingItem) {
        final androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

        final float dp = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = (int) (screenW * 0.92f);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.setBackgroundColor(android.graphics.Color.WHITE);
        scrollView.setVerticalScrollBarEnabled(false);

        // Root container (Pure White, modern padding)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int)(20*dp), (int)(20*dp), (int)(20*dp), (int)(20*dp));
        layout.setBackgroundColor(android.graphics.Color.WHITE);

        // 1. HEADER BAR (Image left | Title + Price middle | Close button right)
        LinearLayout headerLayout = new LinearLayout(this);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.setMargins(0, 0, 0, (int)(16*dp));
        headerLayout.setLayoutParams(headerLp);

        // Product image in rounded light frame
        FrameLayout imgFrame = new FrameLayout(this);
        imgFrame.setBackground(createRoundedBackground("#F2F2F7", "#EBEBF0", 14));
        imgFrame.setClipToOutline(true);
        int imgSize = (int)(68*dp);
        LinearLayout.LayoutParams imgFrameLp = new LinearLayout.LayoutParams(imgSize, imgSize);
        imgFrame.setLayoutParams(imgFrameLp);

        ImageView imgProduct = new ImageView(this);
        imgProduct.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        imgProduct.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (p.localImagePath != null && !p.localImagePath.isEmpty()) {
            android.graphics.Bitmap bm = getCachedThumbnail(p.localImagePath, 1);
            if (bm != null) imgProduct.setImageBitmap(bm);
            else imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            imgProduct.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        imgFrame.addView(imgProduct);
        headerLayout.addView(imgFrame);

        // Title and price text container
        LinearLayout titleTextLayout = new LinearLayout(this);
        titleTextLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleTextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleTextParams.setMargins((int)(14 * dp), 0, (int)(8 * dp), 0);
        titleTextLayout.setLayoutParams(titleTextParams);

        TextView txtTitle = new TextView(this);
        txtTitle.setText(p.name);
        txtTitle.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        txtTitle.setTextSize(19);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        titleTextLayout.addView(txtTitle);

        TextView txtSub = new TextView(this);
        txtSub.setText("Wholesale: LKR " + String.format(Locale.getDefault(), "%,.2f", p.wholesalePrice.doubleValue()));
        txtSub.setTextColor(android.graphics.Color.parseColor("#5856D6"));
        txtSub.setTextSize(13);
        txtSub.setTypeface(null, android.graphics.Typeface.BOLD);
        txtSub.setPadding(0, (int)(4*dp), 0, 0);
        titleTextLayout.addView(txtSub);

        headerLayout.addView(titleTextLayout);

        // Top-right close circular button
        android.widget.TextView btnCloseHeader = new android.widget.TextView(this);
        btnCloseHeader.setText("âœ•");
        btnCloseHeader.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnCloseHeader.setTextSize(16);
        btnCloseHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCloseHeader.setGravity(android.view.Gravity.CENTER);
        btnCloseHeader.setBackground(createRoundedBackground("#F2F2F7", "#F2F2F7", 18));
        LinearLayout.LayoutParams closeHeadLp = new LinearLayout.LayoutParams((int)(36*dp), (int)(36*dp));
        btnCloseHeader.setLayoutParams(closeHeadLp);
        headerLayout.addView(btnCloseHeader);

        layout.addView(headerLayout);

        // Selected Variation (Horizontal Scroll View of Cards)
        final boolean hasVariations = hasProductVariations(p);
        final int[] selectedVarIndex = new int[]{-1};
        final boolean[] itemAdded = new boolean[]{false};

        org.json.JSONArray parsedVars = null;
        if (hasVariations) {
            try {
                parsedVars = new org.json.JSONArray(p.variationsJson);
                if (existingItem != null && existingItem.selectedVariation != null && !existingItem.selectedVariation.isEmpty()) {
                    for (int i = 0; i < parsedVars.length(); i++) {
                        org.json.JSONObject vObj = parsedVars.getJSONObject(i);
                        String name = vObj.optString("attribute", vObj.optString("option_name", ""));
                        if (name.equals(existingItem.selectedVariation)) {
                            selectedVarIndex[0] = i;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("BillingActivity", "Error parsing variations JSON: " + e.getMessage());
            }
        }
        final org.json.JSONArray vars = parsedVars;

        HorizontalScrollView varScrollView = new HorizontalScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        scrollParams.setMargins(0, 0, 0, (int)(14 * dp));
        varScrollView.setLayoutParams(scrollParams);
        varScrollView.setHorizontalScrollBarEnabled(false);

        final LinearLayout varLayout = new LinearLayout(this);
        varLayout.setOrientation(LinearLayout.HORIZONTAL);
        varLayout.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, 
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        varScrollView.addView(varLayout);

        if (hasVariations) {
            TextView lblVar = new TextView(this);
            lblVar.setText("SELECT A VARIATION");
            lblVar.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
            lblVar.setTextSize(11);
            lblVar.setTypeface(null, android.graphics.Typeface.BOLD);
            lblVar.setPadding(0, 0, 0, (int)(8 * dp));
            layout.addView(lblVar);
            layout.addView(varScrollView);
        }

        // 2. UNIT PRICE OVERRIDE CARD
        LinearLayout priceCard = new LinearLayout(this);
        priceCard.setOrientation(LinearLayout.VERTICAL);
        priceCard.setBackground(createRoundedBackground("#F8F8FB", "#EBEBF0", 16));
        priceCard.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        LinearLayout.LayoutParams priceCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        priceCardLp.setMargins(0, 0, 0, (int)(12*dp));
        priceCard.setLayoutParams(priceCardLp);

        TextView lblOverride = new TextView(this);
        lblOverride.setText("UNIT PRICE (LKR)");
        lblOverride.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblOverride.setTextSize(11);
        lblOverride.setTypeface(null, android.graphics.Typeface.BOLD);
        lblOverride.setPadding(0, 0, 0, (int)(8*dp));
        priceCard.addView(lblOverride);

        final EditText edtOverridePrice = new EditText(this);
        edtOverridePrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtOverridePrice.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtOverridePrice.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        java.math.BigDecimal initialPrice = (existingItem != null && existingItem.customPrice.compareTo(java.math.BigDecimal.ZERO) > 0) ? existingItem.customPrice : p.wholesalePrice;
        edtOverridePrice.setText(initialPrice.toPlainString());
        edtOverridePrice.setTextSize(17);
        edtOverridePrice.setTypeface(null, android.graphics.Typeface.BOLD);
        edtOverridePrice.setBackground(createRoundedBackground("#FFFFFF", "#E0E0E5", 10));
        edtOverridePrice.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        priceCard.addView(edtOverridePrice);
        layout.addView(priceCard);

        // 3. QUANTITY SELECTOR CARD (Apple/POS Stepper layout)
        LinearLayout qtyCard = new LinearLayout(this);
        qtyCard.setOrientation(LinearLayout.HORIZONTAL);
        qtyCard.setGravity(android.view.Gravity.CENTER_VERTICAL);
        qtyCard.setBackground(createRoundedBackground("#F8F8FB", "#EBEBF0", 16));
        qtyCard.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        LinearLayout.LayoutParams qtyCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qtyCardLp.setMargins(0, 0, 0, (int)(12*dp));
        qtyCard.setLayoutParams(qtyCardLp);

        LinearLayout qtyLeft = new LinearLayout(this);
        qtyLeft.setOrientation(LinearLayout.VERTICAL);
        qtyLeft.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblQty = new TextView(this);
        lblQty.setText("QUANTITY");
        lblQty.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        lblQty.setTextSize(14);
        lblQty.setTypeface(null, android.graphics.Typeface.BOLD);
        qtyLeft.addView(lblQty);

        TextView lblQtyHint = new TextView(this);
        lblQtyHint.setText("Tap or hold âˆ’ / +");
        lblQtyHint.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblQtyHint.setTextSize(11);
        lblQtyHint.setPadding(0, (int)(2*dp), 0, 0);
        qtyLeft.addView(lblQtyHint);
        qtyCard.addView(qtyLeft);

        // Stepper container on right
        LinearLayout stepperBox = new LinearLayout(this);
        stepperBox.setOrientation(LinearLayout.HORIZONTAL);
        stepperBox.setGravity(android.view.Gravity.CENTER_VERTICAL);
        stepperBox.setBackground(createRoundedBackground("#EBEBF0", "#EBEBF0", 24));
        stepperBox.setPadding((int)(4*dp), (int)(4*dp), (int)(4*dp), (int)(4*dp));

        Button btnMinus = new Button(this);
        btnMinus.setText("âˆ’");
        btnMinus.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnMinus.setTextSize(20);
        btnMinus.setTypeface(null, android.graphics.Typeface.BOLD);
        btnMinus.setBackground(createRoundedBackground("#FFFFFF", "#FFFFFF", 20));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams((int)(42*dp), (int)(42*dp));
        btnMinus.setLayoutParams(btnParams);

        final EditText edtQtyInput = new EditText(this);
        edtQtyInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edtQtyInput.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtQtyInput.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        int initialQty = existingItem != null ? existingItem.quantity : 1;
        edtQtyInput.setText(String.valueOf(initialQty));
        edtQtyInput.setTextSize(20);
        edtQtyInput.setTypeface(null, android.graphics.Typeface.BOLD);
        edtQtyInput.setGravity(android.view.Gravity.CENTER);
        edtQtyInput.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        LinearLayout.LayoutParams qtyInputLp = new LinearLayout.LayoutParams((int)(64*dp), ViewGroup.LayoutParams.WRAP_CONTENT);
        edtQtyInput.setLayoutParams(qtyInputLp);

        Button btnPlus = new Button(this);
        btnPlus.setText("+");
        btnPlus.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnPlus.setTextSize(20);
        btnPlus.setTypeface(null, android.graphics.Typeface.BOLD);
        btnPlus.setBackground(createRoundedBackground("#FFFFFF", "#FFFFFF", 20));
        btnPlus.setLayoutParams(btnParams);

        stepperBox.addView(btnMinus);
        stepperBox.addView(edtQtyInput);
        stepperBox.addView(btnPlus);
        qtyCard.addView(stepperBox);
        layout.addView(qtyCard);

        final android.os.Handler autoIncrementHandler = new android.os.Handler();
        class AutoRepeater implements View.OnTouchListener {
            private final int direction; // -1 for dec, 1 for inc
            private boolean isPressed = false;
            
            AutoRepeater(int direction) {
                this.direction = direction;
            }
            
            private final Runnable updateTask = new Runnable() {
                @Override
                public void run() {
                    if (!isPressed) return;
                    changeQty();
                    autoIncrementHandler.postDelayed(this, 100);
                }
            };
            
            private void changeQty() {
                try {
                    String val = edtQtyInput.getText().toString().trim();
                    int current = val.isEmpty() ? 0 : Integer.parseInt(val);
                    int next = current + direction;
                    if (next < 1) next = 1;
                    edtQtyInput.setText(String.valueOf(next));
                    edtQtyInput.setSelection(edtQtyInput.getText().length());
                } catch (Exception ignored) {}
            }
            
            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch(event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        isPressed = true;
                        changeQty();
                        autoIncrementHandler.postDelayed(updateTask, 400);
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        isPressed = false;
                        autoIncrementHandler.removeCallbacks(updateTask);
                        return true;
                }
                return false;
            }
        }
        
        btnMinus.setOnTouchListener(new AutoRepeater(-1));
        btnPlus.setOnTouchListener(new AutoRepeater(1));

        // 4. DISCOUNT CARD (Optional)
        LinearLayout discountCard = new LinearLayout(this);
        discountCard.setOrientation(LinearLayout.VERTICAL);
        discountCard.setBackground(createRoundedBackground("#F8F8FB", "#EBEBF0", 16));
        discountCard.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        LinearLayout.LayoutParams discountCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        discountCardLp.setMargins(0, 0, 0, (int)(14*dp));
        discountCard.setLayoutParams(discountCardLp);

        TextView lblDiscountHeader = new TextView(this);
        lblDiscountHeader.setText("DISCOUNTS (OPTIONAL)");
        lblDiscountHeader.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
        lblDiscountHeader.setTextSize(11);
        lblDiscountHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        lblDiscountHeader.setPadding(0, 0, 0, (int)(10*dp));
        discountCard.addView(lblDiscountHeader);

        LinearLayout discountRow = new LinearLayout(this);
        discountRow.setOrientation(LinearLayout.HORIZONTAL);

        // Percentage discount
        LinearLayout colPct = new LinearLayout(this);
        colPct.setOrientation(LinearLayout.VERTICAL);
        colPct.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblPct = new TextView(this);
        lblPct.setText("DISCOUNT (%)");
        lblPct.setTextColor(android.graphics.Color.parseColor("#636366"));
        lblPct.setTextSize(10);
        lblPct.setTypeface(null, android.graphics.Typeface.BOLD);
        lblPct.setPadding(0, 0, 0, (int)(6*dp));
        colPct.addView(lblPct);

        final EditText edtDiscountPct = new EditText(this);
        edtDiscountPct.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtDiscountPct.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtDiscountPct.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        edtDiscountPct.setHint("0.0%");
        if (existingItem != null && existingItem.discountPercent.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountPct.setText(existingItem.discountPercent.toPlainString());
        }
        edtDiscountPct.setTextSize(15);
        edtDiscountPct.setTypeface(null, android.graphics.Typeface.BOLD);
        edtDiscountPct.setBackground(createRoundedBackground("#FFFFFF", "#E0E0E5", 10));
        edtDiscountPct.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        colPct.addView(edtDiscountPct);
        discountRow.addView(colPct);

        // Spacer
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams((int)(16*dp), 1));
        discountRow.addView(spacer);

        // Amount discount
        LinearLayout colAmt = new LinearLayout(this);
        colAmt.setOrientation(LinearLayout.VERTICAL);
        colAmt.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView lblAmt = new TextView(this);
        lblAmt.setText("DISCOUNT (LKR)");
        lblAmt.setTextColor(android.graphics.Color.parseColor("#636366"));
        lblAmt.setTextSize(10);
        lblAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        lblAmt.setPadding(0, 0, 0, (int)(6*dp));
        colAmt.addView(lblAmt);

        final EditText edtDiscountAmt = new EditText(this);
        edtDiscountAmt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtDiscountAmt.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        edtDiscountAmt.setHintTextColor(android.graphics.Color.parseColor("#AEAEB2"));
        edtDiscountAmt.setHint("0.00");
        if (existingItem != null && existingItem.discountAmount.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountAmt.setText(existingItem.discountAmount.toPlainString());
        } else if (existingItem != null && existingItem.discountVal.compareTo(java.math.BigDecimal.ZERO) > 0) {
            edtDiscountAmt.setText(existingItem.discountVal.toPlainString());
        }
        edtDiscountAmt.setTextSize(15);
        edtDiscountAmt.setTypeface(null, android.graphics.Typeface.BOLD);
        edtDiscountAmt.setBackground(createRoundedBackground("#FFFFFF", "#E0E0E5", 10));
        edtDiscountAmt.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        colAmt.addView(edtDiscountAmt);
        discountRow.addView(colAmt);

        discountCard.addView(discountRow);
        layout.addView(discountCard);

        // 5. LIVE TOTAL PREVIEW BANNER
        final TextView txtLiveTotal = new TextView(this);
        java.math.BigDecimal currentTotal = (existingItem != null) ? existingItem.total : p.wholesalePrice;
        txtLiveTotal.setText("TOTAL: " + CurrencyUtils.formatLKR(currentTotal));
        txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        txtLiveTotal.setTextSize(19);
        txtLiveTotal.setTypeface(null, android.graphics.Typeface.BOLD);
        txtLiveTotal.setBackground(createRoundedBackground("#F2F2F7", "#E5E5EA", 16));
        txtLiveTotal.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        txtLiveTotal.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerLp.setMargins(0, 0, 0, (int)(16*dp));
        txtLiveTotal.setLayoutParams(bannerLp);
        layout.addView(txtLiveTotal);

        // 6. ACTION BUTTONS ROW
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final Button btnCancel = new Button(this);
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
        btnCancel.setTextSize(15);
        btnCancel.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancel.setAllCaps(false);
        btnCancel.setBackground(createRoundedBackground("#F2F2F7", "#F2F2F7", 25));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, (int)(50*dp), 1.0f);
        btnCancel.setLayoutParams(cancelParams);
        btnRow.addView(btnCancel);

        if (existingItem != null) {
            Button btnRemove = new Button(this);
            btnRemove.setText("Remove");
            btnRemove.setTextColor(android.graphics.Color.WHITE);
            btnRemove.setTextSize(15);
            btnRemove.setTypeface(null, android.graphics.Typeface.BOLD);
            btnRemove.setAllCaps(false);
            btnRemove.setBackground(createRoundedBackground("#FF3B30", "#FF3B30", 25));
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, (int)(50*dp), 1.0f);
            removeParams.setMargins((int)(8 * dp), 0, (int)(8 * dp), 0);
            btnRemove.setLayoutParams(removeParams);
            btnRow.addView(btnRemove);

            btnRemove.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this)
                        .setTitle("Remove Item")
                        .setMessage("Remove this item from the cart?")
                        .setPositiveButton("Remove", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialogConfirm, int which) {
                                cartList.remove(existingItem);
                                boolean checkoutMode = (layoutCartCheckoutMode != null && layoutCartCheckoutMode.getVisibility() == View.VISIBLE);
                                if (cartAdapter != null) {
                                    cartAdapter.notifyDataSetChanged();
                                }
                                recalculateCart();
                                showCartOverlay(checkoutMode);
                                dialogConfirm.dismiss();
                                builder.create().dismiss();
                                Toast.makeText(BillingActivity.this, p.name + " removed from cart.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                }
            });
        } else {
            View spacerBtn = new View(this);
            spacerBtn.setLayoutParams(new LinearLayout.LayoutParams((int)(12 * dp), 1));
            btnRow.addView(spacerBtn);
        }

        final Button btnAction = new Button(this);
        btnAction.setText(existingItem != null ? "Update" : "Add to Cart");
        btnAction.setTextColor(android.graphics.Color.WHITE);
        btnAction.setTextSize(15);
        btnAction.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAction.setAllCaps(false);
        btnAction.setBackground(createRoundedBackground("#1C1C1E", "#1C1C1E", 25));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, (int)(50*dp), 1.5f);
        btnAction.setLayoutParams(actionParams);
        btnRow.addView(btnAction);
        layout.addView(btnRow);

        scrollView.addView(layout);
        builder.setView(scrollView);
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(createRoundedBackground("#FFFFFF", "#FFFFFF", 28));
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        btnCloseHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // Control State Management
        final Runnable updateControlsState = new Runnable() {
            @Override
            public void run() {
                boolean hasSelection = !hasVariations || selectedVarIndex[0] != -1;
                
                edtQtyInput.setEnabled(hasSelection);
                btnMinus.setEnabled(hasSelection);
                btnPlus.setEnabled(hasSelection);
                edtDiscountPct.setEnabled(hasSelection);
                edtDiscountAmt.setEnabled(hasSelection);
                edtOverridePrice.setEnabled(hasSelection);
                btnAction.setEnabled(hasSelection);
                
                if (!hasSelection) {
                    btnAction.setText("Select a Variation");
                    btnAction.setBackground(createRoundedBackground("#E5E5EA", "#E5E5EA", 25));
                    btnAction.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                    txtLiveTotal.setText("SELECT A VARIATION");
                    txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#8E8E93"));
                } else {
                    btnAction.setText(existingItem != null ? "Update" : "Add to Cart");
                    btnAction.setBackground(createRoundedBackground("#1C1C1E", "#1C1C1E", 25));
                    btnAction.setTextColor(android.graphics.Color.WHITE);
                    txtLiveTotal.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                    
                    if (hasVariations) {
                        try {
                            org.json.JSONObject vObj = vars.getJSONObject(selectedVarIndex[0]);
                            String varName = vObj.optString("attribute", vObj.optString("option_name", ""));
                            int totalStock = vObj.optInt("qty", vObj.optInt("quantity_on_hand", 0));
                            int reservedStock = dbHelper.getVariationReservedQty(p.id, varName);
                            int cartQty = 0;
                            for (CartItemModel item : cartList) {
                                if (item.productId == p.id && item.selectedVariation.equals(varName)) {
                                    if (existingItem == null || !existingItem.selectedVariation.equals(varName)) {
                                        cartQty += item.quantity;
                                    }
                                }
                            }
                            int available = totalStock - reservedStock - cartQty;
                            if (available <= 0) {
                                btnAction.setEnabled(false);
                                btnAction.setText("Out of Stock");
                                btnAction.setBackground(createRoundedBackground("#FF3B30", "#FF3B30", 25));
                                btnAction.setTextColor(android.graphics.Color.WHITE);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        };

        // Render variation tiles
        final Runnable renderVariationTiles = new Runnable() {
            @Override
            public void run() {
                varLayout.removeAllViews();
                if (!hasVariations || vars == null) return;
                
                try {
                    for (int i = 0; i < vars.length(); i++) {
                        final int index = i;
                        final org.json.JSONObject vObj = vars.getJSONObject(i);
                        final String varName = vObj.optString("attribute", vObj.optString("option_name", ""));
                        final int totalStock = vObj.optInt("qty", vObj.optInt("quantity_on_hand", 0));
                        double calculatedVarPrice = vObj.optDouble("wholesale_price", 0.0);
                        if (calculatedVarPrice <= 0) {
                            calculatedVarPrice = p.wholesalePrice.doubleValue();
                        }
                        if (calculatedVarPrice <= 0) {
                            calculatedVarPrice = vObj.optDouble("price", 0.0);
                        }
                        final double varPrice = calculatedVarPrice;

                        // Calculate real-time available stock
                        int reservedStock = dbHelper.getVariationReservedQty(p.id, varName);
                        int cartQty = 0;
                        for (CartItemModel item : cartList) {
                            if (item.productId == p.id && item.selectedVariation.equals(varName)) {
                                if (existingItem == null || !existingItem.selectedVariation.equals(varName)) {
                                    cartQty += item.quantity;
                                }
                            }
                        }
                        final int available = totalStock - reservedStock - cartQty;
                        
                        // Outer tile layout
                        LinearLayout tile = new LinearLayout(BillingActivity.this);
                        tile.setOrientation(LinearLayout.VERTICAL);
                        tile.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
                        
                        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(
                            (int) (105 * dp),
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        tileParams.setMargins(0, 0, (int) (10 * dp), 0);
                        tile.setLayoutParams(tileParams);
                        tile.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
                        
                        // Styling: light theme rounded card & selection border
                        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                        gd.setCornerRadius(14 * dp);
                        if (selectedVarIndex[0] == index) {
                            gd.setColor(android.graphics.Color.WHITE);
                            gd.setStroke((int) (2 * dp), android.graphics.Color.parseColor("#1C1C1E"));
                        } else {
                            gd.setColor(android.graphics.Color.parseColor("#F8F8FB"));
                            gd.setStroke((int) (1 * dp), android.graphics.Color.parseColor("#EBEBF0"));
                        }
                        tile.setBackground(gd);
                        
                        // 1. Variation Image
                        ImageView imgVar = new ImageView(BillingActivity.this);
                        LinearLayout.LayoutParams imgVarParams = new LinearLayout.LayoutParams(
                            (int) (56 * dp),
                            (int) (56 * dp)
                        );
                        imgVarParams.setMargins(0, 0, 0, (int) (6 * dp));
                        imgVar.setLayoutParams(imgVarParams);
                        imgVar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        
                        String varImgPath = vObj.optString("image_path", vObj.optString("image", ""));
                        boolean loaded = false;
                        if (!varImgPath.isEmpty()) {
                            android.graphics.Bitmap bm = getCachedThumbnail(varImgPath, 2);
                            if (bm != null) {
                                imgVar.setImageBitmap(bm);
                                loaded = true;
                            }
                        }
                        if (!loaded && p.localImagePath != null && !p.localImagePath.isEmpty()) {
                            android.graphics.Bitmap bm = getCachedThumbnail(p.localImagePath, 2);
                            if (bm != null) {
                                imgVar.setImageBitmap(bm);
                                loaded = true;
                            }
                        }
                        if (!loaded) {
                            imgVar.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                        tile.addView(imgVar);
                        
                        // 2. Variation Name
                        TextView txtVarName = new TextView(BillingActivity.this);
                        txtVarName.setText(varName);
                        txtVarName.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                        txtVarName.setTextSize(12);
                        txtVarName.setTypeface(null, android.graphics.Typeface.BOLD);
                        txtVarName.setGravity(android.view.Gravity.CENTER);
                        txtVarName.setSingleLine(true);
                        txtVarName.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        tile.addView(txtVarName);
                        
                        // 3. Stock badge
                        TextView txtStockBadge = new TextView(BillingActivity.this);
                        txtStockBadge.setTextSize(10);
                        txtStockBadge.setTypeface(null, android.graphics.Typeface.BOLD);
                        txtStockBadge.setGravity(android.view.Gravity.CENTER);
                        txtStockBadge.setPadding((int) (6 * dp), (int) (2 * dp), (int) (6 * dp), (int) (2 * dp));
                        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        badgeParams.setMargins(0, (int) (6 * dp), 0, 0);
                        txtStockBadge.setLayoutParams(badgeParams);
                        
                        android.graphics.drawable.GradientDrawable bgBadge = new android.graphics.drawable.GradientDrawable();
                        bgBadge.setCornerRadius(20 * dp);
                        if (available <= 0) {
                            txtStockBadge.setText("OUT OF STOCK");
                            txtStockBadge.setTextColor(android.graphics.Color.WHITE);
                            bgBadge.setColor(android.graphics.Color.parseColor("#FF3B30"));
                        } else {
                            txtStockBadge.setText("STOCK: " + available);
                            txtStockBadge.setTextColor(android.graphics.Color.WHITE);
                            bgBadge.setColor(android.graphics.Color.parseColor("#30D158"));
                        }
                        txtStockBadge.setBackground(bgBadge);
                        tile.addView(txtStockBadge);
                        
                        tile.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int oldSelection = selectedVarIndex[0];
                                selectedVarIndex[0] = index;
                                renderVariationTiles.run();
                                updateControlsState.run();
                                
                                if (oldSelection != index) {
                                    edtOverridePrice.setText(String.format(Locale.getDefault(), "%.2f", varPrice));
                                    edtQtyInput.requestFocus();
                                    edtQtyInput.selectAll();
                                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) {
                                        imm.showSoftInput(edtQtyInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                                    }
                                }
                            }
                        });
                        
                        varLayout.addView(tile);
                    }
                } catch (Exception e) {
                    android.util.Log.e("BillingActivity", "Error rendering variations: " + e.getMessage());
                }
            }
        };

        // Focus & soft keyboard on launch if no variations
        if (!hasVariations) {
            edtQtyInput.requestFocus();
            edtQtyInput.postDelayed(new Runnable() {
                @Override
                public void run() {
                    edtQtyInput.selectAll();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.showSoftInput(edtQtyInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                    }
                }
            }, 150);
        }

        final boolean[] percentActiveHolder = new boolean[]{ existingItem != null && existingItem.isPercentDiscountActive };

        // Live preview update
        final Runnable updatePreview = new Runnable() {
            private boolean isUpdating = false;
            @Override
            public void run() {
                if (isUpdating) return;
                isUpdating = true;
                try {
                    String overridePriceStr = edtOverridePrice.getText().toString().trim();
                    java.math.BigDecimal uPrice = overridePriceStr.isEmpty() ? p.wholesalePrice : CurrencyUtils.toBigDecimal(overridePriceStr);

                    String qtyStr = edtQtyInput.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

                    java.math.BigDecimal sub = uPrice.multiply(java.math.BigDecimal.valueOf(qty));
                    java.math.BigDecimal discountVal = java.math.BigDecimal.ZERO;

                    String pctStr = edtDiscountPct.getText().toString().trim();
                    String amtStr = edtDiscountAmt.getText().toString().trim();

                    if (edtDiscountPct.hasFocus()) {
                        percentActiveHolder[0] = true;
                        if (!pctStr.isEmpty()) {
                            java.math.BigDecimal pct = CurrencyUtils.toBigDecimal(pctStr);
                            discountVal = CurrencyUtils.calculatePercentage(sub, pct);
                            edtDiscountAmt.setText(String.format(Locale.getDefault(), "%.2f", discountVal));
                        } else {
                            edtDiscountAmt.setText("");
                        }
                    } else if (edtDiscountAmt.hasFocus()) {
                        percentActiveHolder[0] = false;
                        if (!amtStr.isEmpty()) {
                            discountVal = CurrencyUtils.toBigDecimal(amtStr);
                            java.math.BigDecimal pct = sub.compareTo(java.math.BigDecimal.ZERO) > 0 ? discountVal.multiply(java.math.BigDecimal.valueOf(100)).divide(sub, 1, java.math.RoundingMode.HALF_UP) : java.math.BigDecimal.ZERO;
                            edtDiscountPct.setText(String.format(Locale.getDefault(), "%.1f", pct));
                        } else {
                            edtDiscountPct.setText("");
                        }
                    } else {
                        if (percentActiveHolder[0]) {
                            if (!pctStr.isEmpty()) {
                                java.math.BigDecimal pct = CurrencyUtils.toBigDecimal(pctStr);
                                discountVal = CurrencyUtils.calculatePercentage(sub, pct);
                            }
                        } else {
                            if (!amtStr.isEmpty()) {
                                discountVal = CurrencyUtils.toBigDecimal(amtStr);
                            }
                        }
                    }

                    java.math.BigDecimal finalTotal = sub.subtract(discountVal);
                    if (finalTotal.compareTo(java.math.BigDecimal.ZERO) < 0) finalTotal = java.math.BigDecimal.ZERO;
                    
                    if (hasVariations && selectedVarIndex[0] == -1) {
                        txtLiveTotal.setText("SELECT A VARIATION");
                    } else {
                        txtLiveTotal.setText("TOTAL: " + CurrencyUtils.formatLKR(finalTotal));
                    }
                } catch (Exception ignored) {}
                isUpdating = false;
            }
        };

        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview.run();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        };

        edtOverridePrice.addTextChangedListener(watcher);
        edtQtyInput.addTextChangedListener(watcher);
        edtDiscountPct.addTextChangedListener(watcher);
        edtDiscountAmt.addTextChangedListener(watcher);

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String overridePriceStr = edtOverridePrice.getText().toString().trim();
                    java.math.BigDecimal uPrice = overridePriceStr.isEmpty() ? p.wholesalePrice : CurrencyUtils.toBigDecimal(overridePriceStr);

                    String qtyStr = edtQtyInput.getText().toString().trim();
                    int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);

                    java.math.BigDecimal pct = java.math.BigDecimal.ZERO;
                    String pctStr = edtDiscountPct.getText().toString().trim();
                    if (!pctStr.isEmpty()) pct = CurrencyUtils.toBigDecimal(pctStr);

                    java.math.BigDecimal amt = java.math.BigDecimal.ZERO;
                    String amtStr = edtDiscountAmt.getText().toString().trim();
                    if (!amtStr.isEmpty()) amt = CurrencyUtils.toBigDecimal(amtStr);

                    String selectedVar = "";
                    int selectedVarId = 0;
                    if (hasVariations && selectedVarIndex[0] != -1 && vars != null) {
                        org.json.JSONObject vObj = vars.getJSONObject(selectedVarIndex[0]);
                        selectedVar = vObj.optString("attribute", vObj.optString("option_name", ""));
                        selectedVarId = vObj.optInt("id", 0);
                    }

                    if (hasVariations && selectedVarIndex[0] == -1) {
                        Toast.makeText(BillingActivity.this, "Please select a variation.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int left;
                    if (hasVariations) {
                        org.json.JSONObject vObj = vars.getJSONObject(selectedVarIndex[0]);
                        int totalStock = vObj.optInt("qty", vObj.optInt("quantity_on_hand", 0));
                        int reservedStock = dbHelper.getVariationReservedQty(p.id, selectedVar);
                        int cartQty = 0;
                        for (CartItemModel item : cartList) {
                            if (item.productId == p.id && item.selectedVariation.equals(selectedVar)) {
                                if (existingItem == null || !existingItem.selectedVariation.equals(selectedVar)) {
                                    cartQty += item.quantity;
                                }
                            }
                        }
                        left = totalStock - reservedStock - cartQty;
                    } else {
                        left = p.qtyOnHand - p.qtyReserved;
                        int cartQty = 0;
                        for (CartItemModel item : cartList) {
                            if (item.productId == p.id) {
                                if (existingItem == null) {
                                    cartQty += item.quantity;
                                }
                            }
                        }
                        left -= cartQty;
                    }

                    int oldQty = existingItem != null ? existingItem.quantity : 0;
                    if (left + oldQty <= 0) {
                        Toast.makeText(BillingActivity.this, "Out of Stock.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (qty <= 0) {
                        Toast.makeText(BillingActivity.this, "Enter valid quantity.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (qty > left + oldQty) {
                        Toast.makeText(BillingActivity.this, "Insufficient stock available offline.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (existingItem != null) {
                        existingItem.quantity = qty;
                        existingItem.customPrice = uPrice;
                        existingItem.activePrice = uPrice;
                        existingItem.discountPercent = pct;
                        existingItem.discountAmount = amt;
                        existingItem.isPercentDiscountActive = percentActiveHolder[0];
                        existingItem.selectedVariation = selectedVar;
                        existingItem.variationOptionId = selectedVarId;
                        
                        if (hasVariations && !selectedVar.isEmpty()) {
                            existingItem.name = p.name + " - " + selectedVar;
                        } else {
                            existingItem.name = p.name;
                        }

                        java.math.BigDecimal subItem = uPrice.multiply(java.math.BigDecimal.valueOf(qty));
                        java.math.BigDecimal discountValItem = java.math.BigDecimal.ZERO;
                        if (existingItem.isPercentDiscountActive && pct.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            discountValItem = CurrencyUtils.calculatePercentage(subItem, pct);
                        } else if (!existingItem.isPercentDiscountActive && amt.compareTo(java.math.BigDecimal.ZERO) > 0) {
                            discountValItem = amt;
                        }
                        existingItem.discountVal = discountValItem;
                        existingItem.total = subItem.subtract(discountValItem);
                        if (existingItem.total.compareTo(java.math.BigDecimal.ZERO) < 0) existingItem.total = java.math.BigDecimal.ZERO;

                        boolean checkoutMode = (layoutCartCheckoutMode != null && layoutCartCheckoutMode.getVisibility() == View.VISIBLE);
                        if (cartAdapter != null) {
                            cartAdapter.notifyDataSetChanged();
                        }
                        recalculateCart();
                        checkAndPromptItemDiscountsOnAddToCart();
                        showCartOverlay(checkoutMode);
                        dialog.dismiss();
                        Toast.makeText(BillingActivity.this, "Cart updated.", Toast.LENGTH_SHORT).show();
                    } else {
                        CartItemModel duplicate = null;
                        for (CartItemModel item : cartList) {
                            boolean matches = false;
                            if (item.productId == p.id) {
                                if (hasVariations) {
                                    matches = item.selectedVariation != null && item.selectedVariation.equals(selectedVar);
                                } else {
                                    matches = (item.selectedVariation == null || item.selectedVariation.isEmpty());
                                }
                            }
                            if (matches) {
                                duplicate = item;
                                break;
                            }
                        }

                        final String finalSelectedVar = selectedVar;
                        final int finalSelectedVarId = selectedVarId;
                        final java.math.BigDecimal finalUPrice = uPrice;
                        final java.math.BigDecimal finalPct = pct;
                        final java.math.BigDecimal finalAmt = amt;
                        final boolean finalIsPercent = percentActiveHolder[0];
                        final int finalQty = qty;

                        if (duplicate != null) {
                            androidx.appcompat.app.AlertDialog.Builder warnBuilder = new androidx.appcompat.app.AlertDialog.Builder(BillingActivity.this);
                            
                            LinearLayout warnLayout = new LinearLayout(BillingActivity.this);
                            warnLayout.setOrientation(LinearLayout.VERTICAL);
                            warnLayout.setPadding((int)(24*dp), (int)(24*dp), (int)(24*dp), (int)(24*dp));
                            warnLayout.setBackgroundColor(android.graphics.Color.WHITE);
                            
                            TextView titleText = new TextView(BillingActivity.this);
                            titleText.setText("Item Already in Cart");
                            titleText.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                            titleText.setTextSize(18);
                            titleText.setTypeface(null, android.graphics.Typeface.BOLD);
                            titleText.setPadding(0, 0, 0, (int)(12*dp));
                            warnLayout.addView(titleText);
                            
                            TextView messageText = new TextView(BillingActivity.this);
                            messageText.setText("This item is already in your cart. Would you like to merge the quantity into the existing item, keep both as separate line items, or cancel?");
                            messageText.setTextColor(android.graphics.Color.parseColor("#636366"));
                            messageText.setTextSize(14);
                            messageText.setPadding(0, 0, 0, (int)(20*dp));
                            warnLayout.addView(messageText);
                            
                            LinearLayout warnBtnRow = new LinearLayout(BillingActivity.this);
                            warnBtnRow.setOrientation(LinearLayout.HORIZONTAL);
                            
                            Button btnMerge = new Button(BillingActivity.this);
                            btnMerge.setText("Merge Qty");
                            btnMerge.setAllCaps(false);
                            btnMerge.setTextColor(android.graphics.Color.WHITE);
                            btnMerge.setBackground(createRoundedBackground("#1C1C1E", "#1C1C1E", 20));
                            LinearLayout.LayoutParams mergeParams = new LinearLayout.LayoutParams(0, (int)(46*dp), 1.0f);
                            mergeParams.setMargins(0, 0, (int)(8*dp), 0);
                            btnMerge.setLayoutParams(mergeParams);
                            
                            Button btnKeepBoth = new Button(BillingActivity.this);
                            btnKeepBoth.setText("Keep Both");
                            btnKeepBoth.setAllCaps(false);
                            btnKeepBoth.setTextColor(android.graphics.Color.parseColor("#1C1C1E"));
                            btnKeepBoth.setBackground(createRoundedBackground("#F2F2F7", "#F2F2F7", 20));
                            LinearLayout.LayoutParams keepParams = new LinearLayout.LayoutParams(0, (int)(46*dp), 1.0f);
                            keepParams.setMargins(0, 0, (int)(8*dp), 0);
                            btnKeepBoth.setLayoutParams(keepParams);
                            
                            Button btnCancelWarn = new Button(BillingActivity.this);
                            btnCancelWarn.setText("Cancel");
                            btnCancelWarn.setAllCaps(false);
                            btnCancelWarn.setTextColor(android.graphics.Color.parseColor("#636366"));
                            btnCancelWarn.setBackground(createRoundedBackground("#FFFFFF", "#E5E5EA", 20));
                            LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, (int)(46*dp), 1.0f);
                            btnCancelWarn.setLayoutParams(cancelParams);
                            
                            warnBtnRow.addView(btnMerge);
                            warnBtnRow.addView(btnKeepBoth);
                            warnBtnRow.addView(btnCancelWarn);
                            warnLayout.addView(warnBtnRow);
                            
                            warnBuilder.setView(warnLayout);
                            final androidx.appcompat.app.AlertDialog warnDialog = warnBuilder.create();
                            warnDialog.show();
                            if (warnDialog.getWindow() != null) {
                                warnDialog.getWindow().setBackgroundDrawable(createRoundedBackground("#FFFFFF", "#FFFFFF", 24));
                            }
                            
                            btnMerge.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    addToCartCustom(p, finalQty, finalUPrice, finalPct, finalAmt, finalIsPercent, finalSelectedVar, finalSelectedVarId, false);
                                    
                                    itemAdded[0] = true;
                                    btnCancel.setText("Done");
                                    edtQtyInput.setText("1");
                                    edtDiscountPct.setText("");
                                    edtDiscountAmt.setText("");
                                    if (hasVariations) {
                                        selectedVarIndex[0] = -1;
                                        edtOverridePrice.setText(p.wholesalePrice.toPlainString());
                                    }
                                    renderVariationTiles.run();
                                    updateControlsState.run();
                                    updatePreview.run();
                                    warnDialog.dismiss();
                                    if (!hasVariations) {
                                        dialog.dismiss();
                                    }
                                }
                            });

                            btnKeepBoth.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    addToCartCustom(p, finalQty, finalUPrice, finalPct, finalAmt, finalIsPercent, finalSelectedVar, finalSelectedVarId, true);
                                    
                                    itemAdded[0] = true;
                                    btnCancel.setText("Done");
                                    edtQtyInput.setText("1");
                                    edtDiscountPct.setText("");
                                    edtDiscountAmt.setText("");
                                    if (hasVariations) {
                                        selectedVarIndex[0] = -1;
                                        edtOverridePrice.setText(p.wholesalePrice.toPlainString());
                                    }
                                    renderVariationTiles.run();
                                    updateControlsState.run();
                                    updatePreview.run();
                                    warnDialog.dismiss();
                                    if (!hasVariations) {
                                        dialog.dismiss();
                                    }
                                }
                            });
                            
                            btnCancelWarn.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    warnDialog.dismiss();
                                }
                            });
                        } else {
                            addToCartCustom(p, qty, uPrice, pct, amt, percentActiveHolder[0], selectedVar, selectedVarId, false);
                            
                            itemAdded[0] = true;
                            btnCancel.setText("Done");
                            edtQtyInput.setText("1");
                            edtDiscountPct.setText("");
                            edtDiscountAmt.setText("");
                            if (hasVariations) {
                                selectedVarIndex[0] = -1;
                                edtOverridePrice.setText(p.wholesalePrice.toPlainString());
                            }
                            renderVariationTiles.run();
                            updateControlsState.run();
                            updatePreview.run();
                            if (!hasVariations) {
                                dialog.dismiss();
                            }
                        }
                    }
                } catch (Exception e) {
                    Toast.makeText(BillingActivity.this, "Invalid inputs entered.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Initialize state
        renderVariationTiles.run();
        updateControlsState.run();
    }
'@

# Replace lines 2892 to 3875 (indices 2891 to 3874)
$newLines = $lines[0..2065] + ($newMethod -split "`r?`n") + $lines[2636..($lines.Count-1)]
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($file, $newLines, $utf8NoBom)
Write-Host "Updated showProductConfigDialog! New total line count: $($newLines.Count)"
