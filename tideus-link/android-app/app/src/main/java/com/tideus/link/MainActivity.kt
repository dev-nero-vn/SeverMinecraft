package com.tideus.link

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private data class Conversation(
        val id: String,
        val name: String,
        val platform: String,
        val preview: String,
        val time: String,
        val initials: String,
        val avatarColor: Int,
        val unread: Int = 0
    )

    private data class Message(val text: String, val time: String, val mine: Boolean)

    private val bg = Color.rgb(8, 12, 24)
    private val panel = Color.rgb(16, 24, 40)
    private val panelAlt = Color.rgb(21, 32, 52)
    private val line = Color.rgb(37, 52, 76)
    private val primaryText = Color.rgb(239, 244, 255)
    private val secondaryText = Color.rgb(144, 160, 185)
    private val purple = Color.rgb(137, 89, 255)
    private val cyan = Color.rgb(71, 208, 224)
    private val green = Color.rgb(66, 211, 157)
    private val amber = Color.rgb(237, 183, 94)

    private var selectedFilter = "Tất cả"
    private val conversations = listOf(
        Conversation("user001", "User_001", "Messenger", "!vd https://youtube.com/…", "14:45", "U", Color.rgb(116, 79, 231), 2),
        Conversation("vip", "VIP Member", "Messenger", "Cảm ơn bạn nhiều nhé!", "14:42", "♛", Color.rgb(119, 83, 29), 1),
        Conversation("tiktok", "TikTok User", "TikTok", "Cho mình hỏi cách sử dụng…", "14:40", "♪", Color.rgb(31, 44, 64)),
        Conversation("zalo", "Zalo User", "Zalo", "Bot này thật tuyệt!", "14:38", "Z", Color.rgb(37, 111, 211))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        renderInbox()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 16, stroke: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }
    }

    private fun text(value: String, size: Float, color: Int = primaryText, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            includeFontPadding = true
        }
    }

    private fun horizontal(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    private fun vertical(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
    }

    private fun gap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun avatar(initials: String, color: Int, size: Int = 44): TextView {
        return text(initials, if (initials.length > 1) 13f else 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(color, 50)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
    }

    private fun pageHeader(title: String, subtitle: String): LinearLayout {
        val header = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(14))
            setBackgroundColor(bg)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_tideus_logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(panel, 12, line)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)))
        val titles = vertical().apply {
            setPadding(dp(11), 0, 0, 0)
            addView(text("TIDEUS LINK", 13f, primaryText, true))
            addView(text(title + " · " + subtitle, 10f, secondaryText))
        }
        header.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(text("PREVIEW", 9f, amber, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(Color.rgb(52, 39, 22), 30)
        })
        return header
    }

    private fun renderInbox() {
        selectedFilter = selectedFilter.ifBlank { "Tất cả" }
        val root = vertical().apply { setBackgroundColor(bg) }
        root.addView(pageHeader("Hộp thư", "Tin nhắn đa nền tảng"))

        val body = ScrollView(this).apply { isFillViewport = true }
        val content = vertical().apply { setPadding(dp(18), dp(8), dp(18), dp(16)) }

        val titleRow = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        val heading = vertical()
        heading.addView(text("Trò chuyện", 24f, primaryText, true))
        heading.addView(text("Các cuộc hội thoại của bạn", 11f, secondaryText))
        titleRow.addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(text("⌕", 25f, secondaryText).apply {
            gravity = Gravity.CENTER
            background = rounded(panelAlt, 12, line)
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        content.addView(titleRow)
        gap(content, 14)

        val notice = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(43, 34, 19), 12, Color.rgb(92, 68, 32))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        notice.addView(text("ⓘ", 17f, amber, true))
        notice.addView(text(
            " Bản xem trước giao diện. Hội thoại mẫu chưa phải tin nhắn đồng bộ từ tài khoản thật.",
            10f, Color.rgb(229, 198, 139)
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(notice)
        gap(content, 14)

        val search = EditText(this).apply {
            hint = "Tìm kiếm cuộc trò chuyện…"
            setHintTextColor(secondaryText)
            setTextColor(primaryText)
            textSize = 12f
            setSingleLine(true)
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(panelAlt, 13, line)
        }
        content.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        gap(content, 13)

        val filters = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val filterRow = horizontal()
        listOf("Tất cả", "Messenger", "TikTok", "Zalo").forEach { filter ->
            val chip = text(filter, 10f, if (selectedFilter == filter) Color.WHITE else secondaryText, selectedFilter == filter).apply {
                gravity = Gravity.CENTER
                setPadding(dp(15), dp(9), dp(15), dp(9))
                background = rounded(
                    if (selectedFilter == filter) Color.rgb(80, 65, 180) else panelAlt,
                    28,
                    if (selectedFilter == filter) purple else line
                )
                setOnClickListener {
                    selectedFilter = filter
                    renderInbox()
                }
            }
            filterRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                rightMargin = dp(8)
            })
        }
        filters.addView(filterRow)
        content.addView(filters)
        gap(content, 10)

        val list = vertical().apply {
            background = rounded(panel, 17, line)
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        fun populate(query: String) {
            list.removeAllViews()
            val visible = conversations.filter {
                (selectedFilter == "Tất cả" || it.platform == selectedFilter) &&
                    (it.name.contains(query, true) || it.preview.contains(query, true))
            }
            if (visible.isEmpty()) {
                list.addView(text("Không tìm thấy cuộc trò chuyện.", 12f, secondaryText).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(10), dp(24), dp(10), dp(24))
                })
            } else {
                visible.forEachIndexed { index, conversation ->
                    list.addView(conversationRow(conversation))
                    if (index != visible.lastIndex) {
                        list.addView(View(this).apply { setBackgroundColor(line) },
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                                leftMargin = dp(58)
                                rightMargin = dp(4)
                            })
                    }
                }
            }
        }
        populate("")
        content.addView(list)
        gap(content, 14)
        content.addView(text(
            "Kết nối Plugin để thay dữ liệu mẫu bằng tin nhắn thật.",
            10f, secondaryText
        ).apply { gravity = Gravity.CENTER })
        body.addView(content)

        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation("chat"))
        setContentView(root)

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                populate(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun conversationRow(item: Conversation): View {
        val row = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(12))
            background = rounded(Color.TRANSPARENT, 12)
            setOnClickListener { renderConversation(item) }
        }
        row.addView(avatar(item.initials, item.avatarColor))
        val middle = vertical().apply {
            setPadding(dp(11), 0, dp(6), 0)
            addView(text(item.name, 12f, primaryText, true))
            addView(text(item.preview, 10f, secondaryText).apply {
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(4), 0, 0)
            })
            addView(text(item.platform, 9f, if (item.platform == "Zalo") cyan else secondaryText).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        row.addView(middle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val right = vertical().apply {
            gravity = Gravity.END
            addView(text(item.time, 9f, secondaryText))
            if (item.unread > 0) {
                addView(text(item.unread.toString(), 9f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    background = rounded(purple, 50)
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                })
            }
        }
        row.addView(right)
        return row
    }

    private fun renderConversation(item: Conversation) {
        val root = vertical().apply { setBackgroundColor(bg) }
        val header = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(14), dp(12))
            setBackgroundColor(panel)
        }
        header.addView(text("‹", 28f, primaryText).apply {
            gravity = Gravity.CENTER
            setOnClickListener { renderInbox() }
        }, LinearLayout.LayoutParams(dp(34), dp(42)))
        header.addView(avatar(item.initials, item.avatarColor, 40))
        val nameBox = vertical().apply {
            setPadding(dp(10), 0, 0, 0)
            addView(text(item.name, 13f, primaryText, true))
            addView(text(item.platform + " · Hội thoại mẫu", 10f, secondaryText))
        }
        header.addView(nameBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(text("⋮", 22f, secondaryText).apply { gravity = Gravity.CENTER })
        root.addView(header)

        root.addView(text("BẢN XEM TRƯỚC · Chưa kết nối tài khoản " + item.platform, 9f, amber, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(Color.rgb(42, 33, 19), 0)
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val messages = vertical().apply { setPadding(dp(14), dp(18), dp(14), dp(18)) }
        listOf(
            Message("Xin chào! Mình có thể hỗ trợ gì cho bạn?", "14:40", false),
            Message("Mình muốn hỏi cách sử dụng bot.", "14:41", true),
            Message("Bạn gửi yêu cầu vào đây nhé. Đây là bản xem trước giao diện chat.", "14:42", false)
        ).forEach { messages.addView(messageBubble(it)) }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val composer = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(panel)
        }
        val input = EditText(this).apply {
            hint = "Nhập tin nhắn…"
            setHintTextColor(secondaryText)
            setTextColor(primaryText)
            textSize = 12f
            minLines = 1
            maxLines = 4
            setPadding(dp(13), dp(9), dp(13), dp(9))
            background = rounded(panelAlt, 14, line)
        }
        composer.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        composer.addView(text("➤", 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(purple, 13)
            setOnClickListener {
                if (input.text.toString().isNotBlank()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Chưa gửi: hãy kết nối Plugin " + item.platform + " để gửi tin nhắn thật.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(composer)
        root.addView(bottomNavigation("chat"))
        setContentView(root)
    }

    private fun messageBubble(message: Message): View {
        val lineBox = horizontal().apply {
            gravity = if (message.mine) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }
        val bubble = text(message.text + "\n" + message.time, 11f, primaryText).apply {
            maxWidth = dp(285)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = rounded(if (message.mine) Color.rgb(76, 58, 163) else panelAlt, 16)
        }
        lineBox.addView(bubble, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        return lineBox
    }

    private fun renderPlugins() {
        val root = vertical().apply { setBackgroundColor(bg) }
        root.addView(pageHeader("Plugin Center", "Kết nối tài khoản nền tảng"))
        val scroll = ScrollView(this)
        val content = vertical().apply { setPadding(dp(18), dp(12), dp(18), dp(18)) }
        content.addView(text("Liên kết nền tảng", 22f, primaryText, true))
        content.addView(text(
            "Chọn dịch vụ bạn muốn kết nối để quản lý tin nhắn trong cùng một hộp thư.",
            11f, secondaryText
        ))
        gap(content, 15)
        addPluginCard(content, "Z", "Zalo Official Account", "Nhận và phản hồi tin nhắn từ Zalo OA.", cyan)
        addPluginCard(content, "M", "Messenger", "Quản lý hội thoại từ Facebook Page.", Color.rgb(83, 128, 255))
        addPluginCard(content, "♪", "TikTok Business", "Hỗ trợ tin nhắn tài khoản doanh nghiệp khi được cấp quyền.", primaryText)
        gap(content, 12)
        content.addView(text(
            "Kết nối thật cần OAuth, webhook và dịch vụ máy chủ đã được triển khai. Phiên bản hiện tại chưa có máy chủ hoặc quyền xác thực nền tảng nên chưa thể đồng bộ tin nhắn thật.",
            10f, amber
        ).apply {
            background = rounded(Color.rgb(42, 33, 19), 12)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation("plugins"))
        setContentView(root)
    }

    private fun addPluginCard(parent: LinearLayout, mark: String, title: String, description: String, accent: Int) {
        val card = vertical().apply {
            background = rounded(panel, 17, line)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        val row = horizontal().apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(text(mark, 17f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(accent, 13)
        }, LinearLayout.LayoutParams(dp(43), dp(43)))
        val labels = vertical().apply {
            setPadding(dp(11), 0, 0, 0)
            addView(text(title, 12f, primaryText, true))
            addView(text("Chưa kết nối", 9f, amber))
        }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("›", 24f, secondaryText))
        card.addView(row)
        card.addView(text(description, 10f, secondaryText).apply {
            setPadding(0, dp(10), 0, dp(12))
        })
        card.addView(text("Kết nối", 10f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(69, 61, 156), 10)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            setOnClickListener { showPluginRequirement(title) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)))
        parent.addView(card)
        gap(parent, 11)
    }

    private fun showPluginRequirement(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Kết nối " + name)
            .setMessage(
                "Mục Plugin đã có trong app. Để đăng nhập và nhận/gửi tin nhắn thật, cần dịch vụ TIDEUS LINK xử lý OAuth và webhook hợp lệ cho " + name + ". Backend này chưa được triển khai nên nút chưa thể bắt đầu đăng nhập. Không gửi API key hoặc mật khẩu vào chat."
            )
            .setPositiveButton("Đã hiểu", null)
            .show()
    }

    private fun renderActivity() {
        val root = vertical().apply { setBackgroundColor(bg) }
        root.addView(pageHeader("Live Activity", "Theo dõi hoạt động hệ thống"))
        val content = vertical().apply { setPadding(dp(18), dp(16), dp(18), dp(16)) }
        content.addView(text("Nhật ký hoạt động", 22f, primaryText, true))
        gap(content, 12)
        content.addView(text("Ứng dụng đã mở và sẵn sàng.", 11f, green))
        content.addView(text("Chưa có sự kiện từ nền tảng vì chưa kết nối tài khoản.", 11f, secondaryText))
        content.addView(text("Các hoạt động thật sẽ xuất hiện tại đây sau khi Plugin được liên kết.", 10f, secondaryText).apply {
            background = rounded(panel, 13, line)
            setPadding(dp(13), dp(13), dp(13), dp(13))
        })
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation("activity"))
        setContentView(root)
    }

    private fun renderSettings() {
        val root = vertical().apply { setBackgroundColor(bg) }
        root.addView(pageHeader("Cài đặt", "TIDEUS LINK trên thiết bị"))
        val content = vertical().apply { setPadding(dp(18), dp(16), dp(18), dp(16)) }
        content.addView(text("Thông tin ứng dụng", 21f, primaryText, true))
        gap(content, 13)
        settingRow(content, "Phiên bản", "0.2.0")
        settingRow(content, "Chế độ", "Ứng dụng Android")
        settingRow(content, "Máy chủ", "Chưa triển khai")
        settingRow(content, "AI", "Chưa cấu hình")
        gap(content, 15)
        content.addView(text(
            "Không cần nhập địa chỉ Linux khi mở app. Khi backend TIDEUS LINK được triển khai, địa chỉ dịch vụ sẽ được gắn sẵn vào bản APK cập nhật.",
            10f, secondaryText
        ).apply {
            background = rounded(panel, 13, line)
            setPadding(dp(13), dp(13), dp(13), dp(13))
        })
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottomNavigation("settings"))
        setContentView(root)
    }

    private fun settingRow(parent: LinearLayout, label: String, value: String) {
        val row = horizontal().apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        row.addView(text(label, 11f, secondaryText), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text(value, 11f, primaryText, true))
        parent.addView(row)
        parent.addView(View(this).apply { setBackgroundColor(line) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun bottomNavigation(active: String): LinearLayout {
        val nav = horizontal().apply {
            gravity = Gravity.CENTER
            setBackgroundColor(panel)
            setPadding(dp(5), dp(8), dp(5), dp(8))
        }
        val tabs = listOf(
            Triple("chat", "▤", "Chat"),
            Triple("plugins", "⌘", "Plugin"),
            Triple("activity", "◷", "Live"),
            Triple("settings", "⚙", "Cài đặt")
        )
        tabs.forEach { (key, icon, label) ->
            val selected = key == active
            val item = vertical().apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(3), 0, dp(3))
                addView(text(icon, 18f, if (selected) cyan else secondaryText, true).apply {
                    gravity = Gravity.CENTER
                })
                addView(text(label, 9f, if (selected) primaryText else secondaryText, selected).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                })
                setOnClickListener {
                    when (key) {
                        "chat" -> renderInbox()
                        "plugins" -> renderPlugins()
                        "activity" -> renderActivity()
                        "settings" -> renderSettings()
                    }
                }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return nav
    }
}
