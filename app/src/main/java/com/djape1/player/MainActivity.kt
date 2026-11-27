package com.djape1.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var card: View
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var artist: TextView
    private lateinit var album: TextView
    private lateinit var genre: TextView
    private lateinit var key: TextView
    private lateinit var air: View
    private lateinit var airText: TextView
    private lateinit var eq: EqView
    private lateinit var cover: ImageView
    private lateinit var coverBox: View
    private lateinit var play: TextView
    private lateinit var volume: SeekBar
    private lateinit var volIcon: ImageView
    private lateinit var invBox: View
    private lateinit var invInput: EditText
    private lateinit var scanBtn: TextView
    private lateinit var saveInv: TextView
    private lateinit var weeklyBtn: TextView
    private lateinit var zoneBox: View
    private lateinit var zoneIcon: ImageView
    private lateinit var zoneName: TextView
    private lateinit var zoneDesc: TextView
    private lateinit var moodScroll: View
    private lateinit var moodRow: LinearLayout
    private lateinit var toolViews: List<ImageView>
    private lateinit var audio: AudioManager

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var started = false
    private var lastCoverId: String? = null
    private var pendingPlay = false
    private var last: NowInfo? = null
    private var shownMood = ""
    private var shownDark: Boolean? = null
    private var weekCache: List<WeekDay> = emptyList()

    private val hubListener: () -> Unit = { renderPlayState() }

    private val pollTask = object : Runnable {
        override fun run() {
            val inv = Prefs.invite(this@MainActivity)
            if (inv != null) io.execute {
                try {
                    val info = Api.fetchNow(inv)
                    main.post { renderNow(info, inv) }
                } catch (e: ApiException) {
                    if (e.code == 403 || e.code == 404) main.post { renderInvalid() }
                } catch (e: Exception) {
                    // χωρίς δίκτυο: κρατάμε ό,τι δείχνει η οθόνη
                }
            }
            if (started) main.postDelayed(this, 4000)
        }
    }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Η αναπαραγωγή δουλεύει και χωρίς άδεια ειδοποιήσεων· απλώς δεν θα φαίνεται η ειδοποίηση
            if (pendingPlay) { pendingPlay = false; startPlayback() }
        }

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        val text = result?.contents ?: return@registerForActivityResult
        val inv = LinkParser.parse(text)
        if (inv == null) {
            Toast.makeText(this, "This QR code is not a Dj Ape1 station invitation.", Toast.LENGTH_LONG).show()
        } else applyInvite(inv)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        volumeControlStream = AudioManager.STREAM_MUSIC
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        root = findViewById(R.id.root)
        card = findViewById(R.id.card)
        status = findViewById(R.id.status)
        title = findViewById(R.id.title)
        artist = findViewById(R.id.artist)
        album = findViewById(R.id.album)
        genre = findViewById(R.id.genre)
        key = findViewById(R.id.key)
        air = findViewById(R.id.air)
        airText = findViewById(R.id.airText)
        eq = findViewById(R.id.eq)
        cover = findViewById(R.id.cover)
        coverBox = findViewById(R.id.coverBox)
        play = findViewById(R.id.play)
        volume = findViewById(R.id.volume)
        volIcon = findViewById(R.id.volIcon)
        invBox = findViewById(R.id.invBox)
        invInput = findViewById(R.id.invInput)
        scanBtn = findViewById(R.id.scanBtn)
        saveInv = findViewById(R.id.saveInv)
        weeklyBtn = findViewById(R.id.weeklyBtn)
        zoneBox = findViewById(R.id.zoneBox)
        zoneIcon = findViewById(R.id.zoneIcon)
        zoneName = findViewById(R.id.zoneName)
        zoneDesc = findViewById(R.id.zoneDesc)
        moodScroll = findViewById(R.id.moodScroll)
        moodRow = findViewById(R.id.moodRow)
        toolViews = listOf(findViewById(R.id.btnMood), findViewById(R.id.btnScan), findViewById(R.id.theme), findViewById(R.id.btnInfo))

        findViewById<View>(R.id.theme).setOnClickListener {
            val dark = !Prefs.dark(this)
            Prefs.setDark(this, dark)
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        findViewById<View>(R.id.btnMood).setOnClickListener {
            moodScroll.visibility = if (moodScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<View>(R.id.btnScan).setOnClickListener { startScan() }
        findViewById<View>(R.id.btnInfo).setOnClickListener { showInfoMenu(it) }
        scanBtn.setOnClickListener { startScan() }
        play.setOnClickListener { onPlayClicked() }
        weeklyBtn.setOnClickListener {
            val inv = Prefs.invite(this)
            if (inv == null) {
                Toast.makeText(this, "First add the station address or scan the QR code", Toast.LENGTH_SHORT).show()
            } else io.execute {
                val w = try { Api.fetchWeek(inv) } catch (e: Exception) { null }
                main.post {
                    if (w != null) weekCache = w
                    showWeeklyDialog()
                }
            }
        }
        saveInv.setOnClickListener { saveInvite() }

        volume.max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, p, 0)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        handleIntent(intent)
        if (Prefs.invite(this) == null) invBox.visibility = View.VISIBLE
        buildMoodRow()
        applyPalette(true)
        renderPlayState()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Άνοιγμα από σύνδεσμο πρόσκλησης (https://…/l/…): τον αποθηκεύουμε και είμαστε έτοιμοι. */
    private fun handleIntent(i: Intent?) {
        val data = i?.data?.toString() ?: return
        val inv = LinkParser.parse(data) ?: return
        applyInvite(inv)
    }

    private fun applyInvite(inv: Invite) {
        val changed = Prefs.invite(this) != inv
        Prefs.saveInvite(this, inv)
        if (changed && Hub.playing) stopService(Intent(this, PlayerService::class.java))
        invInput.setText("")
        invBox.visibility = View.GONE
        lastCoverId = null
        last = null
        Toast.makeText(this, "Station saved", Toast.LENGTH_SHORT).show()
        main.removeCallbacks(pollTask)
        if (started) main.post(pollTask)
    }

    private fun saveInvite() {
        val inv = LinkParser.parse(invInput.text?.toString())
        if (inv == null) {
            Toast.makeText(this, "The link doesn't look right. Paste the full station address or scan the QR code.", Toast.LENGTH_LONG).show()
            return
        }
        applyInvite(inv)
    }

    private fun startScan() {
        val o = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Point the camera at the station QR code")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        try {
            scanner.launch(o)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open the camera. Paste the station address instead.", Toast.LENGTH_LONG).show()
        }
    }

    private fun onPlayClicked() {
        if (Hub.playing) {
            stopService(Intent(this, PlayerService::class.java))
            Hub.update(false, "Ready")
            return
        }
        if (Prefs.invite(this) == null) {
            invBox.visibility = View.VISIBLE
            Toast.makeText(this, "First add the station address or scan the QR code", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingPlay = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else startPlayback()
    }

    private fun startPlayback() {
        Hub.update(true, "Connecting…")
        ContextCompat.startForegroundService(this, Intent(this, PlayerService::class.java))
    }

    /* ---------------- θέματα και γραφικά ---------------- */

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun dpi(v: Float): Int = dp(v).toInt()

    private fun currentMoodId(): String {
        val pref = Prefs.mood(this)
        if (pref != "auto") return pref
        val n = last
        return Mood.auto(n?.zoneTheme, n?.genre, n?.zone)
    }

    private fun shape(fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Float = 1f): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(radiusDp)
            if (stroke != 0) setStroke(dpi(strokeDp), stroke)
        }

    private fun gradient(a: Int, b: Int, radiusDp: Float, horizontal: Boolean): GradientDrawable =
        GradientDrawable(
            if (horizontal) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(a, b)
        ).apply { cornerRadius = dp(radiusDp) }

    private fun tint(v: ImageView, color: Int) = ImageViewCompat.setImageTintList(v, ColorStateList.valueOf(color))
    private fun tint(v: TextView, color: Int) = TextViewCompat.setCompoundDrawableTintList(v, ColorStateList.valueOf(color))

    private fun applyPalette(force: Boolean = false) {
        val id = currentMoodId()
        val dark = Prefs.dark(this)
        if (!force && id == shownMood && dark == shownDark) return
        shownMood = id; shownDark = dark
        val p = Mood.palette(id, dark)

        root.background = gradient(p.page, ColorUtils.blendARGB(p.page, p.accent2, if (dark) 0.16f else 0.10f), 0f, false)
        window.statusBarColor = p.page
        window.navigationBarColor = p.page
        val strokeDp = if (id == "neon" || id == "jazz") 2f else if (id == "rock") 2f else 1f
        card.background = shape(p.card, p.cardRadiusDp, p.cardStroke, strokeDp)

        title.setTextColor(p.fg); artist.setTextColor(p.fg)
        album.setTextColor(p.sub); zoneDesc.setTextColor(p.fg); zoneName.setTextColor(p.fg)
        weeklyBtn.setTextColor(p.link)
        invInput.setTextColor(p.fg); invInput.setHintTextColor(p.sub)
        invInput.background = shape(p.field, 10f, p.line)
        saveInv.background = shape(p.field, 10f, p.line); saveInv.setTextColor(p.fg)
        coverBox.background = shape(p.field, if (id == "retro" || id == "lofi") 52f else 10f, p.line)
        zoneBox.background = shape(p.field, 12f, p.line)
        zoneIcon.background = shape(p.card, 10f, p.line); tint(zoneIcon, p.accent)
        genre.background = shape(p.field, 999f, p.line); genre.setTextColor(p.sub)
        album.let { tint(it, p.sub) }
        air.background = shape(Color.TRANSPARENT, 999f, p.accent)
        airText.setTextColor(p.accent)
        eq.setColors(p.accent2, p.accent)
        tint(genre, p.sub)
        tint(volIcon, p.sub)

        val grad = gradient(p.accent, p.accent2, 12f, true)
        play.background = grad; play.setTextColor(p.accentFg); tint(play, p.accentFg)
        scanBtn.background = gradient(p.accent, p.accent2, 12f, true); scanBtn.setTextColor(p.accentFg)
        scanBtn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_qr, 0, 0, 0); tint(scanBtn, p.accentFg)
        album.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_disc, 0, 0, 0)
        tint(album, p.sub)

        toolViews.forEach { v ->
            v.background = shape(p.field, 999f, p.line)
            tint(v, p.fg)
        }
        for (i in 0 until moodRow.childCount) styleMoodChip(moodRow.getChildAt(i) as TextView, p)
        renderPlayState()
    }

    private fun styleMoodChip(t: TextView, p: Palette) {
        val m = t.tag as String
        val sel = m == Prefs.mood(this)
        t.background = shape(if (sel) p.card else p.field, 999f, if (sel) p.accent else p.line, if (sel) 2f else 1f)
        t.setTextColor(if (sel) p.accent else p.sub)
        tint(t, if (sel) p.accent else p.sub)
    }

    private fun buildMoodRow() {
        moodRow.removeAllViews()
        Mood.IDS.forEach { id ->
            val t = TextView(this)
            t.tag = id
            t.text = Mood.NAMES[id]
            t.textSize = 13f
            t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
            t.gravity = Gravity.CENTER_VERTICAL
            t.compoundDrawablePadding = dpi(6f)
            t.setCompoundDrawablesWithIntrinsicBounds(Mood.ICONS[id] ?: R.drawable.ic_music, 0, 0, 0)
            t.setPadding(dpi(12f), dpi(8f), dpi(14f), dpi(8f))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dpi(6f)
            t.layoutParams = lp
            t.setOnClickListener {
                Prefs.setMood(this, id)
                applyPalette(true)
            }
            moodRow.addView(t)
        }
    }


    /* ---------------- μενού info ---------------- */

    private fun showInfoMenu(anchor: View) {
        val pm = androidx.appcompat.widget.PopupMenu(this, anchor, Gravity.END)
        pm.menu.add(0, 1, 0, "Streaming quality")
        pm.menu.add(0, 2, 1, "Last 5 songs")
        pm.menu.add(0, 3, 2, "Station program")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> showQualityDialog()
                2 -> showLastSongsDialog()
                3 -> showProgramDialog()
            }
            true
        }
        pm.show()
    }

    private fun infoPalette(): Palette = Mood.palette(shownMood.ifEmpty { "ape1" }, Prefs.dark(this))

    private fun infoText(text: String, sizeSp: Float, color: Int, bold: Boolean = false, topDp: Float = 0f): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dpi(topDp), 0, 0)
        }

    /** Κοινό παράθυρο για τις τρεις επιλογές, στα χρώματα του τρέχοντος θέματος. */
    private fun showInfoDialog(title: String, content: View) {
        val p = infoPalette()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(18f), dpi(20f), dpi(12f))
            addView(infoText(title, 18f, p.fg, true))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpi(12f)
            })
        }
        val scroll = android.widget.ScrollView(this).apply { addView(box) }
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(scroll)
            .setPositiveButton("OK", null)
            .create()
        dlg.window?.setBackgroundDrawable(shape(p.card, 16f, p.line))
        dlg.show()
        dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(p.accent)
    }

    private fun showQualityDialog() {
        val p = infoPalette()
        val options = listOf("high" to "High", "medium" to "Medium", "low" to "Low (saves data)")
        val current = Prefs.quality(this)
        val group = android.widget.RadioGroup(this)
        options.forEachIndexed { i, (qid, label) ->
            val rb = android.widget.RadioButton(this).apply {
                this.id = 1000 + i
                text = label
                textSize = 16f
                setTextColor(p.fg)
                buttonTintList = ColorStateList.valueOf(p.accent)
                setPadding(dpi(8f), dpi(10f), 0, dpi(10f))
                isChecked = qid == current
            }
            group.addView(rb)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            val q = options.getOrNull(checkedId - 1000)?.first ?: return@setOnCheckedChangeListener
            if (q == Prefs.quality(this)) return@setOnCheckedChangeListener
            Prefs.setQuality(this, q)
            // Αν παίζει, επανασύνδεση με τη νέα ποιότητα
            if (Hub.playing) {
                stopService(Intent(this, PlayerService::class.java))
                main.postDelayed({ if (Prefs.invite(this) != null) startPlayback() }, 400)
            }
            Toast.makeText(this, "Streaming quality: " + options.first { it.first == q }.second, Toast.LENGTH_SHORT).show()
        }
        showInfoDialog("Streaming quality", group)
    }

    private fun showLastSongsDialog() {
        val p = infoPalette()
        val list = History.last5(this, last)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (list.isEmpty()) {
            col.addView(infoText("No songs yet. Keep listening and the last songs will appear here.", 14.5f, p.sub))
        } else {
            list.forEachIndexed { i, t ->
                col.addView(infoText("${i + 1}. ${t.title}", 16f, p.fg, true, if (i == 0) 0f else 12f))
                if (!t.artist.isNullOrBlank()) col.addView(infoText(t.artist, 14f, p.sub, false, 2f))
            }
        }
        showInfoDialog("Last 5 songs", col)
    }

    private fun showProgramDialog() {
        val p = infoPalette()
        val n = last
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (n != null && n.program.isNotEmpty()) {
            n.program.forEachIndexed { i, item ->
                val head = if (item.time != null) "${item.time}   ${item.name}" else item.name
                col.addView(infoText(head, 16f, p.fg, true, if (i == 0) 0f else 14f))
                if (!item.desc.isNullOrBlank()) col.addView(infoText(item.desc, 14f, p.sub, false, 2f))
            }
        } else if (n?.zone != null) {
            col.addView(infoText("On now", 12.5f, p.sub))
            col.addView(infoText(n.zone, 16f, p.fg, true, 2f))
            if (!n.zoneDesc.isNullOrBlank()) col.addView(infoText(n.zoneDesc, 14f, p.sub, false, 2f))
        } else {
            col.addView(infoText("The station program is not available right now.", 14.5f, p.sub))
        }
        showInfoDialog("Station program", col)
    }

    private fun showWeeklyDialog() {
        val p = infoPalette()
        val n = last
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK).let {
            // Calendar: SUNDAY=1 … SATURDAY=7  →  Monday…Sunday
            listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[it - 1]
        }
        val week = if (weekCache.isNotEmpty()) weekCache else n?.week ?: emptyList()
        if (week.isNotEmpty()) {
            week.forEachIndexed { di, d ->
                val isToday = d.day == today
                col.addView(infoText(if (isToday) "${d.day} (today)" else d.day, 17f, if (isToday) p.accent else p.fg, true, if (di == 0) 0f else 20f))
                if (d.items.isEmpty()) {
                    col.addView(infoText("No program", 14f, p.sub, false, 4f))
                } else d.items.forEachIndexed { i, item ->
                    val head = if (item.time != null) "${item.time}   ${item.name}" else item.name
                    col.addView(infoText(head, 15.5f, p.fg, true, if (i == 0) 6f else 10f))
                    if (!item.desc.isNullOrBlank()) col.addView(infoText(item.desc, 13.5f, p.sub, false, 2f))
                }
            }
        } else if (n != null && n.program.isNotEmpty()) {
            // Ο σταθμός δεν στέλνει εβδομαδιαίο πρόγραμμα: δείχνουμε το ημερήσιο
            col.addView(infoText("Daily program (the station has not published a weekly one)", 12.5f, p.sub))
            n.program.forEachIndexed { i, item ->
                val head = if (item.time != null) "${item.time}   ${item.name}" else item.name
                col.addView(infoText(head, 16f, p.fg, true, if (i == 0) 10f else 14f))
                if (!item.desc.isNullOrBlank()) col.addView(infoText(item.desc, 14f, p.sub, false, 2f))
            }
        } else if (n?.zone != null) {
            col.addView(infoText("The weekly program is not available right now.", 14.5f, p.sub))
            col.addView(infoText("On now", 12.5f, p.sub, false, 14f))
            col.addView(infoText(n.zone, 16f, p.fg, true, 2f))
            if (!n.zoneDesc.isNullOrBlank()) col.addView(infoText(n.zoneDesc, 14f, p.sub, false, 2f))
        } else {
            col.addView(infoText("The weekly program is not available right now.", 14.5f, p.sub))
        }
        showInfoDialog("Weekly program", col)
    }

    /* ---------------- εμφάνιση ---------------- */

    private fun renderPlayState() {
        play.text = if (Hub.playing) "Stop" else "Listen now"
        play.setCompoundDrawablesWithIntrinsicBounds(if (Hub.playing) R.drawable.ic_pause else R.drawable.ic_play, 0, 0, 0)
        val p = Mood.palette(shownMood.ifEmpty { "ape1" }, Prefs.dark(this))
        tint(play, p.accentFg)
        status.text = Hub.status
        status.setTextColor(if (Hub.bad) p.warn else p.link)
        eq.setActive(Hub.playing)
    }

    private fun show(v: TextView, text: String?) {
        if (text.isNullOrBlank()) v.visibility = View.GONE else { v.text = text; v.visibility = View.VISIBLE }
    }

    private fun renderInvalid() {
        title.visibility = View.VISIBLE
        title.text = "The invitation is not valid."
        listOf(artist, album, genre, key).forEach { it.visibility = View.GONE }
        air.visibility = View.GONE
        coverBox.visibility = View.GONE
        zoneBox.visibility = View.GONE
        Hub.update(false, "The invitation is not valid", true)
    }

    private fun renderNow(info: NowInfo, inv: Invite) {
        last = info
        History.record(this, info)
        applyPalette()
        val p = Mood.palette(shownMood, Prefs.dark(this))
        air.visibility = if (info.onAir) View.VISIBLE else View.GONE
        if (!info.onAir) {
            title.visibility = View.VISIBLE
            title.text = "The station is not broadcasting right now"
            listOf(artist, album, genre, key).forEach { it.visibility = View.GONE }
            coverBox.visibility = View.GONE
            zoneBox.visibility = View.GONE
            return
        }
        val jingle = info.genre == "Jingle"
        if (info.title == null) title.visibility = View.GONE else { title.visibility = View.VISIBLE; title.text = info.title.ifBlank { "—" } }
        show(artist, if (jingle) null else info.artist)
        show(album, if (jingle) null else info.album)
        show(genre, info.genre)
        genre.setCompoundDrawablesWithIntrinsicBounds(Mood.ICONS[shownMood] ?: R.drawable.ic_music, 0, 0, 0)
        tint(genre, p.sub)
        if (info.keyText != null) {
            key.text = info.keyText
            key.background = shape(ColorUtils.HSLToColor(floatArrayOf(info.keyHue, 0.55f, 0.38f)), 999f)
            key.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_key, 0, 0, 0)
            tint(key, Color.WHITE)
            key.visibility = View.VISIBLE
        } else key.visibility = View.GONE

        if (info.zone != null) {
            zoneName.text = info.zone
            zoneIcon.setImageResource(Mood.iconFor(info.zoneIcon))
            show(zoneDesc, info.zoneDesc)
            zoneBox.visibility = View.VISIBLE
        } else zoneBox.visibility = View.GONE

        if (info.hasCover) {
            if (lastCoverId != info.id) {
                lastCoverId = info.id
                io.execute {
                    val b: Bitmap? = try { Api.fetchCover(inv, info.id) } catch (e: Exception) { null }
                    main.post {
                        if (lastCoverId == info.id) {
                            if (b != null) { cover.setImageBitmap(b); coverBox.visibility = View.VISIBLE }
                            else coverBox.visibility = View.GONE
                        }
                    }
                }
            }
        } else {
            lastCoverId = null
            coverBox.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        Hub.listeners.add(hubListener)
        applyPalette(true)
        volume.progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        main.removeCallbacks(pollTask)
        main.post(pollTask)
    }

    override fun onStop() {
        started = false
        Hub.listeners.remove(hubListener)
        main.removeCallbacks(pollTask)
        eq.setActive(false)
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
