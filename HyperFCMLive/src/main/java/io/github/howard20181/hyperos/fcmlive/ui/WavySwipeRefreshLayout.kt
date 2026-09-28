package io.github.howard20181.hyperos.fcmlive.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.loadingindicator.LoadingIndicator
import kotlin.math.roundToInt

/**
 * A [SwipeRefreshLayout] whose spinner is the Material 3 Expressive
 * [LoadingIndicator] — the scalloped ring that morphs between its wavy and
 * circular forms — instead of the classic indeterminate circle.
 *
 * SwipeRefreshLayout owns its spinner as a private child and exposes no way to
 * swap it, so this layout keeps the original spinner in place (it still drives
 * all the geometry: pull distance, release snap and the scale animation) and
 * simply never shows it. [LoadingIndicator] is expected as a child in the same
 * layout, declared *after* the scrolling content so that the content stays the
 * first non-spinner child, which is what the gesture binds to.
 *
 * Two signals are mirrored from the hidden spinner:
 *
 * - **Position and scale**, so the indicator slides out with the finger and
 *   snaps back with the release, exactly like the stock spinner.
 * - **Visibility**, which is what decides when the indicator is shown. It is
 *   deliberately *not* [isRefreshing]: the spinner becomes visible the moment a
 *   pull starts, long before the refresh begins, so driving off `isRefreshing`
 *   would leave nothing on screen during the drag and make the indicator pop in
 *   at the resting position instead of sliding out.
 *
 * The mirroring runs off a frame callback rather than [dispatchDraw]. Under
 * hardware acceleration a child going `GONE` only invalidates that child, so
 * once the refresh settles this layout stops being redrawn and a draw-driven
 * sync would never run again — the indicator would spin forever because its own
 * animator keeps invalidating *itself* while nothing ever asks it to hide.
 */
class WavySwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var indicator: LoadingIndicator? = null
    private var spinner: View? = null
    private var indicatorShown = false
    private var ticking = false
    private val containerSize: Int by lazy {
        // The M3 container is 48dp; the stock spinner is smaller, so take the
        // larger of the two and let the indicator draw at its design size.
        (CONTAINER_DP * resources.displayMetrics.density).roundToInt()
    }

    /**
     * Re-posts itself for as long as there is something to mirror. Runs at most
     * once per frame and stops on its own as soon as the spinner is gone and
     * the indicator has been asked to hide, so an idle list costs nothing.
     */
    private val syncTick: Runnable = object : Runnable {
        override fun run() {
            ticking = false
            if (syncIndicator()) {
                ticking = true
                postOnAnimation(this)
            }
        }
    }

    /** Paints the loading indicator: container plate plus ring. */
    fun setIndicatorColors(containerColor: Int, indicatorColor: Int) {
        val ind = findChild { it is LoadingIndicator } as? LoadingIndicator ?: return
        indicator = ind
        ind.setContainerColor(containerColor)
        ind.setIndicatorColor(indicatorColor)
        scheduleSync()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scheduleSync()
    }

    override fun onDetachedFromWindow() {
        // A posted callback never fires once detached, so the flag has to be
        // cleared here or the next sync would be skipped forever.
        ticking = false
        removeCallbacks(syncTick)
        indicatorShown = false
        super.onDetachedFromWindow()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (spinner == null) {
            spinner = findChild(::isSpinner)?.also {
                // Hidden by transparency rather than by skipping its draw. The
                // stock spinner is animated with a *View* animation, and such an
                // animation only advances while the view is drawn — returning
                // early from drawChild() froze it, so onAnimationEnd() never
                // fired, reset() never ran and the spinner stayed VISIBLE for
                // good, which in turn kept this indicator on screen forever.
                // An alpha of 0 hides it while still letting that animation run.
                it.alpha = 0f
            }
        }
        if (indicator == null) indicator = findChild { it is LoadingIndicator } as? LoadingIndicator
        val ind = indicator ?: return
        val size = maxOf(spinner?.width ?: 0, containerSize)
        val start = ((right - left - size) / 2f).roundToInt()
        // Laid out at the very top on purpose: the real position comes from the
        // translation applied in syncIndicator(), which tracks the spinner.
        ind.layout(start, 0, start + size, size)
        if (syncIndicator()) scheduleSync()
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (syncIndicator()) scheduleSync()
        super.dispatchDraw(canvas)
    }

    /**
     * Re-arms the sync when the window becomes visible again. A frame callback
     * posted before the screen went off is not delivered while the display is
     * off, which leaves [ticking] stuck at true and turns every later
     * [scheduleSync] into a no-op. With the screen off nothing advances anyway
     * — the spinner's close animation is a View animation and the indicator's
     * animator only runs on frames — so a refresh that finishes while asleep
     * settles here, once the window is drawn again.
     */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            ticking = false
            removeCallbacks(syncTick)
            scheduleSync()
        }
    }

    override fun setRefreshing(refreshing: Boolean) {
        super.setRefreshing(refreshing)
        scheduleSync()
    }

    private fun scheduleSync() {
        if (ticking) return
        ticking = true
        postOnAnimation(syncTick)
    }

    /**
     * Mirrors the hidden spinner. The spinner is moved with
     * `offsetTopAndBottom`, so its live position has to be read every frame
     * rather than cached at layout time — otherwise the indicator sits still
     * while the gesture moves the spinner.
     *
     * @return true while there is still state to mirror, i.e. the caller should
     *         keep scheduling frames.
     */
    private fun syncIndicator(): Boolean {
        val ind = indicator ?: return false
        val spinner = this.spinner
        val engaged = spinner?.visibility == View.VISIBLE || isRefreshing
        if (engaged && !indicatorShown) {
            indicatorShown = true
            ind.show()
        } else if (!engaged && indicatorShown) {
            indicatorShown = false
            ind.hide()
        }
        // Without the spinner there is nothing to track, so the indicator keeps
        // whatever frame onLayout gave it rather than jumping somewhere odd.
        if (spinner == null) return engaged || indicatorShown
        ind.translationX = (spinner.left + spinner.width / 2f) - (ind.left + ind.width / 2f)
        ind.translationY = (spinner.top + spinner.height / 2f) - (ind.top + ind.height / 2f)
        ind.scaleX = spinner.scaleX
        ind.scaleY = spinner.scaleY
        return engaged || indicatorShown
    }

    private fun findChild(matches: (View) -> Boolean): View? {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (matches(child)) return child
        }
        return null
    }

    private companion object {
        private const val CONTAINER_DP = 48f

        /**
         * The stock spinner is a package-private `CircleImageView`, so it is
         * matched by type instead of by name: it is the only ImageView in the
         * hierarchy (the content is a list, the replacement is a plain View),
         * and a type check survives R8 where a class-name string might not.
         */
        private fun isSpinner(child: View): Boolean = child is ImageView
    }
}
