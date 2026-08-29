package com.dbt.tracker.data

data class CategoryDef(val name: String, val color: Long, val keywords: List<String>)

/**
 * Keyword-based classifier tuned for Indian merchants as they appear in SBI/UPI SMS.
 * Order matters: the first definition whose keyword appears in the haystack wins, so
 * narrow categories are listed before broad ones.
 */
object Categories {

    const val FOOD = "Food & Dining"
    const val GROCERY = "Groceries"
    const val TRANSPORT = "Transport & Fuel"
    const val SHOPPING = "Shopping"
    const val BILLS = "Bills & Recharge"
    const val ENTERTAINMENT = "Entertainment"
    const val HEALTH = "Health & Medical"
    const val EDUCATION = "Education"
    const val RENT = "Rent & Home"
    const val INVEST = "SIP"
    const val INSURANCE = "Insurance"
    const val CASH = "Cash Withdrawal"
    const val TRANSFER = "People & Transfers"
    const val INCOME = "Income"
    const val FEES = "Bank Charges"
    const val OTHER = "Uncategorised"

    val ALL: List<CategoryDef> = listOf(
        CategoryDef(FOOD, 0xFFF97316, listOf(
            "zomato", "eternal ltd", "eternal limited", "swiggy", "bundl tech", "eatsure",
            "dominos", "domino", "jubilant food", "pizza", "mcdonald", "kfc",
            "burger", "subway", "starbucks", "cafe", "coffee", "chaayos", "chai point", "barista",
            "restaurant", "resto", "dhaba", "biryani", "bakery", "sweets", "juice", "faasos",
            "behrouz", "ovenstory", "wow momo", "haldiram", "bikaner", "food", "kitchen",
            "canteen", "icecream", "ice cream", "baskin", "naturals", "hotel", "mess "
        )),
        CategoryDef(GROCERY, 0xFF16A34A, listOf(
            "bigbasket", "big basket", "supermarket grocery", "blinkit", "blink commerce",
            "grofers", "zepto", "kiranakart", "instamart", "dmart", "avenue supermart",
            "d mart", "d-mart", "reliance fresh", "reliance smart", "more retail", "spencer",
            "star bazaar", "nature basket", "kirana", "grocery", "provision", "supermarket",
            "super market", "vegetable", "sabzi", "milk", "amul", "mother dairy",
            "country delight", "licious", "freshtohome", "fresh to home", "dairy",
            "general store", "bigbazaar", "big bazaar", "smart bazaar", "vishal mega",
            "easyday", "ratnadeep", "heritage fresh", "24 seven", "namdhari"
        )),
        CategoryDef(TRANSPORT, 0xFF0EA5E9, listOf(
            "uber", "ola ", "olacabs", "ani technologies", "rapido", "roppen",
            "namma yatri", "blusmart", "meru",
            "irctc", "railway", "redbus", "abhibus", "makemytrip", "goibibo", "ixigo",
            "cleartrip", "indigo", "spicejet", "air india", "vistara", "akasa",
            "metro", "dmrc", "bmtc", "best bus", "msrtc", "ksrtc", "tsrtc", "apsrtc",
            "petrol", "diesel", "fuel", "hpcl", "bpcl", "bharat petroleum",
            "indian oil", "indianoil", "iocl", "shell", "nayara", "reliance petro",
            "fastag", "toll", "parking", "yulu", "bounce", "chalo", "onedelhi",
            "cab ", "taxi", "rickshaw"
        )),
        CategoryDef(SHOPPING, 0xFFA855F7, listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "tata cliq", "tatacliq",
            "snapdeal", "shopsy", "firstcry", "pepperfry", "urban ladder", "ikea", "decathlon",
            "lifestyle", "pantaloons", "westside", "shoppers stop", "max fashion", "zudio",
            "reliance trends", "zara", "uniqlo", "levis", "puma", "adidas", "nike",
            "croma", "reliance digital", "vijay sales", "titan", "tanishq", "lenskart",
            "retail", "fashion", "apparel", "electronics", "store", "mart"
        )),
        CategoryDef(BILLS, 0xFF64748B, listOf(
            "electricity", "bses", "tata power", "adani electricity", "mseb", "bescom", "tneb",
            "torrent power", "cesc", "discom", "powergrid",
            "airtel", "jio", "vodafone", "idea cell", "bsnl", "mtnl", "act fibernet",
            "hathway", "excitel", "tikona", "broadband", "postpaid", "prepaid", "recharge",
            "dth", "tata sky", "tata play", "dish tv", "d2h", "sun direct",
            "indane", "hp gas", "bharat gas", "lpg", "gail", "igl", "mahanagar gas",
            "water bill", "municipal", "property tax", "bill payment", "billdesk", "bbps",
            "utility"
        )),
        CategoryDef(ENTERTAINMENT, 0xFFEC4899, listOf(
            "netflix", "spotify", "hotstar", "jiohotstar", "prime video", "sony liv", "sonyliv",
            "zee5", "jiocinema", "voot", "mubi", "apple music", "gaana", "wynk", "youtube",
            "bookmyshow", "pvr", "inox", "cinepolis", "carnival cinema", "cinema", "multiplex",
            "dream11", "rummy", "gaming", "steam ", "playstation", "xbox", "nintendo",
            "audible", "kindle"
        )),
        CategoryDef(HEALTH, 0xFF14B8A6, listOf(
            "pharmeasy", "netmeds", "1mg", "apollo", "medplus", "wellness forever",
            "practo", "cult.fit", "cultfit", "cult fit", "curefit", "gym", "fitness",
            "hospital", "clinic", "nursing", "diagnostic", "pathology", "doctor",
            "medical", "medico", "pharma", "chemist", "dental", "optical", "physio"
        )),
        CategoryDef(EDUCATION, 0xFF6366F1, listOf(
            "udemy", "coursera", "unacademy", "byju", "vedantu", "physics wallah",
            "toppr", "whitehat", "skillshare", "upgrad", "simplilearn", "great learning",
            "school", "college", "university", "institute", "academy", "coaching", "tuition",
            "exam fee", "admission", "hostel fee", "course"
        )),
        CategoryDef(RENT, 0xFF8B5CF6, listOf(
            "rent", "landlord", "nobroker", "nestaway", "stanza", "colive", "zolo",
            "society", "maintenance", "apartment", "housing"
        )),
        CategoryDef(INVEST, 0xFF22C55E, listOf(
            "zerodha", "groww", "nextbillion", "upstox", "angel one", "angelone", "5paisa",
            "icici direct",
            "kuvera", "indmoney", "smallcase", "paytm money", "dhan ", "fyers",
            "mutual fund", "sip ", "nps ", "ppf", "sukanya", "recurring deposit",
            "fixed deposit", "term deposit", "gold bond", "sgb", "broking",
            "demat", "cdsl", "nsdl"
        )),
        CategoryDef(INSURANCE, 0xFF0891B2, listOf(
            "insurance", "lic ", "hdfc life", "max life", "sbi life",
            "icici pru", "bajaj allianz", "tata aig", "star health", "niva bupa", "care health",
            "policybazaar", "acko", "digit ", "policy"
        )),
        CategoryDef(CASH, 0xFFEAB308, listOf(
            "atm", "cash withdrawal", "cash wdl", "cwdr", "nfs ", "cash dep"
        )),
        CategoryDef(FEES, 0xFF94A3B8, listOf(
            "charges", "service charge", "sms charge", "annual fee", "amc ",
            "penalty", "convenience fee", "processing fee", "late fee"
        )),
        CategoryDef(INCOME, 0xFF10B981, listOf(
            "salary", "payroll", "stipend", "refund", "reversal", "cashback",
            "interest credit", "dividend", "reimburs", "bonus", "settlement"
        ))
    )

    private val byName = ALL.associateBy { it.name }

    /** Categories the user can pick from in the UI, plus the two structural buckets. */
    val PICKABLE: List<String> = ALL.map { it.name } + listOf(TRANSFER, OTHER)

    fun colorOf(name: String): Long = byName[name]?.color ?: when (name) {
        TRANSFER -> 0xFF7C3AED
        else -> 0xFF94A3B8
    }

    /**
     * Classifies a transaction. [merchant] carries the most signal; [raw] is searched too so
     * hints living elsewhere in the SMS (e.g. "ATM", "NEFT SALARY") still land correctly.
     */
    fun classify(merchant: String, raw: String, channel: String, isCredit: Boolean): String {
        val hay = (" " + merchant + "  " + raw + " ").lowercase()

        if (channel == Channel.ATM) return CASH

        for (def in ALL) {
            // Income keywords only make sense on money coming in.
            if (def.name == INCOME && !isCredit) continue
            if (def.keywords.any { hay.contains(it) }) return def.name
        }

        if (isCredit) return INCOME
        // A handle that reads like a person's name is a transfer, not a mystery.
        if (looksLikePerson(merchant)) return TRANSFER
        return OTHER
    }

    /** Heuristic: a few alphabetic words and no digits reads like a person's name. */
    private fun looksLikePerson(merchant: String): Boolean {
        val m = merchant.trim()
        if (m.isEmpty() || m.any { it.isDigit() }) return false
        val words = m.split(" ").filter { it.isNotBlank() }
        return words.size in 1..4 && words.all { it.length in 2..15 }
    }
}
