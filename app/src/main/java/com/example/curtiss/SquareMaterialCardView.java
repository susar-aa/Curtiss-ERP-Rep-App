package com.example.curtiss;

import android.content.Context;
import android.util.AttributeSet;
import com.google.android.material.card.MaterialCardView;

/**
 * A MaterialCardView that always measures its height to be equal to its width,
 * creating a perfect 1:1 square aspect ratio on any screen size or column count.
 */
public class SquareMaterialCardView extends MaterialCardView {

    public SquareMaterialCardView(Context context) {
        super(context);
    }

    public SquareMaterialCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareMaterialCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Force height measure spec to match width measure spec for a perfect 1:1 square
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
    }
}
