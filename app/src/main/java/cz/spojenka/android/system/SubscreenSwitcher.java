package cz.spojenka.android.system;

import android.view.View;

/**
 * Utility for toggling the visibility of sibling views in a container.
 * Unlike a ViewPager, all the views are alive at a given time, but similarly, only one is visible.
 */
public class SubscreenSwitcher {

    private final View[] views;

    /**
     * Constructor
     * @param views Group of views among which to switch. No initial state reset is done.
     */
    public SubscreenSwitcher(View... views) {
        this.views = views;
    }

    /**
     * Set the current visible view.
     *
     * @param view The view to be made visible. It must be one of the views passed to the constructor,
     *             otherwise, or if null is passed, all views will be made invisible.
     */
    public void setSubscreen(View view) {
        for (View v : views) {
            v.setVisibility(v == view ? View.VISIBLE : View.GONE);
        }
    }
}
