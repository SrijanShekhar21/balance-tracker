package com.dbt.tracker.sms

import com.dbt.tracker.data.Categories
import com.dbt.tracker.data.Signal
import java.util.Locale

/**
 * Reads the SMS that service apps send you and records what you were *doing*, so that a payment
 * to an unidentifiable payee can still be explained.
 *
 * This exists because of how Rapido works in practice: riders are usually paid by scanning a
 * personal UPI QR code, so the bank SMS names an individual rather than Rapido. Every rider is
 * different, so learning payees is useless. But Rapido texts a ride OTP when the trip starts,
 * and that message dates the ride precisely enough to explain the payment that follows.
 *
 * Nothing here is treated as money. A signal only ever supplies a category to a transaction the
 * classifier already failed to place.
 */
object SignalDetector {

    private data class Source(val label: String, val category: String, val senderKeys: List<String>)

    private val SOURCES = listOf(
        Source("Rapido", Categories.TRANSPORT, listOf("RAPIDO", "ROPPEN")),
        Source("Uber", Categories.TRANSPORT, listOf("UBER")),
        Source("Ola", Categories.TRANSPORT, listOf("OLACAB", "OLACABS", "OLAAPP", "OLAMNY")),
        Source("Namma Yatri", Categories.TRANSPORT, listOf("NMAYTR", "NAMMAY")),
        Source("Blinkit", Categories.GROCERY, listOf("BLINKIT", "BLNKIT", "GROFER")),
        Source("Zepto", Categories.GROCERY, listOf("ZEPTO", "ZPTONW", "KIRANAKART")),
        Source("Swiggy", Categories.FOOD, listOf("SWIGGY", "SWGY", "BUNDL")),
        Source("Zomato", Categories.FOOD, listOf("ZOMATO", "ZOMATO", "ETERNAL")),
        Source("Dunzo", Categories.GROCERY, listOf("DUNZO")),
        Source("BigBasket", Categories.GROCERY, listOf("BIGBSK", "BGBSKT", "BIGBASKET"))
    )

    /**
     * Evidence that an activity genuinely happened. A ride OTP is the strongest of these: it is
     * sent at the moment a trip starts and never appears in marketing.
     */
    private val ACTIVITY = listOf(
        "otp", "one time password",
        "ride started", "trip started", "ride is", "trip is", "your ride", "your trip",
        "your captain", "your rider", "captain is", "driver is", "has arrived", "is arriving",
        "on the way", "reached your", "pickup", "drop location", "ride completed",
        "trip completed", "order placed", "order confirmed", "order is", "your order",
        "out for delivery", "has been delivered", "arriving in", "delivery partner"
    )

    /**
     * Marketing that merely mentions rides or orders. Checked first: a discount blast must never
     * invent a ride that a later unrelated payment could then be attributed to.
     */
    private val PROMO = listOf(
        "% off", " off ", "offer", "coupon", "discount", "cashback", "flat ", "save up",
        "refer", "download", "install", "rate us", "review", "survey", "win ", "lucky",
        "limited time", "hurry", "expires", "last chance", "unlock", "free delivery",
        "use code", "apply code", "sale", "deal"
    )

    /**
     * @param sender SMS short-code, used to identify which app sent it
     * @param body message text
     * @param ts delivery time, which is the whole point: it dates the activity
     * @return a signal to remember, or null if this message proves nothing
     */
    fun detect(sender: String, body: String, ts: Long): Signal? {
        val upper = sender.uppercase(Locale.ROOT)
        val source = SOURCES.firstOrNull { s -> s.senderKeys.any { upper.contains(it) } }
            ?: return null

        val low = body.lowercase(Locale.ROOT)
        if (PROMO.any { low.contains(it) }) return null
        if (ACTIVITY.none { low.contains(it) }) return null

        return Signal(ts = ts, category = source.category, label = source.label, raw = body)
    }
}
